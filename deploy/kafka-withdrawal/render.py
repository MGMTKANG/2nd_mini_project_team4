#!/usr/bin/env python3
"""Prepare a local Kafka activation overlay; never contact or change a cluster."""
import argparse
import ipaddress
import json
from pathlib import Path
import re
import shutil

SERVICES = {"user-service": "USER_KAFKA_ENABLED", "match-service": "MATCH_KAFKA_ENABLED"}
CONFIG_NAME = "sporty-withdrawal-kafka"


def bootstrap_servers(value):
    endpoints = value.split(",")
    for endpoint in endpoints:
        # Reject URLs, credentials, placeholders and local-only addresses before rendering.
        match = re.fullmatch(r"([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?):([0-9]{1,5})", endpoint)
        if not match:
            raise argparse.ArgumentTypeError("브로커는 실제 host:port 목록이어야 합니다(쉼표로 구분).")
        host, port = match.groups()
        if not 1 <= int(port) <= 65535:
            raise argparse.ArgumentTypeError("브로커 포트는 1~65535 범위여야 합니다.")
        lower = host.lower()
        if lower in {"localhost", "example", "broker", "replace-me", "changeme"} or lower.endswith((".localhost", ".example", ".invalid", ".test", ".example.com")) or lower == "example.com":
            raise argparse.ArgumentTypeError("예시 주소나 localhost를 클라우드 브로커로 사용할 수 없습니다.")
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            if any(not part or len(part) > 63 or part.startswith("-") or part.endswith("-") for part in host.split(".")):
                raise argparse.ArgumentTypeError("올바른 브로커 호스트 이름이 필요합니다.")
        else:
            if address.is_loopback or address.is_unspecified or address.is_multicast:
                raise argparse.ArgumentTypeError("Pod에서 접근 가능한 브로커 주소가 필요합니다.")
    return ",".join(endpoints)


def topic_name(value):
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,249}", value) or value in {".", ".."}:
        raise argparse.ArgumentTypeError("올바른 Kafka 토픽 이름이 필요합니다.")
    return value


def render(repo, output, bootstrap, topic):
    # Validate even when called without argparse (e.g. automated build tooling).
    bootstrap = bootstrap_servers(bootstrap)
    topic = topic_name(topic)
    if output.exists():
        raise ValueError("출력 폴더가 이미 존재합니다. 기존 결과를 덮어쓰지 않도록 새 경로를 지정하세요.")
    sources = {service: repo / "k8s" / service / "deployment.yaml" for service in SERVICES}
    for source in sources.values():
        if not source.is_file():
            raise ValueError(f"기준 Deployment 파일을 찾을 수 없습니다: {source}")
    output.mkdir(parents=True)
    resources, patches = [], []
    for service, enabled_key in SERVICES.items():
        resource_name = f"{service}-deployment.yaml"
        shutil.copyfile(sources[service], output / resource_name)
        resources.append(resource_name)
        env = [{"name": enabled_key, "value": "true"}]
        for key in ("KAFKA_BOOTSTRAP_SERVERS", "USER_WITHDRAWN_TOPIC"):
            env.append({"name": key, "valueFrom": {"configMapKeyRef": {"name": CONFIG_NAME, "key": key, "optional": False}}})
        patch = {"apiVersion": "apps/v1", "kind": "Deployment", "metadata": {"name": service, "namespace": "sporty"},
                 "spec": {"template": {"spec": {"containers": [{"name": service, "env": env}]}}}}
        patch_name = f"{service}-kafka-patch.json"
        (output / patch_name).write_text(json.dumps(patch, indent=2) + "\n", encoding="utf-8")
        patches.append({"path": patch_name})
    overlay = {"apiVersion": "kustomize.config.k8s.io/v1beta1", "kind": "Kustomization", "namespace": "sporty",
               "resources": resources, "patches": patches,
               "configMapGenerator": [{"name": CONFIG_NAME, "literals": [f"KAFKA_BOOTSTRAP_SERVERS={bootstrap}", f"USER_WITHDRAWN_TOPIC={topic}"]}]}
    # Keep the generated name hash: changing broker/topic also changes the Pod template refs.
    (output / "kustomization.yaml").write_text(json.dumps(overlay, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bootstrap-servers", required=True, type=bootstrap_servers)
    parser.add_argument("--topic", type=topic_name, default="user.withdrawn")
    parser.add_argument("--output", required=True, type=Path, help="새로 생성할 로컬 overlay 폴더")
    args = parser.parse_args()
    try:
        render(Path(__file__).resolve().parents[2], args.output, args.bootstrap_servers, args.topic)
    except (ValueError, OSError) as error:
        parser.error(str(error))
    print(f"로컬 overlay 준비 완료: {args.output}\n검토: kubectl kustomize {args.output}\n클러스터 변경은 실행하지 않았습니다.")


if __name__ == "__main__":
    main()
