# match-service

운동 매칭 서비스. 모놀리식 `backend/sporty`의 `exerciseMatching` 코드를 떼어 낸 독립 Spring Boot 앱이다.
1단계는 원본 매치 테스트 101개로 확인한 범위에서 도메인 로직을 유지하며 분리했다. 매치 상태는 기존과 같은 `RECRUITING/CLOSED`이다.

- 담당: 김승진
- 소유 DB: `match_db` (`match`, `match_participant`, `processed_event`, `match_user_state`)
- 포트: 8082

## 실행

```bash
cd services/match-service
JWT_SECRET_KEY=... MATCH_DB_URL=jdbc:mariadb://localhost:3306/match_db \
MATCH_DB_USERNAME=... MATCH_DB_PASSWORD=... ./gradlew bootRun
```

- Docker(매치 단독 로컬용): 위의 `cd services/match-service` 실행 후 `cp .env.example .env`로 가짜 비밀값을 복사하고 필요시 수정한다(`.env`는 Git·이미지 빌드에서 제외).
- 기동: `docker compose -f compose.match-local.yml up -d --build` (MariaDB 헬스체크 통과 후 매치 시작, 매치 컨테이너 메모리 512MiB 제한).
- 확인: `docker compose -f compose.match-local.yml ps`, `curl -i http://localhost:8082/actuator/health` → 비로그인 `200`·`{"status":"UP"}` 예상. `curl -i http://localhost:8082/api/matches` → 초기 빈 DB는 비로그인 `200`·`[]` 예상. HTTP 확인은 앱 기동 완료 후 실행한다.
- 유저·체육시설 URL은 기본값(`http://localhost:8081`, `http://localhost:8083`)이며 연결되지 않는다. `curl -i 'http://localhost:8082/api/matches?isFree=true'`처럼 시설 검색 조건을 확인하는 호출은 `503 / EXTERNAL_SERVICE_UNAVAILABLE`이 정상이다. 기본 목록·상세는 기존 매치가 있어도 `200`이며, 장애가 난 시설·프로필 필드만 `null`로 반환한다. 내 매치도 시설 장애에는 부분 응답하지만 로그인 회원 확인 장애에는 `503`이다.
- 정리: `docker compose -f compose.match-local.yml down -v` (로컬 DB 볼륨도 삭제).

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `MATCH_DB_URL` | `jdbc:mariadb://localhost:3306/match_db` | 매치 전용 DB |
| `MATCH_DB_USERNAME` / `MATCH_DB_PASSWORD` | `match` / 빈 값 | |
| `JWT_SECRET_KEY` | 없음(필수) | 유저 서비스와 같은 값 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 직접 호출 주소. 명시적 빈 값이면 `user-service` 이름으로 탐색 |
| `FACILITY_SERVICE_URL` | `http://localhost:8083` | 직접 호출 주소. 명시적 빈 값이면 `facility-service` 이름으로 탐색 |
| `EUREKA_ENABLED` | `false` | Eureka 등록·탐색 활성화 여부 |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka 서버 주소 |
| `CONFIG_ENABLED` | `false` | Config 클라이언트 활성화 여부 |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Config 서버 주소(`optional:configserver:`로 가져옴) |
| `CONFIG_CONNECT_TIMEOUT_MS` | `2000` | Config 서버 연결 타임아웃(밀리초) |
| `CONFIG_READ_TIMEOUT_MS` | `5000` | Config 서버 읽기 타임아웃(밀리초) |
| `MATCH_KAFKA_ENABLED` | `false` | 회원 탈퇴 Kafka 리스너 자동 시작 여부 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka 브로커 주소 |
| `USER_WITHDRAWN_TOPIC` | `user.withdrawn` | 회원 탈퇴 이벤트 수신 토픽 |
| `MATCH_JPA_DDL_AUTO` | `update` | Hibernate 스키마 처리. 운영 권장값은 `validate` |
| `SPRING_PROFILES_ACTIVE` | 미지정 시 `local` | `local` / `dev` / `prod`. Compose 기본값도 `local` |

기본값은 Eureka·Config 없이 단독 실행한다. 기존 `.env` import는 유지한다. `SPRING_PROFILES_ACTIVE=dev` 또는 `prod`에서는 `show-sql=false`이며, 프로필만 바꿔도 Eureka·Config가 켜지지 않는다. Config 서버가 준비되면 `CONFIG_ENABLED=true CONFIG_SERVER_URL=http://<설정서버>:8888`로 활성화하고 재시작한다. 설정 조회의 서비스 이름은 `match-service`이다.

Eureka 전환 방법:

1. 서버와 상대 서비스 등록이 준비되면 `EUREKA_ENABLED=true EUREKA_URL=http://<유레카서버>:8761/eureka/`로 설정한다.
2. `USER_SERVICE_URL='' FACILITY_SERVICE_URL=''`로 명시적으로 비우면 `@FeignClient`의 `name`(`user-service`, `facility-service`)을 Spring Cloud LoadBalancer가 찾아 호출한다. 변수를 생략하면 기존 직접 주소를 사용한다.
3. 앱을 재시작하고 `match-service` 등록과 상대 서비스 호출을 확인한다. Compose도 `.env`의 같은 변수를 전달하며, 주소는 컨테이너에서 접근 가능한 값으로 설정한다.

Feign/Eureka 호출은 로드밸런서 자동 재시도 끔(`spring.cloud.loadbalancer.retry.enabled=false`): 잠금 안 원격 호출 시간의 예측 가능성을 유지한다.

