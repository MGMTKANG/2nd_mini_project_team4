# user-service

Sporty 회원 및 인증 마이크로서비스. 모놀리식 `backend/sporty`의 회원(`user`), 프로필(`profile`), 선호 종목(`sport_preference`), 인증(`auth`) 도메인을 분리한 독립 Spring Boot 애플리케이션이다.

- 소유 DB: `user_db` (`user`, `profile`, `sport_preference`)
- 포트: **8081**
- 주요 인프라: MariaDB (3306), Redis (6379, JWT Refresh Token 저장소)

---

## 1. 실행 방법

```bash
cd services/user-service
./gradlew.bat bootRun
```

* 서버 포트: **8081**
* Swagger API 문서: `http://localhost:8081/swagger-ui/index.html`

---

## 2. 환경 변수 (`.env`)

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `USER_DB_URL` | `jdbc:mariadb://localhost:3306/user_db` | 유저 전용 DB URL |
| `USER_DB_USERNAME` / `USER_DB_PASSWORD` | `root` / `admin` | DB 계정 및 비밀번호 |
| `JWT_SECRET_KEY` | (필수) | JWT 서명 비밀키 (전체 서비스 공통) |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Refresh Token 관리용 Redis 주소 |
| `EUREKA_ENABLED` | `false` | Eureka 클라이언트 활성화 여부 |
| `CONFIG_ENABLED` | `false` | Spring Cloud Config 활성화 여부 |

---

## 3. 타 마이크로서비스 연동 (Internal Feign API)

`match-service` 등 타 서비스에서 회원 상태 확인 및 참가자 프로필 조회를 위해 아래 내부 API를 제공합니다:

* **회원 상태 단건 조회**: `GET /internal/users/{userId}`
  * 응답: `{"userId": 1, "status": "ACTIVE", "active": true}`
* **참가자 프로필 목록 일괄 조회**: `GET /internal/users/profiles?userIds=1,2,3`
  * 응답: `[{"userId": 1, "profileId": 1, "nickname": "러너", "imageUrl": null}]`

---

## 4. 데이터베이스 테이블 구성 (3개 테이블)

1. `user`: 회원 계정 및 탈퇴 상태 (`ACTIVE`, `WITHDRAWN`), 이메일, 비밀번호 해시, 성별
2. `profile`: 닉네임, 활동 자치구(`District`), 프로필 이미지
3. `sport_preference`: 회원별 운동 선호 종목 목록

---

## 5. 회원 탈퇴 Kafka 이벤트 (COM-07)

`withdraw()`는 내부 이벤트를 등록하고, DB 커밋 후 `AFTER_COMMIT` 리스너가 `UserWithdrawn`을 발행한다. 롤백되거나 트랜잭션이 없으면 발행하지 않는다. 기본값 `user.events.kafka-enabled=${USER_KAFKA_ENABLED:false}`에서는 발행과 Kafka Admin의 토픽 자동 생성을 끄며, Kafka에 연결하지 않고 DEBUG 로그 한 줄만 남긴다.

Kafka와 토픽을 준비한 뒤 `USER_KAFKA_ENABLED=true`, `KAFKA_BOOTSTRAP_SERVERS=<브로커>:9092`로 활성화한다. 토픽 설정 `user.events.user-withdrawn-topic`은 `USER_WITHDRAWN_TOPIC`으로 변경하며 기본값은 `user.withdrawn`이다. 구독하는 match-service에도 같은 토픽과 `MATCH_KAFKA_ENABLED=true`가 필요하다. 메시지 키는 `aggregateId`(회원 ID 문자열), 값은 다음 JSON 문자열이다.

```json
{"eventId":"f3cc9eb9-634a-4dd8-a3a6-53e4a7d58e62","eventType":"UserWithdrawn","aggregateType":"User","aggregateId":"1","occurredAt":"2026-11-04T12:34:56.789Z","payload":{"userId":1}}
```

전송 결과는 비동기 콜백으로 기록하며 실패를 탈퇴 응답에 전파하지 않는다. `KafkaTemplate.send()`의 메타데이터·버퍼 대기는 `USER_KAFKA_MAX_BLOCK_MS`(기본 3000ms)로 제한한다. 성공 로그에는 topic/partition/offset/eventId, 실패 로그에는 eventId/예외 클래스명만 남긴다. Kafka 기본 실패 리스너도 대체하여 원문과 예외 상세가 별도로 기록되지 않게 한다.

**한계:** Outbox(DAT-03)는 이번 범위에 포함하지 않는다. DB 커밋 후 프로세스가 종료되거나 발행이 실패하면 이벤트가 유실될 수 있으며, 영속 재시도나 재발행 기능은 없다.

검증 명령: `cd services/user-service && bash ./gradlew clean test`. 테스트 구성은 JSON 계약·실패 격리 단위 테스트, H2 커밋/롤백·비활성 통합 테스트, 실제 토픽의 키·값을 확인하는 EmbeddedKafka 통합 테스트다.

2026-11-04 검증(로컬 macOS, JDK 17): `bash ./gradlew clean test` **17개 통과·실패 0·건너뜀 0** (단위 6, H2 트랜잭션 5, 비활성/로그 2, EmbeddedKafka 1, 기존 컨트롤러 3). 실제 브로커·MariaDB·match-service 간 연동과 배포 환경 검증은 아직 하지 않았다.
