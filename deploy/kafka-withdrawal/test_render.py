import argparse
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

MODULE_PATH = Path(__file__).with_name("render.py")
spec = importlib.util.spec_from_file_location("withdrawal_kafka_render", MODULE_PATH)
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)
REPO = MODULE_PATH.resolve().parents[2]


class KafkaOverlayTest(unittest.TestCase):
    def test_missing_placeholder_and_local_endpoints_are_rejected(self):
        for value in ("", "<broker>:9092", "broker:9092", "localhost:9092", "127.0.0.1:9092", "0.0.0.0:9092",
                      "https://kafka.svc:9092", "user:password@kafka.svc:9092", "kafka.example.com:9092",
                      "kafka.svc:0", "kafka.svc:65536", "kafka.svc:9092,", "kafka..svc:9092"):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                renderer.bootstrap_servers(value)

    def test_cluster_addresses_and_multiple_brokers_are_preserved(self):
        for value in ("kafka.sporty.svc.cluster.local:9092", "10.1.2.3:9092,10.1.2.4:9093"):
            self.assertEqual(value, renderer.bootstrap_servers(value))

    def test_missing_required_input_does_not_create_output(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "overlay"
            result = subprocess.run(["python3", str(MODULE_PATH), "--output", str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(output.exists())

    def test_both_services_share_required_broker_and_topic_and_sources_are_unchanged(self):
        before = {service: (REPO / "k8s" / service / "deployment.yaml").read_bytes() for service in renderer.SERVICES}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "overlay"
            renderer.render(REPO, output, "kafka.sporty.svc.cluster.local:9092", "user.withdrawn")
            config = json.loads((output / "kustomization.yaml").read_text())
            self.assertEqual(["KAFKA_BOOTSTRAP_SERVERS=kafka.sporty.svc.cluster.local:9092", "USER_WITHDRAWN_TOPIC=user.withdrawn"], config["configMapGenerator"][0]["literals"])
            self.assertNotIn("disableNameSuffixHash", config.get("generatorOptions", {}))
            for service, enabled in renderer.SERVICES.items():
                patch = json.loads((output / f"{service}-kafka-patch.json").read_text())
                env = {entry["name"]: entry for entry in patch["spec"]["template"]["spec"]["containers"][0]["env"]}
                self.assertEqual("true", env[enabled]["value"])
                for key in ("KAFKA_BOOTSTRAP_SERVERS", "USER_WITHDRAWN_TOPIC"):
                    self.assertEqual({"name": renderer.CONFIG_NAME, "key": key, "optional": False}, env[key]["valueFrom"]["configMapKeyRef"])
                self.assertEqual(before[service], (output / f"{service}-deployment.yaml").read_bytes())
                self.assertEqual(before[service], (REPO / "k8s" / service / "deployment.yaml").read_bytes())

    def test_existing_output_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            marker = output / "keep.txt"
            marker.write_text("existing")
            with self.assertRaises(ValueError):
                renderer.render(REPO, output, "kafka.sporty.svc.cluster.local:9092", "user.withdrawn")
            self.assertEqual("existing", marker.read_text())
            self.assertEqual([marker], list(output.iterdir()))

    @unittest.skipUnless(shutil.which("kubectl"), "kubectl kustomize is not installed")
    def test_kustomize_merges_into_real_deployments_without_changing_other_urls(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "overlay"
            renderer.render(REPO, output, "kafka.sporty.svc.cluster.local:9092", "user.withdrawn")
            result = subprocess.run(["kubectl", "kustomize", str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            rendered = result.stdout
            self.assertEqual(1, rendered.count("kind: ConfigMap"))
            self.assertEqual(2, rendered.count("kind: Deployment"))
            config_name = re.search(r"name: (sporty-withdrawal-kafka-[a-z0-9]+)", rendered).group(1)
            self.assertEqual(5, rendered.count(f"name: {config_name}"))  # ConfigMap + 2 refs per deployment
            for key in renderer.SERVICES.values():
                self.assertRegex(rendered, rf'name: {key}\n\s+value: "true"')
            self.assertIn("value: http://point-service:8085", rendered)
            self.assertIn("key: DB_PASSWORD", rendered)
            self.assertNotIn("<broker>", rendered)
            self.assertNotIn("localhost:9092", rendered)


if __name__ == "__main__":
    unittest.main()
