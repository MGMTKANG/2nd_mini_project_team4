# 개발·시연용 단일 Kafka 브로커

회원 탈퇴 이벤트의 유저 발행·매치 수신에 사용할 Kafka를 `sporty` 네임스페이스 내부에
준비하는 별도 배포 자료다. 이 디렉터리를 추가하는 PR은 클라우드에 적용하지 않으며,
기존 `k8s/`의 애플리케이션 Deployment나 Argo CD 설정도 바꾸지 않는다.

## 구성

- Apache Kafka **4.1.2 JVM 이미지**, Linux amd64/arm64를 포함하는 OCI 인덱스 digest로 고정.
- KRaft broker/controller 겸용 StatefulSet `kafka`, replica 1. ZooKeeper는 사용하지 않는다.
- bootstrap 주소: `kafka.sporty.svc.cluster.local:9092`.
- Kafka가 클라이언트에 반환하는 실제 주소:
  `kafka-0.kafka-headless.sporty.svc.cluster.local:9092`.
- controller voter: `1@kafka-0.kafka-headless.sporty.svc.cluster.local:9093`.
- `kafka`는 ClusterIP이며 클라이언트용 9092만 연다. headless Service는 StatefulSet의
  고정 DNS와 controller 9093을 제공한다. NodePort, LoadBalancer, Ingress, hostPort는 없다.
- NetworkPolicy는 같은 네임스페이스의 `app=user-service`, `app=match-service`,
  `app=kafka-topic-init` Pod에만 9092를 허용하고, 9093은 `app=kafka`에만 허용한다.
- 데이터는 RWO PVC **10Gi**의 `/mnt/kafka/data/logs`에 저장한다.
  파일시스템 루트의 `lost+found`를 Kafka 데이터 디렉터리로 잘못 읽지 않도록 하위 경로를 쓴다.
- UID/GID/fsGroup 1000. broker heap 512MiB, Pod 요청 CPU 250m·메모리 1Gi,
  상한 CPU 1·메모리 1536Mi. readiness 도구의 별도 JVM heap은 64MiB로 제한한다.

단일 브로커·RF 1은 **개발·시연용**이다. Pod/노드 장애 동안 서비스가 중단되며 저장소 손실 시
복제본으로 복구할 수 없다. PVC를 유지한 정상 재시작은 데이터 재사용을 목적으로 하지만
실제 클라우드의 StorageClass·디스크·스케줄링까지 검증됐다는 뜻은 아니다.

## 적용 전 확인할 것

1. `sporty` 네임스페이스, 충분한 노드 여유 자원, 이미지 다운로드 경로가 준비되어 있어야 한다.
2. 기본 StorageClass로 PVC가 프로비저닝되는지 확인한다. `storageClassName`을 생략했으므로
   기본 StorageClass 또는 조건에 맞는 사전 생성 PV가 없으면 PVC와 Pod는 **Pending**이다.
   필요한 클래스가 있다면 검토한 환경별 patch로 지정한다. 임시 `emptyDir`로 대체하지 않는다.
3. CNI가 NetworkPolicy를 실제로 집행해야 한다. 정책 YAML만으로 집행을 확인했다고 보지 않는다.
   클러스터 DNS 도메인은 여기서 `cluster.local`로 가정하며 실제 도메인이 다르면 관련 DNS를 함께 바꾼다.
4. 현재 구성은 내부망 **PLAINTEXT**다. 외부 노출이나 운영용 TLS/SASL 인증을 구성한 것으로 보지 않는다.
5. 동일 이름의 기존 브로커·PVC·ConfigMap이 있으면 먼저 소유권과 클러스터 ID를 확인한다.
   `kafka-cluster-identity`는 immutable ConfigMap이며, `CLUSTER_ID`는 해당 PVC 수명 동안
   유지한다. 비밀값은 아니지만 기존 저장소를 새 ID로 다시 포맷해서는 안 된다.

## 배포 순서와 별도 승인 범위

`base/`와 `topics/`는 별도 Kustomization이다. 토픽 Job은 broker 준비보다 먼저 실행되지 않도록
구분했으며, Argo hook이나 자동 배포 설정이 아니다. 실제 적용은 기존 GitOps 관리 방식과
충돌하지 않는 경로로 별도 승인 후 수행한다. 이번 변경에서는 다음 클라우드 단계들을 실행하지 않는다.

1. `base/` 적용 후 `data-kafka-0` PVC Bound 및 `kafka-0` Ready를 확인한다.
2. `topics/`의 `kafka-user-withdrawn-init` Job을 실행하고 Complete와 설정 검증 로그를 확인한다.
3. 애플리케이션이 같은 bootstrap 주소와 `user.withdrawn` 토픽을 사용하도록 설정한다.
   유저는 `USER_KAFKA_ENABLED=true`, 매치는 `MATCH_KAFKA_ENABLED=true`가 필요하다.
   유저·매치 활성화는 이 인프라 디렉터리가 자동 변경하지 않는다. 별도 설정 변경을 같은
   GitOps 관리 원본에 반영하며, 수동 변경 후 Argo CD에 의해 되돌아가지 않도록 한다.
