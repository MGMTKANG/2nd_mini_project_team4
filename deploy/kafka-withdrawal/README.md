# 회원 탈퇴 Kafka 연동 활성화

2026-11-05 확인: 클라우드에는 아직 Kafka 브로커가 없다. 기존 `k8s/user-service`와
`k8s/match-service` Deployment는 Kafka 없이 기동하는 상태를 유지한다.
이 디렉터리는 브로커가 준비된 뒤 **두 서비스의 발행·수신을 함께 활성화하는 로컬
배포 설정 생성 도구**다. 브로커를 설치하거나 클러스터에 적용하지 않는다.
이 코드 추가만으로 클라우드 탈퇴 정리가 활성화되는 것은 아니다.

## 필요한 운영 준비

- 두 Pod에서 bootstrap 주소와 Kafka의 `advertised.listeners` 주소에 모두 접근할 수 있는 브로커.
- 현재 애플리케이션 기본 구성은 **PLAINTEXT**다. TLS/SASL 브로커라면 클라이언트 인증 설정과
  Secret 참조를 먼저 추가해야 한다. 인증 정보나 키를 이 도구의 주소 인수에 넣지 않는다.
- 토픽 `user.withdrawn`(또는 합의한 토픽) 생성과 적절한 보존 기간 설정. 현재 발행 코드에
  `NewTopic` 빈이 없고 수신 서비스는 토픽 자동 생성을 요청하지 않으므로 토픽을 별도로 준비한다.
- 매치·포인트 DB 마이그레이션과 포인트 서비스 연결 확인. 펀딩 매치 탈퇴 정리는 환불 API에도 의존한다.

## 로컬 설정 생성과 검토

저장소 루트에서 실행한다. `KAFKA_BOOTSTRAP_SERVERS`에는 인프라 담당자가 확인한 실제
`host:port` 목록을 넣는다. 아래 명령은 값을 자동 추측하지 않으며 빈 값·예시 주소·localhost를 거절한다.

```sh
python3 deploy/kafka-withdrawal/render.py \
  --bootstrap-servers "$KAFKA_BOOTSTRAP_SERVERS" \
  --output /tmp/sporty-withdrawal-kafka
kubectl kustomize /tmp/sporty-withdrawal-kafka > /tmp/sporty-withdrawal-kafka-rendered.yaml
```

출력 폴더는 새 경로여야 한다. 재생성할 때에는 새 경로를 지정한다. 생성물은 현재 작업본의
두 Deployment를 복사하므로 **배포 직전 최신 이미지 태그를 포함하는 작업본에서 다시 생성한다.**
저장소의 원본 Deployment, kubeconfig와 클러스터는 수정하지 않는다.

생성되는 설정:

- `USER_KAFKA_ENABLED=true`, `MATCH_KAFKA_ENABLED=true`.
- 두 서비스가 같은 ConfigMap의 `KAFKA_BOOTSTRAP_SERVERS`, `USER_WITHDRAWN_TOPIC`을 필수로 참조.
- ConfigMap 이름에 내용 해시를 유지하여 브로커·토픽 변경 시 Pod 템플릿 참조도 함께 변경.
- 기존 DB Secret, 서비스 URL, 이미지, probe 등은 원본 그대로 보존.

인프라가 준비되고 생성물을 검토한 뒤, 승인된 GitOps/배포 경로에 ConfigMap과 두 Deployment를
함께 반영한다. 기존 Argo CD가 원본 `k8s/` 경로를 동기화한다면 생성물만 수동 적용해도 원본으로
되돌아갈 수 있으므로 **관리하는 원본에 설정을 반영하는 단계가 필요하다.** 이 도구는 Argo CD
Application이나 기존 동기화 대상을 변경하지 않는다. 실행 중인 서버에 적용하는 명령은 포함하지 않는다.

## 검증과 남은 사항

로컬 검증:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/kafka-withdrawal -p 'test_*.py' -v
```

실제 `kubectl kustomize` 로컬 렌더링, 두 서비스의 활성화·공통 필수값 연결, 기존 URL/Secret 보존,
잘못된 입력 거절과 덮어쓰기 방지를 검증한다. 클러스터 접속 또는 리소스 적용은 수행하지 않는다.

운영 완료 조건은 별도다: 브로커/토픽 준비 → 배포 설정 반영 → 유저 탈퇴 이벤트 발행 →
매치 정리·환불 → 동일 이벤트 재수신 시 중복 처리 방지까지 실제 환경에서 확인한다.
Kafka가 꺼져 있던 동안 발생한 탈퇴는 이벤트 자체가 발행되지 않았으므로, `earliest`로 켜도
자동 복구되지 않는다. 기존 탈퇴 회원의 잔존 매치 정리는 별도 대조·복구 대상으로 남는다.
또한 현재 유저 발행기는 DB 커밋 후 비동기 발행 실패를 로그만 남기므로 지속 가능한 Outbox
재전송은 이번 배포 연결 변경의 범위 밖이며, Kafka 활성화만으로 전송 유실까지 해결되지는 않는다.
