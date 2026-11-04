"""Validate the rendered demo manifests locally; never contacts a Kubernetes API.

    python -m pip install -r deploy/kafka-demo/requirements-test.txt
    python deploy/kafka-demo/test_manifests.py

Requires kubectl on PATH for its offline `kustomize` renderer.
"""
from pathlib import Path
import posixpath
import re
import subprocess
import unittest

import yaml

ROOT = Path(__file__).resolve().parent


def render(directory):
    result = subprocess.run(
        ["kubectl", "kustomize", str(directory)], check=True,
        capture_output=True, text=True,
    )
    return [resource for resource in yaml.safe_load_all(result.stdout) if resource]


def selector_matches(selector, labels):
    if any(labels.get(key) != value for key, value in selector.get("matchLabels", {}).items()):
        return False
    for expression in selector.get("matchExpressions", []):
        key, operator = expression["key"], expression["operator"]
        values = expression.get("values", [])
        if operator == "In" and labels.get(key) not in values:
            return False
        if operator == "NotIn" and labels.get(key) in values:
            return False
        if operator == "Exists" and key not in labels:
            return False
        if operator == "DoesNotExist" and key in labels:
            return False
        if operator not in {"In", "NotIn", "Exists", "DoesNotExist"}:
            raise AssertionError(f"Unsupported selector operator: {operator}")
    return True


def comma_mapping(value, separator):
    return dict(item.strip().split(separator, 1) for item in value.split(","))


class KafkaDemoManifestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = render(ROOT / "base")
        cls.topics = render(ROOT / "topics")
        brokers = [item for item in cls.base if item["kind"] == "StatefulSet"]
        if len(brokers) != 1:
            raise AssertionError("The demo must render exactly one StatefulSet")
        cls.broker = brokers[0]
        cls.pod = cls.broker["spec"]["template"]
        cls.container = cls.pod["spec"]["containers"][0]
        cls.namespace = cls.broker["metadata"].get("namespace", "default")
        cls.configmaps = {
            item["metadata"]["name"]: item for item in cls.base if item["kind"] == "ConfigMap"
        }
        cls.env = {}
        for source in cls.container.get("envFrom", []):
            if "configMapRef" in source:
                cls.env.update(cls.configmaps[source["configMapRef"]["name"]].get("data", {}))
        cls.env.update({item["name"]: item["value"] for item in cls.container.get("env", []) if "value" in item})
        cls.services = [item for item in cls.base if item["kind"] == "Service"]
        cls.jobs = [item for item in cls.topics if item["kind"] == "Job"]
        cls.port_names = {
            item["name"]: item["containerPort"] for item in cls.container.get("ports", []) if "name" in item
        }

    def test_single_broker_keeps_logs_on_persistent_volume(self):
        self.assertEqual(self.broker["spec"].get("replicas", 1), 1)
        claims = self.broker["spec"]["volumeClaimTemplates"]
        self.assertEqual(len(claims), 1)
        claim = claims[0]
        self.assertEqual(claim["spec"]["resources"]["requests"]["storage"], "10Gi")
        self.assertIn("ReadWriteOnce", claim["spec"]["accessModes"])
        mounts = {item["name"]: item for item in self.container["volumeMounts"]}
        mount = mounts[claim["metadata"]["name"]]
        self.assertFalse(mount.get("readOnly", False))
        mounted_directory = posixpath.normpath(mount["mountPath"])
        for log_directory in self.env["KAFKA_LOG_DIRS"].split(","):
            log_directory = posixpath.normpath(log_directory)
            self.assertTrue(log_directory == mounted_directory or log_directory.startswith(mounted_directory + "/"),
                            "Kafka logs must be inside the PVC, not the container writable layer")
        retention = self.broker["spec"].get("persistentVolumeClaimRetentionPolicy", {})
        self.assertEqual(retention.get("whenDeleted", "Retain"), "Retain")
        self.assertEqual(retention.get("whenScaled", "Retain"), "Retain")

    def test_bootstrap_services_have_no_external_exposure(self):
        self.assertGreaterEqual(len(self.services), 2)
        self.assertFalse(self.pod["spec"].get("hostNetwork", False))
        self.assertFalse(any(port.get("hostPort") for port in self.container.get("ports", [])))
        for service in self.services:
            self.assertEqual(service["spec"].get("type", "ClusterIP"), "ClusterIP")
            self.assertFalse(service["spec"].get("externalIPs"))
            self.assertFalse(any(port.get("nodePort") for port in service["spec"]["ports"]))
            self.assertTrue(selector_matches({"matchLabels": service["spec"]["selector"]}, self.pod["metadata"]["labels"]))
        headless = next(item for item in self.services if item["metadata"]["name"] == self.broker["spec"]["serviceName"])
        self.assertEqual(headless["spec"].get("clusterIP"), "None")
        self.assertTrue(headless["spec"].get("publishNotReadyAddresses", False),
                        "The broker needs its own stable DNS name before readiness succeeds")
        bootstrap = next(item for item in self.services if item["spec"].get("clusterIP") != "None")
        self.assertEqual([port["port"] for port in bootstrap["spec"]["ports"]], [9092])

    def test_kraft_addresses_match_statefulset_and_headless_dns(self):
        self.assertEqual(set(self.env["KAFKA_PROCESS_ROLES"].split(",")), {"broker", "controller"})
        listeners = comma_mapping(self.env["KAFKA_LISTENERS"], "://")
        advertised = comma_mapping(self.env["KAFKA_ADVERTISED_LISTENERS"], "://")
        protocols = comma_mapping(self.env["KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"], ":")
        controller = self.env["KAFKA_CONTROLLER_LISTENER_NAMES"]
        client = self.env["KAFKA_INTER_BROKER_LISTENER_NAME"]
        self.assertEqual(set(listeners), {client, controller})
        self.assertEqual(set(advertised), {client}, "Never advertise the KRaft controller as a client broker")
        self.assertEqual(protocols[client], "PLAINTEXT")
        self.assertEqual(protocols[controller], "PLAINTEXT")
        client_port = int(listeners[client].rsplit(":", 1)[1])
        controller_port = int(listeners[controller].rsplit(":", 1)[1])
        self.assertEqual(client_port, 9092)
        self.assertEqual(controller_port, 9093)
        host = f"{self.broker['metadata']['name']}-0.{self.broker['spec']['serviceName']}.{self.namespace}.svc.cluster.local"
        self.assertEqual(advertised[client], f"{host}:{client_port}")
        self.assertEqual(self.env["KAFKA_CONTROLLER_QUORUM_VOTERS"], f"{self.env['KAFKA_NODE_ID']}@{host}:{controller_port}")

    def test_single_node_replication_and_explicit_topic_creation(self):
        for key in ("KAFKA_DEFAULT_REPLICATION_FACTOR", "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR",
                    "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR",
                    "KAFKA_MIN_INSYNC_REPLICAS", "KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR",
                    "KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR"):
            self.assertEqual(str(self.env[key]), "1", key)
        self.assertEqual(str(self.env["KAFKA_AUTO_CREATE_TOPICS_ENABLE"]).lower(), "false")

    def test_cluster_identity_is_stable_across_pod_replacement(self):
        owners = [item for item in self.configmaps.values() if "CLUSTER_ID" in item.get("data", {})]
        self.assertEqual(len(owners), 1)
        self.assertTrue(owners[0].get("immutable", False))
        self.assertRegex(self.env["CLUSTER_ID"], r"^[A-Za-z0-9_-]{22}$")

    def ingress_allowed(self, labels, port):
        selected = [item for item in self.base if item["kind"] == "NetworkPolicy"
                    and selector_matches(item["spec"].get("podSelector", {}), self.pod["metadata"]["labels"])]
        self.assertTrue(selected, "The demo broker must be selected by an ingress policy")
        for policy in selected:
            self.assertEqual(policy["metadata"].get("namespace", "default"), self.namespace)
            for rule in policy["spec"].get("ingress", []):
                ports = rule.get("ports", [])
                if ports and not any(item.get("protocol", "TCP") == "TCP"
                                     and ("port" not in item or self.port_names.get(item["port"], item["port"]) == port)
                                     for item in ports):
                    continue
                for peer in rule.get("from", [{}]):
                    # Only same-namespace workload identities are permitted in this demo.
                    self.assertNotIn("ipBlock", peer)
                    self.assertNotIn("namespaceSelector", peer)
                    self.assertIn("podSelector", peer)
                    if selector_matches(peer["podSelector"], labels):
                        return True
        return False

    def test_network_policy_allows_only_clients_and_broker_controller(self):
        self.assertEqual(len(self.jobs), 1)
        topic_labels = self.jobs[0]["spec"]["template"]["metadata"]["labels"]
        for labels in ({"app": "user-service"}, {"app": "match-service"}, topic_labels):
            self.assertTrue(self.ingress_allowed(labels, 9092), labels)
            self.assertFalse(self.ingress_allowed(labels, 9093), labels)
        self.assertTrue(self.ingress_allowed(self.pod["metadata"]["labels"], 9093))
        self.assertFalse(self.ingress_allowed({"app": "unrelated-workload"}, 9092))

    def test_topic_config_checks_reject_mixed_policy_and_matching_synonyms(self):
        script = self.jobs[0]['spec']['template']['spec']['containers'][0]['args'][0]
        checks = [line.strip() for line in script.splitlines() if '"$configs" | grep' in line]
        self.assertEqual(len(checks), 3)
        settings = [('cleanup.policy', 'delete', 'delete,compact'),
                    ('retention.ms', '604800000', '1'),
                    ('retention.bytes', '1073741824', '1')]
        for check, (key, expected, wrong) in zip(checks, settings):
            for current, accepted in [(expected, True), (wrong, False)]:
                # The desired value in a synonym must not hide an incorrect effective value.
                output = (f'  {key}={current} sensitive=false synonyms={{DYNAMIC_TOPIC_CONFIG:'
                          f'{key}={current},DEFAULT_CONFIG:{key}={expected}}}\n')
                with self.subTest(key=key, current=current):
                    result = subprocess.run(['/bin/sh', '-c', 'configs=$(cat)\n' + check],
                                            input=output, text=True, capture_output=True)
                    self.assertEqual(result.returncode, 0 if accepted else 1)

    def test_topic_job_creates_withdrawal_topic_idempotently(self):
        self.assertEqual(len(self.jobs), 1)
        job = self.jobs[0]
        self.assertEqual(job["metadata"].get("namespace", "default"), self.namespace)
        pod = job["spec"]["template"]["spec"]
        self.assertIn(pod["restartPolicy"], {"Never", "OnFailure"})
        self.assertEqual(len(pod["containers"]), 1)
        container = pod["containers"][0]
        script = "\n".join(container.get("command", []) + container.get("args", []))
        self.assertIn("kafka-topics", script)
        self.assertIn("--create", script)
        self.assertIn("--if-not-exists", script)
        self.assertRegex(script, r"--topic\s+['\"]?user\.withdrawn(?:['\"]|\s|$)")
        self.assertRegex(script, r"--replication-factor\s+['\"]?1(?:['\"]|\s|$)")
        bootstrap = next(item for item in self.services if item["spec"].get("clusterIP") != "None")
        name = re.escape(bootstrap["metadata"]["name"])
        namespace = re.escape(self.namespace)
        self.assertRegex(script, rf"{name}(?:\.{namespace}(?:\.svc(?:\.cluster\.local)?)?)?:9092")


if __name__ == "__main__":
    unittest.main(verbosity=2)
