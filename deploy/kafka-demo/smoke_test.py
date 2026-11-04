#!/usr/bin/env python3
"""Test the rendered broker locally; creates only uniquely named disposable Docker resources."""
import json
import re
from pathlib import Path
import subprocess
import time
import uuid
import yaml

HERE = Path(__file__).resolve().parent


def command(*args, input=None, timeout=45):
    result = subprocess.run(args, input=input, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"Command failed: {' '.join(args[:4])}\n{result.stderr[-3000:]}")
    return result.stdout


def main():
    resources = list(yaml.safe_load_all(command('kubectl', 'kustomize', str(HERE / 'base'))))
    stateful = next(r for r in resources if r['kind'] == 'StatefulSet')
    container = stateful['spec']['template']['spec']['containers'][0]
    configmaps = {r['metadata']['name']: r['data'] for r in resources if r['kind'] == 'ConfigMap'}
    env = {}
    for source in container['envFrom']:
        env.update(configmaps[source['configMapRef']['name']])
    env.update({e['name']: e['value'] for e in container.get('env', []) if 'value' in e})
    jobs = list(yaml.safe_load_all(command('kubectl', 'kustomize', str(HERE / 'topics'))))
    job = next(r for r in jobs if r['kind'] == 'Job')
    job_container = job['spec']['template']['spec']['containers'][0]
    job_env = {e['name']: e['value'] for e in job_container.get('env', []) if 'value' in e}
    script = job_container['args'][0]
    bootstrap_alias = re.search(r'^broker=([^:\s]+):9092$', script, re.MULTILINE).group(1)
    image = container['image']
    advertised = env['KAFKA_ADVERTISED_LISTENERS'].split('://', 1)[1]
    alias = advertised.rsplit(':', 1)[0]
    mount = container['volumeMounts'][0]['mountPath']
    suffix = uuid.uuid4().hex[:10]
    name = 'sporty-kafka-demo-test-' + suffix
    network, volume = name + '-network', name + '-data'
    created_network = created_volume = created_container = False

    def kafka(tool, *args, input=None, timeout=45):
        return command('docker', 'exec', '-i', '-e', 'KAFKA_HEAP_OPTS=-Xms32m -Xmx64m', name,
                       '/opt/kafka/bin/' + tool, *args, input=input, timeout=timeout)

    def ready():
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                kafka('kafka-topics.sh', '--bootstrap-server', 'localhost:9092', '--list', timeout=15)
                return
            except (RuntimeError, subprocess.TimeoutExpired):
                time.sleep(2)
        raise RuntimeError('Broker did not become ready within 120 seconds')

    try:
        # Image download can take much longer than a local container command on a cold cache.
        command('docker', 'pull', image, timeout=600)
        command('docker', 'network', 'create', '--internal', network)
        created_network = True
        command('docker', 'volume', 'create', volume)
        created_volume = True
        # Simulate the fsGroup volume ownership applied by Kubernetes, only on our new test volume.
        command('docker', 'run', '--rm', '--network', 'none', '--user', '0',
                '--mount', f'type=volume,source={volume},target={mount}', '--entrypoint', '/bin/sh',
                image, '-c', f'chown 1000:1000 {mount}')
        args = ['docker', 'run', '-d', '--name', name, '--network', network, '--network-alias', alias, '--network-alias', bootstrap_alias,
                '--memory', '1536m', '--cpus', '1', '--user', '1000:1000',
                '--security-opt', 'no-new-privileges', '--cap-drop', 'ALL', '--mount', f'type=volume,source={volume},target={mount}']
        for key, value in env.items():
            args.extend(['-e', f'{key}={value}'])
        args.append(image)
        command(*args)
        created_container = True
        ready()
        command('docker', 'exec', name, *container['readinessProbe']['exec']['command'])
        print('PASS broker startup and exact readiness probe using rendered KRaft configuration', flush=True)
        # Execute the topic Job's real shell script twice, including its existing-topic validation.
        for _ in range(2):
            job_args = ['docker', 'exec', '-e', 'KAFKA_HEAP_OPTS=-Xms32m -Xmx64m']
            for key, value in job_env.items():
                job_args.extend(['-e', f'{key}={value}'])
            command(*job_args, name, *job_container['command'], script, timeout=90)
        print('PASS actual topic Job script, including repeated initialization', flush=True)
        message = json.dumps({'eventId': str(uuid.uuid4()), 'eventType': 'SmokeTest', 'payload': {'test': True}})
        kafka('kafka-console-producer.sh', '--bootstrap-server', 'localhost:9092', '--topic', 'user.withdrawn',
              '--producer-property', 'acks=all', input=message + '\n')
        received = kafka('kafka-console-consumer.sh', '--bootstrap-server', 'localhost:9092', '--topic',
                         'user.withdrawn', '--from-beginning', '--max-messages', '1', '--timeout-ms', '15000')
        assert message in received, received
        print('PASS event produce and consume', flush=True)
        command('docker', 'stop', '--time', '30', name)
        command('docker', 'rm', name)
        created_container = False
        command(*args)
        created_container = True
        ready()
        received = kafka('kafka-console-consumer.sh', '--bootstrap-server', 'localhost:9092', '--topic',
                         'user.withdrawn', '--from-beginning', '--max-messages', '1', '--timeout-ms', '15000')
        assert message in received, received
        print('PASS event retained after replacing the broker container on the same volume', flush=True)
    except Exception:
        if created_container:
            print(command('docker', 'logs', '--tail', '60', name), flush=True)
        raise
    finally:
        if created_container:
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
        if created_volume:
            subprocess.run(['docker', 'volume', 'rm', volume], capture_output=True)
        if created_network:
            subprocess.run(['docker', 'network', 'rm', network], capture_output=True)


if __name__ == '__main__':
    main()