운영 DB는 아래 [운영 스키마 적용 안내](#운영-스키마-적용과-erd-범위)에 따라 빈 DB용 전체 SQL 또는 기존 DB용 추가 SQL을 먼저 적용한 뒤 `MATCH_JPA_DDL_AUTO=validate`로 실행한다. `validate`는 스키마를 생성·변경하지 않으므로 DB별 사전 검증이 필요하다. 기본 `update`는 현재 로컬 동작을 유지한다.

주의. `ddl-auto=update`는 기존 BIGINT `service_id` 칸의 타입을 바꾸지 않으므로, 이미 `match` 테이블이 있는 DB는 새로 만들거나 칸 타입을 직접 바꿔야 한다.

Actuator 웹 노출은 `health,info`뿐이며 두 경로는 비로그인 접근을 허용한다. Compose 앱 healthcheck는 미설정이다. K8s에서는 `management.endpoint.health.probes.enabled=true`(환경 변수 `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`)로 활성화하고, liveness는 `/actuator/health/liveness`, readiness는 `/actuator/health/readiness`를 사용한다. 두 probe 경로도 비로그인 접근을 허용한다.

경기 시작·모집 재개 판정은 `Asia/Seoul`을 명시해 JVM 기본 시간대와 무관하게 비교한다. 프론트/API의 오프셋 없는 `startAt`·`endAt`은 서울 지역 시각이다. Docker도 로그와 기본 시각을 맞추도록 `TZ=Asia/Seoul`로 실행한다. 테스트 fixture는 서울 기준이며 별도 시간대 회귀 테스트가 UTC·미국 서부 JVM에서도 같은 동작을 확인한다.

앱은 graceful shutdown과 종료 단계 제한 `20s`를 사용하며, Compose는 `stop_grace_period: 30s`로 종료를 기다린다.

테스트: `./gradlew test` (기존 H2 테스트와 아래 MariaDB 통합 테스트. 다른 서비스 없이 실행되며 Docker가 없으면 MariaDB 테스트만 자동으로 건너뛴다. 새 의존성·컨테이너 이미지를 최초로 받으려면 네트워크가 가능한 빌드 환경 필요)

### MariaDB 통합 테스트

MariaDB 통합 테스트는 Testcontainers로 `compose.match-local.yml`과 같은 `mariadb:11.4.8`을 실행한다. Docker 데몬에 접근할 수 있어야 하며 로컬 Compose DB나 실서비스 DB는 사용하지 않는다. `MatchMariaDBIntegrationTest`와 `MatchUserGuardMariaDBIntegrationTest`의 `MariaDBIntegrationTestSupport`는 MariaDB dialect·`ddl-auto=create-drop`을 적용하고, 정적 컨테이너를 같은 테스트 JVM의 클래스 사이에서 공유한다. `MatchSchemaMariaDBIntegrationTest`는 이 지원 클래스를 상속하지 않는 별도 `@SpringBootTest`이며, 전용 컨테이너의 빈 스키마에 실제 전체 운영 SQL을 먼저 실행한 뒤 `ddl-auto=validate`·`spring.sql.init.mode=never`로 기동한다. 클래스별 `@Container` 종료 대신 Testcontainers 리소스 정리기가 JVM 종료 시 컨테이너를 제거한다. 모두 `@Testcontainers(disabledWithoutDocker = true)`로 Docker 없는 CI에서는 MariaDB 테스트를 건너뛴다. Docker가 있는데 이미지 다운로드·DB 기동·단언이 실패하면 테스트 실패로 남는다.

```bash
cd services/match-service
./gradlew test --tests '*Match*MariaDBIntegrationTest'
# 전체 회귀 테스트
./gradlew cleanTest test
```

Gradle 의존성과 MariaDB·Testcontainers 보조 이미지가 모두 준비된 환경에서는 `--offline`으로 Gradle의 의존성 다운로드를 막을 수 있다. 이 옵션은 Docker 이미지 다운로드를 차단하지 않는다. 건너뜀 여부와 실행 결과는 `build/reports/tests/test/index.html` 및 `build/test-results/test/TEST-*.xml`에서 확인한다.

- 스냅샷 회귀: OWNER+A+B+U, 정원 4, CLOSED에서 처리기의 실제 ID SELECT 직후 저장소 스파이·래치로 멈춘다. 별도 트랜잭션으로 A 탈퇴를 커밋하고 OWNER가 정원 3으로 수정·커밋한 뒤 U 처리를 재개한다. 최종 OWNER+B 2명·정원 3·RECRUITING을 단언한다. 호출자의 기본 REPEATABLE READ 트랜잭션에 오래된 Match도 적재해 두고, 처리기 연결의 실제 READ_COMMITTED와 호출자 엔티티의 분리도 확인한다.
- 잠금: `findByIdForUpdate` 보유자가 참가자 추가를 커밋하기 전까지 수정의 SELECT 자체가 반환되지 않고, 이후 커밋한 인원으로 CLOSED가 되는지 확인한다.
- 멱등 처리: 같은 ID의 순차 중복은 처리 시각·후속 매치를 보존한다. 동시 두 호출이 모두 미처리를 조회하도록 동기화한 뒤 실제 PK 충돌(오류 1062 / SQLState 23000), 실패 측 롤백 후 정상 반환, 이력 1행·정리 1회를 확인한다. 실제 리스너→처리기를 직접 호출하며 Kafka 전송·오프셋은 기존 EmbeddedKafka 테스트 범위다.
- 스키마: `MatchSchemaMariaDBIntegrationTest`에서 실제 전체 SQL 적용 후 컨텍스트 기동, 컬럼 타입·길이·NULL·기본값·자동 증가·PK 순서·UNIQUE·FK·CHECK 메타데이터, 제약 위반 거절, 다른 소비자의 같은 이벤트 ID 허용 및 재적용 후 전체 데이터 보존을 검증한다. `MatchUserGuardMariaDBIntegrationTest`는 기존 매치 테이블을 둔 채 두 추가 SQL로 보조 테이블을 복원하고 Hibernate validate와 탈퇴 상태·이력 보존을 검증한다. 최신 ERD의 미구현 Saga 스키마 전체를 검증하는 것은 아니다.
- 오류 재현: `defaultRepeatableReadReproducesClosedStatusAfterWithdrawal`은 같은 시나리오를 격리 지정 없는 새 트랜잭션에서 `removeMatchesForWithdrawal` 직접 호출로 실행한다. 기본 REPEATABLE READ 연결에서 최종 2명/정원 3/CLOSED 오류가 실제로 생기는 것을 단언하며 집계 결과는 스텁하지 않는다. 격리 수준을 지정해야 하는 이유를 보여 주므로 유지한다.

## 회원 탈퇴 이벤트 수신

기본값 `MATCH_KAFKA_ENABLED=false`에서는 리스너 컨테이너가 시작되지 않는다. Kafka Admin의 토픽 자동 생성과 컨슈머의 토픽 자동 생성 요청도 끄므로 Kafka 없이 앱을 기동할 수 있다. Kafka와 유저 서비스가 준비되면 `MATCH_KAFKA_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=<브로커>:9092`로 설정하고 재시작한다. 컨슈머 그룹은 `match-service`, 처리 주체는 `match-service.user-withdrawn`이다. 발행 코드와 토픽 생성 코드는 포함하지 않는다.

Compose도 `.env`의 `MATCH_KAFKA_ENABLED`(기본 `false`), `KAFKA_BOOTSTRAP_SERVERS`, `USER_WITHDRAWN_TOPIC`을 매치 컨테이너에 전달한다. 브로커 주소는 컨테이너에서 접근 가능한 값으로 설정한다. 새 그룹처럼 저장된 오프셋이 없으면 운영 설정 `spring.kafka.consumer.auto-offset-reset=earliest`로 토픽에 남아 있는 가장 오래된 이벤트부터 처리한다. 이미 확정된 오프셋은 그대로 사용하며, 브로커 보존기간이 지나 삭제된 이벤트는 이 설정으로 복구할 수 없다.

유저 서비스가 맞출 회원 탈퇴 이벤트 계약:

- 인프라 담당자와 수신 토픽 `user.withdrawn`을 미리 준비한다. 이름 변경 시 `USER_WITHDRAWN_TOPIC`도 맞춘다.
- 메시지 key는 `userId`의 문자열(`aggregateId`)이다. 같은 회원의 이벤트는 같은 파티션으로 보낸다.
- value는 아래 JSON envelope이다. `eventId`는 UUID이며 재발행할 때 유지한다. `payload.userId`는 양의 정수(BIGINT 범위)로 보낸다.
- 회원 탈퇴 변경과 `outbox_event`를 같은 유저 DB 트랜잭션에 저장하고 발행한다. `occurredAt`은 최초 outbox 생성 시각을 유지한다. 발행 측 구현은 유저 서비스 범위다.

```json
{
  "eventId": "8b1f0c2e-1d3a-4c5b-9e7f-0a1b2c3d4e5f",
  "eventType": "UserWithdrawn",
  "aggregateType": "user",
  "aggregateId": "1",
  "occurredAt": "2026-11-02T14:00:00",
  "payload": { "userId": 1 }
}
```

`UserWithdrawnListener`가 JSON을 검사한 뒤 별도 빈인 `UserWithdrawnHandler.handle`을 호출한다. 다른 `eventType`은 INFO 로그 후 건너뛴다. 잘못된 JSON, 없거나 잘못된 `eventId`/`payload.userId`는 원문을 남기지 않고 ERROR 로그(`topic`/`partition`/`offset`, 확인 가능한 UUID `eventId`) 후 건너뛴다. 이 메시지들은 재시도하지 않고 다음 이벤트를 처리한다.

처리 순서는 새 `REQUIRES_NEW / READ_COMMITTED` 트랜잭션 시작 → `(consumer_name, event_id)` 존재 확인 → 없으면 명시적 INSERT → 사용자 행 잠금 및 탈퇴 상태 기록 → `removeMatchesForWithdrawal(userId)` → DB 커밋이다. 이미 처리된 ID는 매치 정리를 실행하지 않는다. 동시 INSERT의 PK 충돌은 실패한 트랜잭션을 먼저 롤백한 뒤 처리 이력을 다시 조회해, 다른 처리가 커밋한 이력이 있으면 중복으로 정상 반환한다. 그 외 삽입 오류는 재시도 대상으로 전달한다. 처리 이력·사용자 탈퇴 상태·매치 변경은 같은 트랜잭션이므로 정리 실패 시 함께 롤백한다. `processed_event`는 ERD의 `consumer_name VARCHAR(100)`, `event_id CHAR(36)`, `processed_at DATETIME`, 복합 PK를 매핑한다.

리스너에는 Kafka 트랜잭션을 사용하지 않는다. 컨슈머의 자동 커밋을 끄고 `ack-mode=record`를 사용해, 처리 서비스의 DB 트랜잭션이 끝나고 리스너가 정상 반환한 뒤 컨테이너가 오프셋을 확정한다. DB 커밋 후 오프셋 확정 전 중단돼 재전달되면 처리 이력으로 건너뛴다. DB·연결 등 일시 오류는 `DefaultErrorHandler`와 `ExponentialBackOff`로 무제한 재시도한다. 초기 1초, 배수 2, 최대 간격 30초이며 최대 경과 시간은 `Long.MAX_VALUE`로 설정한다. 실패마다 WARN 로그에 `topic`/`partition`/`offset`/확인 가능한 `eventId`와 `attempt`(최초 실패 1)를 남긴다. **같은 파티션의 뒤 메시지는 앞 메시지가 처리될 때까지 대기한다.** 장애 원인을 복구하면 같은 레코드 처리를 재시도하며, 프로세스를 재시작해도 마지막 확정 오프셋부터 이어간다.

검증된 이벤트 처리 중의 모든 예외는 재시도한다. `setClassifications(Map.of(), true)`로 Spring Kafka의 기본 fatal 분류도 제거하므로 `NullPointerException`, `ClassCastException`, 처리 단계의 `IllegalArgumentException`도 소비 위치를 넘기지 않는다. 형식 오류 제외는 리스너의 파싱 구간에 한정된다. 복구 콜백도 로그만 남기고 성공 반환하지 않고 예외를 던지므로, DLT/실패 보관이 준비되기 전에 이벤트를 유실하지 않는다. 영구적인 코드 오류는 수정·재배포가 필요하며 그동안 같은 파티션의 뒤 메시지는 대기한다. 토픽·DLT와 운영 경보는 Kafka 담당자와 협의한다.

백오프 간격은 `match.kafka.retry.initial-interval-ms`(기본 `1000`), `match.kafka.retry.max-interval-ms`(기본 `30000`)로 주입한다(`0 < 초기 <= 최대`). `UserWithdrawnKafkaIntegrationTest`의 `@SpringBootTest(properties=...)`에서만 `10`/`50`ms로 줄여 5회 실패 후 성공을 빠르게 검증하며, 배수·무제한 경과 시간·운영 `earliest` 설정은 유지한다.

운영에서 `removeMatchesForWithdrawal`은 이 처리 경계 밖에서 호출하지 않는다. 새 트랜잭션에는 미리 적재된 `Match`가 없고, 매치 ID만 모은 뒤 오름차순 잠금 조회로 정리한다. MariaDB에서의 스냅샷·잠금·중복 처리는 위 MariaDB 통합 테스트로 확인했다.

## 생성·참가와 회원 탈퇴의 경합 방지

`match_user_state(user_id BIGINT PK, withdrawn BOOLEAN NOT NULL)`를 매치 DB에 둔다. 유저 서비스 DB와 FK를 연결하지 않는다. `user_id`는 재사용하지 않는다는 기존 계약을 따른다.

- 참가는 **원격 회원 존재·ACTIVE 확인 → 사용자 행 잠금 및 로컬 탈퇴 확인 → 매치 행 잠금 → 참가 조건 검사·저장** 순서다.
- 생성은 **원격 회원 존재·ACTIVE 확인 → 원격 시설 존재 확인 → 사용자 행 잠금 및 로컬 탈퇴 확인 → 매치·OWNER 저장** 순서다. 원격 확인 중에는 사용자·매치 행 잠금을 잡지 않으며, 참가의 잠금 순서는 **사용자 행 → 매치 행**이다.
- 탈퇴 처리도 사용자 행을 먼저 잠그고 `withdrawn=true`로 바꾼 뒤 매치 ID를 수집하고 오름차순으로 잠가 정리한다.
- 생성·참가가 먼저 잠갔다면 탈퇴 처리는 해당 쓰기의 커밋까지 기다린 뒤 그 기록까지 정리한다. 탈퇴가 먼저 커밋했다면 원격 확인을 통과한 생성·참가도 `401 / USER_WITHDRAWN`으로 거절한다. 원격 서비스가 오래된 ACTIVE 응답을 주더라도 쓰기 직전 잠근 로컬 기록이 최종 허용 여부를 결정한다.
- 최초 요청끼리 경합하는 경우도 MariaDB의 `INSERT ... ON DUPLICATE KEY UPDATE user_id=user_id`로 처리한다. 기존 `withdrawn=true`를 false로 되돌리지 않는다. 별도 트랜잭션/추가 커넥션을 열지 않는다.
- 원격 확인을 통과한 회원의 로컬 탈퇴 기록은 매치 존재 여부보다 먼저 검사한다. 획득한 잠금은 요청 트랜잭션 종료까지 유지하므로 같은 사용자의 로컬 쓰기는 직렬화된다. 단순한 두 번째 원격 조회로 대체하면 경합이 다시 생긴다.

원격 조회가 잠금보다 앞서므로 동시에 여러 오류가 있으면 다음 순서로 응답한다.

| 동시 오류 조건 | 우선 응답 |
|---|---|
| 참가: 매치·원격 회원 모두 없음 | `401 / MATCH_USER_NOT_FOUND` |
| 참가: 매치 없음·원격 회원 탈퇴 | `401 / USER_WITHDRAWN` |
| 생성·참가: 로컬 탈퇴·원격 회원 없음 | `401 / MATCH_USER_NOT_FOUND` |
| 생성·참가: 로컬 탈퇴·회원 조회 장애 | `503 / EXTERNAL_SERVICE_UNAVAILABLE` |
| 생성: 로컬 탈퇴·원격 ACTIVE·시설 없음/장애 | `404 / SERVICE_NOT_FOUND` 또는 `503 / EXTERNAL_SERVICE_UNAVAILABLE` |
| 참가: 로컬 탈퇴·원격 ACTIVE·매치 없음 | `401 / USER_WITHDRAWN` |
| 생성: 로컬 탈퇴·원격 ACTIVE·시설 정상 | `401 / USER_WITHDRAWN` |

모든 매치 인스턴스가 같은 잠금 규칙을 사용해야 하므로 기존 버전의 진행 중 요청을 종료한 뒤 전환한다. 이 변경 전에 이미 정리가 누락된 데이터와 이미 소비 완료된 이벤트는 자동으로 복구하지 않으며 별도 대조·정리가 필요하다.

H2 테스트는 테스트 전용 `schema.sql`로 MariaDB 호환 모드를 사용한다. SQL 초기화 기본값 `embedded`이므로 실제 MariaDB에서는 이 스크립트가 실행되지 않는다. 최종 경합 검증은 `MatchUserGuardMariaDBIntegrationTest`에서 실제 MariaDB 행 잠금과 커밋 결과로 확인한다.

### 운영 스키마 적용과 ERD 범위

`match_user_state` 보조 테이블은 최신 ERD·테이블 명세에 이미 반영되어 있다. 운영 `validate` 기동 전에 대상 DB에 맞춰 다음 SQL을 적용한다. 로컬 `update`는 테이블을 생성한다.

| 적용 대상 | SQL |
|---|---|
| 빈 `match_db` | [2026-11-02-match-schema.sql](docs/sql/2026-11-02-match-schema.sql) 하나로 현재 엔티티 4개 테이블 전체 생성 |
| `match`·`match_participant`가 있는 기존 DB | [2026-11-01-match-user-state.sql](docs/sql/2026-11-01-match-user-state.sql)과 [2026-11-02-processed-event.sql](docs/sql/2026-11-02-processed-event.sql) 적용 |

SQL은 모두 `CREATE TABLE IF NOT EXISTS`·`ENGINE=InnoDB`이며 데이터 삭제나 기존 테이블 변경을 하지 않는다. `IF NOT EXISTS`는 기존 잘못된 컬럼·제약·PK 순서를 고치지 않는다. 기존 DB의 스키마 불일치는 별도로 검증해야 하며, 데이터 변환이나 자동 ALTER는 제공하지 않는다. Hibernate `validate`만으로 UNIQUE·CHECK·PK 순서 전체를 검증할 수 없어 통합 테스트에서 메타데이터와 실제 제약 거절도 확인한다.

전체 SQL은 현재 구현 범위다. `match.status`는 ERD의 `VARCHAR(20)`·`RECRUITING/CONFIRMED/CANCELLING/CANCELLED`와 달리 엔티티의 `VARCHAR(10)`·`RECRUITING/CLOSED`를 따른다. ERD의 `target_participants`, `target_points`, `entry_points`, `funding_deadline_at`, `version`과 참가자의 `status`, `contribution_points`, `saga_id`, `version`은 아직 미구현이다. Saga·outbox 테이블도 포함하지 않는다. 미구현 NOT NULL 컬럼을 추가하면 현재 INSERT가 실패할 수 있으므로 ERD에서 내보낸 SQL을 그대로 실행하지 않는다. ID 자동 증가와 `ck_match_valid_fields`는 코드에서 구체화한 제약이며, DB 최소 정원은 1이고 API 최소 정원은 2다.

## 원본에서 바꾼 것

| 원본 | 변경 |
|---|---|
| `MatchEntity.service` (시설 엔티티 참조) | `match.service_id`에 서울시 svcId(`VARCHAR(50)`) 저장 |
| `MatchParticipantEntity.user` (회원 엔티티 참조) | `userId` 값만 저장 |
| `UserRepository`·`ProfileRepository` 직접 조회 | `UserClient` → 유저 서비스 Feign 호출 |
| `ServiceRepository` 직접 조회, 검색 시 시설·장소 JOIN | `FacilityClient` → 체육시설 서비스 Feign 호출 |
| 지역·무료 여부 검색을 매치 쿼리에서 JOIN | 체육시설 서비스에서 해당 svcId 문자열 목록을 받아 `IN` 조건으로 검색 |
| `createMatch`가 `SecurityContextHolder`에서 사용자 ID를 직접 읽음 | 컨트롤러에서 `@AuthenticationPrincipal`로 받아 전달 |
| `joinMatch`가 회원 존재만 확인해 탈퇴 회원도 참가 가능 | 회원 확인 직후 ACTIVE 여부를 검사해 탈퇴 회원의 참가를 차단 (`WithdrawnUserFoundException`, 401, `USER_WITHDRAWN`) |
| 생성·수정 요청에서 정원 1명 허용 | 최소 2명으로 Bean Validation 적용(400, `모집 정원은 2명 이상이어야 합니다.`). 검색 요청의 정원 조건은 유지 |
| 현재 참가 인원보다 작은 정원으로 수정 가능 | OWNER를 포함한 인원을 확인하고 변경 전에 거절(400, `{code, message}`, `MATCH_CAPACITY_BELOW_PARTICIPANTS`) |
| 정원을 수정해도 모집 상태 유지 | 모든 수정 후 현재 인원 ≥ 정원이면 `CLOSED`, 인원 < 정원이고 수정 후 시작 시각이 서울 현재시각보다 미래면 `RECRUITING`, 나머지는 기존 상태 유지. 정원이 그대로여도 시작 시각을 미래로 바꾸면 빈자리가 있는 매치는 재개된다 |
| 수정·삭제는 잠금 없는 매치 조회 | 참가·탈퇴·회원탈퇴 정리와 같은 `findByIdForUpdate` 행 잠금을 먼저 획득. 상세 조회는 일반 조회 유지 |
| 디버그용 `System.out.println` | 삭제 |

1단계 분리에서는 원본 테스트 101개가 검증하는 범위의 동작을 유지했다. 이후 정원·모집 상태와 수정·삭제 잠금은 위 표와 같이 보완했으며, 원격 서비스 관련 동작 차이는 아래에 정리했다.

### 원본과 달라진 동작

- 체육시설 서비스 응답에 해당 시설이 없으면 목록·내 매치·상세의 시설 조회 필드는 `null`로 반환한다. 상세의 `serviceId`는 시설 응답의 숫자 `id`로 채우며 시설을 찾지 못하면 `null`이다. 원본은 시설 FK/JOIN을 전제로 한다.
- 유저·체육시설 호출의 HTTP 오류·연결 실패·타임아웃·JSON 해석 실패와 필수 필드 누락은 `ExternalServiceUnavailableException`으로 매핑하며, 아래 기준으로 부분 응답 또는 `503 / EXTERNAL_SERVICE_UNAVAILABLE`을 반환한다. 회원 단건 조회 404는 기존처럼 빈 결과로 처리한다(생성·참가 시 `401 / MATCH_USER_NOT_FOUND`). 운영 Feign 타임아웃은 연결 1000ms·응답 3000ms이며, 생성·참가의 원격 확인은 로컬 행 잠금 전에 수행한다. 동시 오류의 응답 우선순위는 위 표와 같다.
  - 목록·내 매치의 시설 정보 조합: 장애 시 시설 필드만 `null`인 `200`. 매치 목록·참가 인원·역할은 유지한다. 내 매치의 로그인 회원 확인(`findUser`) 장애는 `503`이다.
  - 상세·수정 응답 조합: 프로필·시설을 각각 조회하며, 실패한 쪽의 필드만 `null`인 `200`. 시설 필드는 `serviceId`, `serviceName`, `locationName`, `region`, 프로필 필드는 `profileId`, `nickname`, `imageUrl`이다. 둘 다 실패하면 둘 다 비우고, 참가 인원·역할·`isOwner`·`isParticipant`는 유지한다.
  - 검색 조건의 숫자 `serviceId` 확인(`findServiceById`)·지역/무료 필터(`searchServiceIds`), 생성·참가의 회원·시설 확인: 장애 시 `503`. 생성·참가는 매치·참가자를 저장하지 않는다.
  - 부분 응답은 `ExternalServiceUnavailableException`만 처리하고 서비스 이름을 WARN 로그에 남긴다(요청 원문·원인 예외는 기록하지 않음). 다른 예외는 전파하며 포인트 차감·환불 실패 처리도 유지한다.
- 인증이 필요한 경로에서 토큰이 없거나 손상·만료·Refresh 토큰으로 인증에 실패하면 `401`, `{"code":"UNAUTHORIZED","message":"인증에 실패했습니다."}`를 JSON·UTF-8로 반환한다. 도메인의 회원 없음·탈퇴 오류 코드는 각각 `MATCH_USER_NOT_FOUND`·`USER_WITHDRAWN`을 유지한다. 공개 상세 조회의 잘못된 토큰은 기존처럼 비로그인으로 처리한다.
- `read-timeout`은 호출 1건의 응답 대기 상한이며 전체 요청·잠금 점유 시간의 상한은 아니다. 잠금 안에서 원격 호출을 여러 번 하면(예: 수정 응답의 프로필→시설 순차 조회) 대기 시간이 합쳐질 수 있다.
- 매치 수정 후 응답 조합의 프로필→시설 원격 조회는 여전히 매치 행 잠금 안에서 순차 실행한다. 한쪽의 원격 장애가 나도 다른 쪽 조회를 수행한다. 같은 트랜잭션 경계 안에서 장애를 처리해 수정을 커밋하고 위 부분 응답 규칙으로 `200`을 반환한다.
- 회원 탈퇴 정리 메서드 `removeMatchesForWithdrawal`은 `UserWithdrawn` Kafka 처리 서비스에서 호출한다. 기본값은 수신 비활성이며, 실제 유저 서비스·Kafka와의 연동은 아직 검증하지 않았다.

## 검증

**최종 실행(2026-11-02, Docker MariaDB 11.4.8 포함): `gradle cleanTest test` 248개 통과, 실패·오류·건너뜀 0개. 실제 MariaDB 19개(운영 스키마 4개 포함), EmbeddedKafka 10개, 기본 시간대 변경 회귀 2개를 포함한다.**

2026-11-04 조회 부분 응답 변경 후 실행(Docker MariaDB 포함): `./gradlew clean test` 305개 중 304개 통과, 건너뜀 0개. 실패 1개는 develop에 이미 있던 `MatchSchemaMariaDBIntegrationTest.deploymentSqlMatchesEntityConstraints`(포인트 칸 `target_points`·`contribution_points` 미반영)로, PR #44에서 따로 고친다.

Docker가 없으면 MariaDB 통합 테스트는 실패가 아니라 건너뜀으로 끝난다. 통과 개수만 보지 말고 `build/reports/tests/test/index.html`에서 건너뜀이 0개인지 함께 확인한다.

| 범위 | 테스트 | 확인 내용 |
|---|---|---|
| 원본 동작 유지 | 원본 매치 테스트 | 회원·시설 데이터를 메모리 가짜 클라이언트로 바꿔 원본 동작 확인 |
| 정원·모집 상태 | `MatchCapacityControllerTest`, `MatchModifyIntegrationTest` | 최소 정원과 전용 오류 응답, 거절 시 DB 보존, 정원 유지·미래 재일정 시 재개, 만석/시작 후 상태 유지, 참가와 정원 축소 동시 요청 |
| 인증 실패 | `MatchJoinControllerTest`, `MatchDetailControllerTest` | 무토큰·손상·만료·Refresh 토큰의 401 JSON·UTF-8과 처리 미호출, 공개 상세의 비로그인 처리 유지 |
| 잠금 경합 | `MatchLockContentionIntegrationTest` (H2) | 한 트랜잭션이 매치 행 잠금을 잡은 동안 수정·참가·삭제가 실제로 기다리고, 잠금이 풀린 뒤 최종 상태가 맞음 |
| 회원 탈퇴 정리 | `MatchWithdrawalIntegrationTest` | OWNER 매치 삭제, 본인 참가만 취소, 마감 재개, 시작 후 마감 유지, ID 조회 뒤 커밋된 정원 수정 보존 |
| Feign HTTP 계약 | `FeignUserClientHttpTest`, `FeignFacilityClientHttpTest` | 실제 Feign과 JDK 내장 HTTP 스텁으로 경로, 반복 ID 쿼리, 한글·선택 파라미터, JSON 매핑, 404/500, 시간 초과, 빈 입력 시 요청 생략 확인. 연결 1초/응답 1500ms는 테스트 전용 값 |
| 원격 장애 | `MatchRemoteFailureHttpIntegrationTest`, `MatchModifyConnectionFailureHttpIntegrationTest` | 목록·내 매치의 시설 500, 상세·수정의 프로필/시설 500에 200 부분 응답과 정상인 쪽 정보 보존을 단언. 수정의 연결 실패·시간 초과·JSON 오류도 커밋하며 프로필 유지. 검색 조건·회원 확인 및 생성·참가의 503/저장 안 함 계약 유지. 원인 예외 단언 포함 |
| 설정 | `MatchServiceApplicationTests`, `CloudProfileConfigurationTest`, `FeignTimeoutConfigurationTest`, `FeignDiscoveryClientHttpTest` | 전체 컨텍스트 기동, Eureka·Config·Kafka 기본 꺼짐, health/info·probe 공개와 다른 Actuator 경로 비노출, 프로필별 설정, 운영 Feign 타임아웃, Eureka 없이 서비스 이름 기반 Feign 호출 |
| Kafka 탈퇴 수신 | `UserWithdrawnKafkaIntegrationTest`, `UserWithdrawnKafkaStartupIntegrationTest` (EmbeddedKafka) | 정상 정리, 같은 ID 중복, 잘못된 메시지 건너뜀, 동시 PK 충돌, 일시 장애 동안 같은 파티션 대기와 복구 후 처리, 프로그래밍 오류 동안 오프셋 보존 및 복구 후 정상 처리, 실제 그룹 오프셋 확정, 리스너가 꺼져 있던 동안 발행된 이벤트를 `earliest`로 처리 |
| 실제 MariaDB | `MatchMariaDBIntegrationTest`, `MatchUserGuardMariaDBIntegrationTest`, `MatchSchemaMariaDBIntegrationTest` (Testcontainers) | 격리 수준·매치 잠금·이벤트 중복, 쓰기/탈퇴 선점 경합, 회원·시설 원격 조회 중 잠금 취득·탈퇴 커밋과 오래된 ACTIVE 거절, 전체 SQL만 적용한 validate 기동·제약 검증·데이터 보존, 두 추가 SQL의 보조 테이블 복원·이력/탈퇴 상태 보존 |
| 테스트 격리 | `MatchDatabaseCleanupTest` 및 실제 커밋하는 통합 테스트의 사후 정리 | 작업 스레드·리스너 종료 후 참가자→매치→처리 이력→사용자 상태 정리, 공유 컨텍스트의 잔여 데이터 방지 |
| 시간대 | `MatchTimeZoneTest` | JVM 기본 시간대를 UTC·미국 서부로 바꿔도 서울 시각 기준 시작 후 참가 차단·탈퇴/정원 변경 후 모집 마감 유지 |

변이 검증 기록: 일부러 코드를 바꿔 아래 테스트 실패를 확인한 뒤 원복했다.

- `modifyMatch`의 잠금 조회를 일반 조회로 바꾸면 잠금 경합 테스트가 실패한다.
- 탈퇴 처리의 `READ_COMMITTED` 지정을 빼면 MariaDB 격리 수준 단언이 실패한다.
- 정원 수정의 시작 시각 조건을 빼면 `시작 시각이 지난 CLOSED 매치는 정원을 늘려도 모집을 재개하지 않는다` 테스트가 실패한다.
- (2026-11-02) 수정 후 모집 상태 재계산을 빼면 `시작 후 탈퇴로 빈자리가 생긴 CLOSED 매치를 정원 변경 없이 미래로 옮기면 재참가할 수 있다` 테스트가 실패한다.
- (2026-11-02) 인증 실패 JSON 작성을 빼고 상태 코드만 설정하면 무토큰·잘못된 토큰 401 테스트가 실패한다.
- (2026-11-02) 생성·참가의 원격 조회를 잠금 뒤로 되돌리면 원격 조회 중 잠금 취득 테스트(참가·생성)와 오류 우선순위 테스트가 실패한다.
- (2026-11-02) 전체 운영 SQL에서 `processed_event`를 빼면 운영 스키마 테스트 4개가 모두 실패한다.

이전 반복 실행 기록(2026-10-31, 이번 변경에서는 재실행하지 않음): 잠금 경합·탈퇴 테스트 10회, Kafka·MariaDB 테스트 3회 연속 통과.

Docker 수동 확인(PR #5·#6 시점, Apple Silicon): 이미지 빌드, 비루트 사용자(`uid=10001`), Compose 기동, `/actuator/health` 200, `/actuator/env`·`/actuator/beans` 401, `GET /api/matches` 200 `[]`, `GET /api/matches?isFree=true` 503(체육시설 서비스 없음), Eureka·Config 오류 로그 0건, graceful shutdown. liveness·readiness는 probe를 켜지 않은 로컬에서 404이며 K8s에서는 자동으로 켜진다.

아직 확인하지 않은 것:

- 실제 유저·체육시설 서비스와의 연동(상대 API가 아직 없음)
- 실제 Eureka 등록·탐색, Config 서버에서 설정 받기
- 유저 서비스가 실제로 발행한 Kafka 이벤트 수신
- Docker가 없는 환경에서 MariaDB 테스트가 건너뛰어지는 동작
- 동시 참가·정원 변경 전체의 MariaDB 검증. MariaDB에서는 탈퇴 정리, 잠금 대기, 중복 처리, 스키마만 확인했다.

옮기지 않은 테스트: 원본 `MatchIntegrationTest`(원본에서도 MariaDB가 필요해 기본 실행에서 빠지던 테스트). 실제 MariaDB 확인은 새 `MatchMariaDBIntegrationTest`로 한다.

## 서비스 구성 기준

1. 폴더 구조: `services/<서비스명>` 아래 서비스별 독립 Gradle 프로젝트
2. 패키지 구조: 원본과 같게 유지(`com.example.sporty.features.exerciseMatching`) — 원본과 비교하기 쉽게
3. 포트: 매치 8082, 유저 8081, 체육시설 8083
4. 인증: 매치 서비스가 Access Token을 직접 검증한다(원본 JWT 검증 방식 유지, 인증 실패는 공통 JSON 응답). Gateway에서 사용자 ID 헤더를 넘기는 방식으로 바꾸면 필터만 교체한다.
5. 서비스 간 내부 API: 아래 경로와 응답 형식

## 다른 서비스가 제공하는 내부 API

유저·체육시설 서비스는 아래 경로와 응답 형식으로 내부 API를 제공한다.

**유저 서비스**

| 용도 | 요청 | 응답 |
|---|---|---|
| 회원 존재·상태 확인 (생성·참가·내 매치) | `GET /internal/users/{userId}` | `{"userId":1,"status":"ACTIVE"}`, 없으면 404 |
| 상세 화면 참가자 프로필 | `GET /internal/users/profiles?userIds=1&userIds=2` | `[{"userId":1,"profileId":10,"nickname":"..","imageUrl":".."}]` |

**체육시설 서비스**

| 용도 | 요청 | 응답 |
|---|---|---|
| 목록·상세·내 매치에 저장된 svcId로 시설 정보 표시 | `GET /internal/facility-services?ids=S240101000000000001&ids=S240101000000000002` | `[{"id":1,"serviceId":"S240101000000000001","serviceName":"..","locationName":"..","region":"강남구","isFree":true,"reservationDeadlineAt":"2026-11-01T18:00:00"}]` |
| 생성·목록 검색의 숫자 시설 PK를 svcId로 변환 | `GET /internal/facility-services/by-id?ids=1&ids=2` | `[{"id":1,"serviceId":"S240101000000000001","serviceName":"..","locationName":"..","region":"강남구","isFree":true,"reservationDeadlineAt":"2026-11-01T18:00:00"}]` |
| 지역·무료 여부 검색 | `GET /internal/facility-services/ids?region=강남&isFree=true` | `["S240101000000000001","S240101000000000002"]` (region은 대소문자 무시 부분 일치, 비우면 조건 제외) |

## 남은 일

- **회원 탈퇴 연동:** `UserWithdrawn` 구독·처리 이력·무제한 재시도 경계를 추가했다(기본 비활성). 유저 서비스 발행·토픽 준비 후 실제 연동 확인이 필요하다.
- **AI 서비스 연동:** 원본 AI는 `MatchService.searchMatches`와 참가 여부 조회를 직접 썼다. AI 서비스가 `GET /api/matches`를 쓰고, 추천의 "이미 참가한 매치 제외"는 별도 조건이 필요하다.
- **생성·참가의 원격 호출 위치 — 반영:** 원격 회원·시설 확인을 로컬 행 잠금 앞으로 옮겼다. 원격 조회 중 탈퇴가 커밋되어도 쓰기 직전 로컬 guard가 오래된 ACTIVE를 거절한다. 동시 오류의 응답 우선순위는 위 표와 같으며, 수정 응답의 원격 조회는 여전히 매치 잠금 안에 남아 있다.
- **다른 서비스 장애 시 처리:** 조회·수정 응답 조합은 장애가 난 원격 정보만 비우는 부분 응답을 적용했다. 검색 조건·회원 확인·생성·참가의 원격 확인은 `503 / EXTERNAL_SERVICE_UNAVAILABLE`을 유지한다. 운영 Feign 연결 1000ms·응답 3000ms이며, Circuit Breaker 도입(선택 요구사항 5번)은 추가 검토한다.
- **수정·참가·삭제 경합 테스트 — 완료:** `MatchLockContentionIntegrationTest`에서 A의 실제 행 잠금 획득 후 B의 조회 반환·서비스 완료가 400ms 동안 대기하고, A 커밋 후 일관된 결과가 저장되는지 단언한다(테스트 H2 `LOCK_TIMEOUT=10000`).
- **회원 탈퇴 정리의 최신 조회 — 완료:** 엔티티 대신 매치 ID만 오름차순으로 조회한 뒤 잠금 후 읽은 매치·참가 정보로 정리하며, OWNER 삭제·본인만 참가 취소·마감 재개·시작 후 마감 유지와 ID 조회 사이에 커밋된 정원 수정 보존 테스트를 추가했다. 호출 경계의 영속성 컨텍스트 공유는 Kafka 처리 경계의 새 트랜잭션으로 해결했고, ID 수집 후 커밋된 참가자·정원 변경 반영은 실제 MariaDB로 확인했다.
- **수정 응답 장애 테스트 — 완료:** 실제 Feign의 시설 시간 초과·JSON 오류·연결 거부에서도 수정 200·DB 반영·시설 필드 null을 단언하고, 프로필 정상/시설 500은 시설 요청 기록까지 확인한다. 시간 초과·JSON 오류·연결 거부의 실제 원인 예외(`SocketTimeoutException`, `DecodeException`, `ConnectException`)도 단언한다.
- **Eureka·Config 실제 연동 및 Gateway:** 클라이언트 연결 준비는 추가했으며 기본값은 비활성이다. 공통 설정 담당(이우진)의 서버가 준비되면 위 환경 변수로 활성화하고 실제 등록·설정 수신을 검증한다. Gateway 연동은 별도 후속 작업이다.
- **전체 Compose 연동:** 매치 Dockerfile·매치 단독 로컬 Compose는 추가했다. 전체 서비스 Compose 통합은 인프라 담당과 진행한다.
- **포인트 Saga:** ERD의 포인트·Saga 컬럼과 새 상태값은 Saga 규칙을 정한 뒤 반영한다.

## 탈퇴 연동 전 반드시 해결 — 두 항목 해결

- **(1) 구조적 해결 — 완료:** 탈퇴 처리는 Kafka 리스너가 `UserWithdrawnHandler.handle`을 통해 여는 새 `REQUIRES_NEW` 트랜잭션에서만 호출한다. 기존 `Match`가 적재된 영속성 컨텍스트를 공유하지 않으며, 운영에서 `removeMatchesForWithdrawal`은 이 처리 경계 밖에서 호출하지 않는다.
- **(2) READ_COMMITTED 지정·MariaDB 검증 — 완료:** 처리 경계에 `Isolation.READ_COMMITTED`를 명시했다. 실제 MariaDB 11.4.8에서 ID 수집 후 A 탈퇴·정원 3 수정이 커밋되는 시나리오의 기본 REPEATABLE READ 오류(최종 2명/정원 3/CLOSED)와 READ_COMMITTED 정상 동작(최종 2명/정원 3/RECRUITING)을 모두 확인했다. 오류 재현 테스트는 유지한다. 일반적인 동시 참가·정원 변경 전체의 MariaDB 검증까지 완료한 것으로 해석하지 않는다.

## 알려진 한계

- 생성·참가의 원격 회원·시설 확인 중에는 사용자·매치 행 잠금을 잡지 않는다. 두 메서드의 기존 `@Transactional` 경계는 유지하므로 트랜잭션·커넥션 점유까지 제거한 것은 아니다. 수정 응답의 프로필·시설 조회는 매치 행 잠금 안에 남아 있어 원격 지연 시 같은 매치의 참가·탈퇴·수정·삭제가 대기하고, 잠금 시간 초과 시 요청 실패 또는 Kafka 재시도로 이어질 수 있다.
- 처리 오류의 무제한 재시도 동안 같은 파티션의 후속 메시지도 대기한다. 영구 오류는 코드 수정·재배포와 운영 경보가 필요하며 DLT·실패 이벤트 보관은 Kafka 담당(이우진)과 협의해야 한다.
- `processed_event`의 PK 순서는 생성 방식에 따라 다르다. Hibernate 생성 스키마는 `(event_id, consumer_name)`, 전체·추가 운영 SQL은 ERD와 같은 `(consumer_name, event_id)`다. 조회는 항상 전체 키를 사용하므로 동작 영향은 없다. `IF NOT EXISTS`로 재적용해도 기존 PK 순서는 바뀌지 않는다.