4. 테스트 회원 탈퇴 → 이벤트 발행 → 매치 정리·환불 → 중복 이벤트 무해성을 확인한다.
   펀딩 환불의 내구성·멱등성은 별도 수정 PR #88과 DB 마이그레이션에도 의존한다.

토픽 Job은 `--if-not-exists`로 `user.withdrawn`을 생성하고 다음 값을 **검증**한다.
이미 존재하는 토픽의 설정이 다르면 자동 변경하지 않고 실패하므로, 운영자가 차이를 검토해야 한다.

| 항목 | 값 |
| --- | --- |
| partitions | 1 |
| replication factor | 1 |
| cleanup policy | delete |
| retention.ms | 604800000 (7일) |
| retention.bytes | 1073741824 (파티션당 1GiB) |

기간·용량 제한 중 먼저 충족된 조건에 따라 오래된 로그가 지워질 수 있다. 세그먼트 단위 정리라
용량 값은 엄격한 디스크 상한이 아니며, 메타데이터·내부 토픽도 PVC를 사용한다.
Job은 완료 후 보존하며 자동 삭제·재실행하지 않는다. 재검증은 승인된 별도 Job 이름으로 하거나
읽기 전용 describe 도구를 사용한다. 토픽 생성 전에 앱을 활성화해도 브로커는 자동 생성하지 않는다.

## 로컬 검증

아래 렌더링은 클러스터에 접속하거나 리소스를 적용하지 않는다.

```sh
kubectl kustomize deploy/kafka-demo/base
kubectl kustomize deploy/kafka-demo/topics
```

저장소 루트에서 구조 검사와 로컬 Docker smoke test를 실행할 수 있다.
Python 환경에는 PyYAML이 필요하다. smoke test는 로컬 전용 브로커·볼륨으로
발행/수신 및 같은 볼륨을 이용한 컨테이너 교체 후 데이터 보존을 확인하며 클라우드 kubeconfig를 사용하지 않는다.

```sh
python3 -m venv /tmp/sporty-kafka-tests
/tmp/sporty-kafka-tests/bin/pip install -r deploy/kafka-demo/requirements-test.txt
/tmp/sporty-kafka-tests/bin/python -m unittest discover -s deploy/kafka-demo -p 'test_*.py' -v
/tmp/sporty-kafka-tests/bin/python deploy/kafka-demo/smoke_test.py
```

로컬 Docker 검증 통과와 클라우드 검증은 구분한다. 실제 PVC 프로비저닝, NetworkPolicy 집행,
Pod DNS, 노드 장애 복구, 유저·매치 서비스 간 종단 연동은 클라우드 적용 후 확인해야 한다.

2026-11-05 로컬 검증 결과: 렌더 계약 검사 7개 통과. 고정한 공식 이미지의 실제 기동과
readiness 명령, 토픽 Job 두 번 실행, 이벤트 발행·수신, 기존 볼륨을 연결한 새 컨테이너에서
이벤트 재조회가 모두 통과했다. 검증용 컨테이너·네트워크·볼륨은 종료 후 정리한다.
로컬 검증은 Docker의 Linux arm64에서 실행했으며 클라우드 Kubernetes 검증을 대체하지 않는다.

GitHub Actions의 `Kafka demo configuration`도 위 검사를 수행한다. 임시 Docker 리소스만
만들며 클러스터 인증 정보와 배포 단계는 없다.

## 기존 탈퇴 데이터와 발행 보장

Kafka가 꺼져 있던 동안 발생한 탈퇴는 이벤트 자체가 발행되지 않았으므로,
새 브로커를 구축하고 `earliest`로 소비해도 과거 매치가 자동 정리되지 않는다.
기존 탈퇴 회원과 잔존 매치는 별도 대조·복구가 필요하다. 또한 유저 서비스의 현재
DB 커밋 후 비동기 발행은 실패를 로그로만 남기므로, 이 브로커 추가가 Outbox 재전송까지
보장하지는 않는다. 토픽 보존 기간이 지난 이벤트도 자동 복구 대상이 아니다.

## 근거

- [Apache Kafka 4.1 Docker 문서](https://kafka.apache.org/41/getting-started/docker/): 공식 JVM 이미지 4.1.2.
- [공식 단일 노드 Compose 예제](https://github.com/apache/kafka/blob/4.1.2/docker/examples/docker-compose-files/single-node/plaintext/docker-compose.yml): combined role, static voter, 단일 브로커 내부 토픽 복제 설정.
- [KRaft 운영 문서](https://kafka.apache.org/41/operations/kraft/): 개발용 combined mode 및 static quorum, 저장소 클러스터 ID 관리.
