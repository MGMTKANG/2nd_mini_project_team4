# match-service

운동 매칭 서비스. 모놀리식 `backend/sporty`의 `exerciseMatching` 코드를 떼어 낸 독립 Spring Boot 앱이다.
1단계는 원본 매치 테스트 101개로 확인한 범위에서 도메인 로직을 유지하며 분리했다. 매치 상태는 기존과 같은 `RECRUITING/CLOSED`이다.

- 담당: 김승진
- 소유 DB: `match_db` (`match`, `match_participant`, `processed_event`)
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
- 유저·체육시설 URL은 기본값(`http://localhost:8081`, `http://localhost:8083`)이며 연결되지 않는다. `curl -i 'http://localhost:8082/api/matches?isFree=true'` 같은 원격 호출은 `503 / EXTERNAL_SERVICE_UNAVAILABLE`이 정상이며, 기존 매치가 있으면 기본 목록도 시설 조회로 `503`이 될 수 있다.
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

운영 DB는 명시적인 스키마 마이그레이션을 먼저 적용한 뒤 `MATCH_JPA_DDL_AUTO=validate`로 실행하는 것을 권장한다. `validate`는 스키마를 생성·변경하지 않으므로 마이그레이션과 DB별 사전 검증이 필요하다. 기본 `update`는 현재 로컬 동작을 유지한다.

Actuator 웹 노출은 `health,info`뿐이며 두 경로는 비로그인 접근을 허용한다. Compose 앱 healthcheck는 미설정이다. K8s에서는 `management.endpoint.health.probes.enabled=true`(환경 변수 `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`)로 활성화하고, liveness는 `/actuator/health/liveness`, readiness는 `/actuator/health/readiness`를 사용한다. 두 probe 경로도 비로그인 접근을 허용한다.

앱은 graceful shutdown과 종료 단계 제한 `20s`를 사용하며, Compose는 `stop_grace_period: 30s`로 종료를 기다린다.

테스트: `./gradlew test` (기존 H2 테스트와 아래 MariaDB 통합 테스트. 다른 서비스 없이 실행되며 Docker가 없으면 MariaDB 테스트만 자동으로 건너뛴다. 새 의존성·컨테이너 이미지를 최초로 받으려면 네트워크가 가능한 빌드 환경 필요)

### MariaDB 통합 테스트 (4b)

`MatchMariaDBIntegrationTest`는 Testcontainers로 `compose.match-local.yml`과 같은 `mariadb:11.4.8`을 실행한다. Docker 데몬에 접근할 수 있어야 하며 로컬 Compose DB나 실서비스 DB는 사용하지 않는다. `MariaDBIntegrationTestSupport`는 MariaDB dialect·`ddl-auto=create-drop`을 적용하고, 정적 컨테이너를 같은 테스트 JVM의 클래스 사이에서 공유한다. 클래스별 `@Container` 종료 대신 Testcontainers 리소스 정리기가 JVM 종료 시 컨테이너를 제거한다. `@Testcontainers(disabledWithoutDocker = true)`로 Docker 없는 CI에서는 이 테스트만 건너뛴다. Docker가 있는데 이미지 다운로드·DB 기동·단언이 실패하면 테스트 실패로 남는다.

```bash
cd services/match-service
./gradlew test --tests '*MatchMariaDBIntegrationTest'
# 전체 회귀 테스트
./gradlew cleanTest test
```

Gradle 의존성과 MariaDB·Testcontainers 보조 이미지가 모두 준비된 환경에서는 `--offline`으로 Gradle의 의존성 다운로드를 막을 수 있다. 이 옵션은 Docker 이미지 다운로드를 차단하지 않는다. 건너뜀 여부와 실행 결과는 `build/reports/tests/test/index.html` 및 `build/test-results/test/TEST-*.xml`에서 확인한다.

- 스냅샷 회귀: OWNER+A+B+U, 정원 4, CLOSED에서 처리기의 실제 ID SELECT 직후 저장소 스파이·래치로 멈춘다. 별도 트랜잭션으로 A 탈퇴를 커밋하고 OWNER가 정원 3으로 수정·커밋한 뒤 U 처리를 재개한다. 최종 OWNER+B 2명·정원 3·RECRUITING을 단언한다. 호출자의 기본 REPEATABLE READ 트랜잭션에 오래된 Match도 적재해 두고, 처리기 연결의 실제 READ_COMMITTED와 호출자 엔티티의 분리도 확인한다.
- 잠금: `findByIdForUpdate` 보유자가 참가자 추가를 커밋하기 전까지 수정의 SELECT 자체가 반환되지 않고, 이후 커밋한 인원으로 CLOSED가 되는지 확인한다.
- 멱등 처리: 같은 ID의 순차 중복은 처리 시각·후속 매치를 보존한다. 동시 두 호출이 모두 미처리를 조회하도록 동기화한 뒤 실제 PK 충돌(오류 1062 / SQLState 23000), 실패 측 롤백 후 정상 반환, 이력 1행·정리 1회를 확인한다. 실제 리스너→처리기를 직접 호출하며 Kafka 전송·오프셋은 기존 EmbeddedKafka 테스트 범위다.
- 스키마: 예약어 테이블 `match`의 조회·InnoDB, `(match_id, user_id)` UNIQUE와 `processed_event` 복합 PK의 메타데이터 및 실제 중복 거절을 확인한다. 현재 엔티티가 만드는 스키마의 검증이며 최신 ERD의 미확정 Saga 스키마 전체를 검증하는 것은 아니다.
- b의 `defaultRepeatableReadReproducesClosedStatusAfterWithdrawal`은 같은 시나리오를 격리 지정 없는 새 트랜잭션에서 `removeMatchesForWithdrawal` 직접 호출로 실행한다. 기본 REPEATABLE READ 연결·최종 2명/정원 3/CLOSED 오류를 단언하며 집계 결과는 스텁하지 않는다. **2026-10-31 실제 MariaDB에서 재현 확인(Claude 실행). 오류 재현 테스트를 유지한다.**
- **수정 전 검증(Claude 실행 보고, 2026-10-31): Docker MariaDB 11.4.8에서 전체 211개 통과·skipped 0, MariaDB+Kafka 테스트 3회 반복 통과.** PK 컬럼 순서 단언 수정 후 결과이며, READ_COMMITTED 제거 변이는 실제 격리 수준 단언에서 실패하는 것을 확인했다. Codex가 이번 작업에서 재실행한 결과는 아니다. **이번 Kafka 수신 보완 후 결과: 2026-10-31 Claude 실행 보고(샌드박스 밖, Docker MariaDB 11.4.8): `gradle cleanTest test` 213개 통과·skipped 0, `UserWithdrawnKafkaIntegrationTest`·`UserWithdrawnKafkaStartupIntegrationTest`·`MatchMariaDBIntegrationTest` 3회 반복 통과.** Docker 없는 환경의 자동 건너뜀은 미확인이다.

## 회원 탈퇴 이벤트 수신

기본값 `MATCH_KAFKA_ENABLED=false`에서는 리스너 컨테이너가 시작되지 않는다. Kafka Admin의 토픽 자동 생성과 컨슈머의 토픽 자동 생성 요청도 끄므로 Kafka 없이 앱을 기동할 수 있다. Kafka와 유저 서비스가 준비되면 `MATCH_KAFKA_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=<브로커>:9092`로 설정하고 재시작한다. 컨슈머 그룹은 `match-service`, 처리 주체는 `match-service.user-withdrawn`이다. 발행 코드와 토픽 생성 코드는 포함하지 않는다.

Compose도 `.env`의 `MATCH_KAFKA_ENABLED`(기본 `false`), `KAFKA_BOOTSTRAP_SERVERS`, `USER_WITHDRAWN_TOPIC`을 매치 컨테이너에 전달한다. 브로커 주소는 컨테이너에서 접근 가능한 값으로 설정한다. 새 그룹처럼 저장된 오프셋이 없으면 운영 설정 `spring.kafka.consumer.auto-offset-reset=earliest`로 토픽에 남아 있는 가장 오래된 이벤트부터 처리한다. 이미 확정된 오프셋은 그대로 사용하며, 브로커 보존기간이 지나 삭제된 이벤트는 이 설정으로 복구할 수 없다.

유저 서비스 담당자가 준비할 계약(공통 규약 5절 초안 기준):

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

처리 순서는 새 `REQUIRES_NEW / READ_COMMITTED` 트랜잭션 시작 → `(consumer_name, event_id)` 존재 확인 → 없으면 명시적 INSERT → `removeMatchesForWithdrawal(userId)` → DB 커밋이다. 이미 처리된 ID는 매치 정리를 실행하지 않는다. 동시 INSERT의 PK 충돌은 실패한 트랜잭션을 먼저 롤백한 뒤 처리 이력을 다시 조회해, 다른 처리가 커밋한 이력이 있으면 중복으로 정상 반환한다. 그 외 삽입 오류는 재시도 대상으로 전달한다. 처리 이력과 매치 변경은 같은 트랜잭션이므로 정리 실패 시 함께 롤백한다. `processed_event`는 ERD의 `consumer_name VARCHAR(100)`, `event_id CHAR(36)`, `processed_at DATETIME`, 복합 PK를 매핑한다.

리스너에는 Kafka 트랜잭션을 사용하지 않는다. 컨슈머의 자동 커밋을 끄고 `ack-mode=record`를 사용해, 처리 서비스의 DB 트랜잭션이 끝나고 리스너가 정상 반환한 뒤 컨테이너가 오프셋을 확정한다. DB 커밋 후 오프셋 확정 전 중단돼 재전달되면 처리 이력으로 건너뛴다. DB·연결 등 일시 오류는 `DefaultErrorHandler`와 `ExponentialBackOff`로 무제한 재시도한다. 초기 1초, 배수 2, 최대 간격 30초이며 최대 경과 시간은 `Long.MAX_VALUE`로 설정한다. 실패마다 WARN 로그에 `topic`/`partition`/`offset`/확인 가능한 `eventId`와 `attempt`(최초 실패 1)를 남긴다. **같은 파티션의 뒤 메시지는 앞 메시지가 처리될 때까지 대기한다.** 장애 원인을 복구하면 같은 레코드 처리를 재시도하며, 프로세스를 재시작해도 마지막 확정 오프셋부터 이어간다.

`JsonProcessingException`, `IllegalArgumentException`, `NullPointerException`, `ClassCastException`은 `addNotRetryableExceptions`로 즉시 건너뛴다. Spring Kafka의 기본 재시도 불가 분류도 유지한다. 형식 오류·명백한 프로그래밍 오류를 건너뛸 때는 ERROR 로그에 토픽·파티션·오프셋·확인 가능한 이벤트 ID를 남긴다. 프로그래밍 오류는 코드 수정 후 해당 오프셋의 수동 재처리가 필요하다. 토픽·DLT 생성과 별도 실패 보관은 Kafka 담당 영역이다.

백오프 간격은 `match.kafka.retry.initial-interval-ms`(기본 `1000`), `match.kafka.retry.max-interval-ms`(기본 `30000`)로 주입한다(`0 < 초기 <= 최대`). `UserWithdrawnKafkaIntegrationTest`의 `@SpringBootTest(properties=...)`에서만 `10`/`50`ms로 줄여 5회 실패 후 성공을 빠르게 검증하며, 배수·무제한 경과 시간·운영 `earliest` 설정은 유지한다.

운영에서 `removeMatchesForWithdrawal`은 이 처리 경계 밖에서 호출하지 않는다. 새 트랜잭션에는 미리 적재된 `Match`가 없고, 매치 ID만 모은 뒤 오름차순 잠금 조회로 정리한다. MariaDB에서의 스냅샷·잠금·중복 처리 검증은 4b의 해당 테스트로 확인했다(Claude 실행 보고, 2026-10-31). 수정 전 211개 통과와 이번 수정 후 실행 예정 상태는 위 검증 기록을 따른다.

## 원본에서 바꾼 것

| 원본 | 변경 |
|---|---|
| `MatchEntity.service` (시설 엔티티 참조) | `serviceId` 값만 저장 |
| `MatchParticipantEntity.user` (회원 엔티티 참조) | `userId` 값만 저장 |
| `UserRepository`·`ProfileRepository` 직접 조회 | `UserClient` → 유저 서비스 Feign 호출 |
| `ServiceRepository` 직접 조회, 검색 시 시설·장소 JOIN | `FacilityClient` → 체육시설 서비스 Feign 호출 |
| 지역·무료 여부 검색을 매치 쿼리에서 JOIN | 체육시설 서비스에서 해당 `serviceId` 목록을 받아 `IN` 조건으로 검색 |
| `createMatch`가 `SecurityContextHolder`에서 사용자 ID를 직접 읽음 | 컨트롤러에서 `@AuthenticationPrincipal`로 받아 전달 |
| `joinMatch`가 회원 존재만 확인해 탈퇴 회원도 참가 가능 | 회원 확인 직후 ACTIVE 여부를 검사해 탈퇴 회원의 참가를 차단 (`WithdrawnUserFoundException`, 401, `USER_WITHDRAWN`) |
| 생성·수정 요청에서 정원 1명 허용 | 최소 2명으로 Bean Validation 적용(400, `모집 정원은 2명 이상이어야 합니다.`). 검색 요청의 정원 조건은 유지 |
| 현재 참가 인원보다 작은 정원으로 수정 가능 | OWNER를 포함한 인원을 확인하고 변경 전에 거절(400, `{code, message}`, `MATCH_CAPACITY_BELOW_PARTICIPANTS`) |
| 정원을 수정해도 모집 상태 유지 | 정원이 바뀌면 현재 인원과 같을 때 `CLOSED`, 더 클 때 `RECRUITING`으로 재계산 |
| 수정·삭제는 잠금 없는 매치 조회 | 참가·탈퇴·회원탈퇴 정리와 같은 `findByIdForUpdate` 행 잠금을 먼저 획득. 상세 조회는 일반 조회 유지 |
| 디버그용 `System.out.println` | 삭제 |

1단계 분리에서는 원본 테스트 101개가 검증하는 범위의 동작을 유지했다. 이후 정원·모집 상태와 수정·삭제 잠금은 위 표와 같이 보완했으며, 원격 서비스 관련 동작 차이는 아래에 정리했다.

### 원본과 달라진 동작

- 체육시설 서비스 응답에 해당 시설이 없으면 목록·내 매치·상세의 시설 조회 필드는 `null`로 반환한다(상세의 `serviceId`는 유지한다). 원본은 시설 FK/JOIN을 전제로 한다.
- 유저·체육시설 호출의 HTTP 오류·연결 실패·타임아웃·JSON 해석 실패와 필수 필드 누락은 `503`, `{"code":"EXTERNAL_SERVICE_UNAVAILABLE","message":"..."}`로 반환한다. 회원 단건 조회 404는 기존처럼 빈 결과로 처리한다(참가 시 `401 / MATCH_USER_NOT_FOUND`). 운영 Feign 타임아웃은 연결 1000ms·응답 3000ms이며, 참가의 잠금 후 회원 조회 순서는 유지한다.
- `read-timeout`은 호출 1건의 응답 대기 상한이며 전체 요청·잠금 점유 시간의 상한은 아니다. 잠금 안에서 원격 호출을 여러 번 하면(예: 수정 응답의 프로필→시설 순차 조회) 대기 시간이 합쳐질 수 있다.
- 매치 수정 후 응답 조합 중 원격 장애가 나면 같은 트랜잭션 경계 안에서 처리해 수정을 커밋하고 `200`을 반환한다. 이때 시설 조회 필드(`serviceName`, `locationName`, `region`)와 참가자 프로필 필드(`profileId`, `nickname`, `imageUrl`)는 `null`이며 시설 ID·참가 인원·역할·권한은 유지한다. 생성·참가는 저장 전 원격 확인에 실패하면 `503`이며 매치·참가자를 저장하지 않는다.
- 회원 탈퇴 정리 메서드 `removeMatchesForWithdrawal`은 `UserWithdrawn` Kafka 처리 서비스에서 호출한다. 기본값은 수신 비활성이며, 실제 유저 서비스·Kafka와의 연동은 아직 검증하지 않았다.

## 검증

- 원본 매치 테스트 11개 파일(101개)을 옮겨 모두 통과. 회원·시설 데이터는 메모리 가짜 클라이언트로 대체했다.
- 전체 설정(Feign 클라이언트 포함)이 뜨는지 확인하는 `MatchServiceApplicationTests`를 유지하고 기본 Eureka·Config 비활성값, 비로그인 health/info·probe 접근, 다른 Actuator 경로의 비노출 검증을 추가했다.
- `FeignDiscoveryClientHttpTest` 추가: Eureka 없이 `SimpleDiscoveryClient`에 두 스텁 주소를 등록하고 두 URL을 빈 값으로 설정한다. 실제 `FeignUserClient`·`FeignFacilityClient`, LoadBalancer 클라이언트와 HTTP 요청 기록을 확인한다.
- `CloudProfileConfigurationTest` 추가: `local/dev/prod`의 SQL 출력·클라우드 기본 비활성값과 `MATCH_JPA_DDL_AUTO=validate` 적용을 검증한다.
- 팀 공통 규약 초안(2026-10-31, 합의 전) 기준 HTTP 계약 검증: `FeignUserClientHttpTest`, `FeignFacilityClientHttpTest`에서 실제 Feign 구현과 JDK 내장 HTTP 스텁을 사용한다.
- 경로·반복 ID 쿼리·한글/선택 파라미터·JSON 매핑·404/500·응답 시간 초과·빈 입력의 요청 생략을 검증한다. 연결 1초/응답 1500ms는 테스트 전용 설정이다.
- 캐시에 의존성이 준비된 환경에서 실행: `gradle cleanTest test --offline` (H2와 `127.0.0.1` 임의 포트 사용, 외부 서비스 불필요).
- 옮기지 않은 테스트: `MatchIntegrationTest`(원본도 MariaDB 연결이 필요해 기본 실행에서 제외되던 테스트).
- 실제 유저·체육시설 서비스와 연결한 동작은 아직 확인하지 않았다(상대 API가 아직 없음).
- 정원·잠금 회귀 테스트 추가: `MatchCapacityControllerTest`(최소 정원 입력·전용 오류 응답), `MatchModifyIntegrationTest`(거절 후 DB 보존·마감/재개·참가와 축소 동시 요청). 동시성 검증 대상 DB는 H2이며 MariaDB 검증은 별도로 필요하다.
- Codex 샌드박스 실행 이력(2026-10-31): Gradle이 `~/.gradle/native/.../libnative-platform.dylib.lock` 쓰기 권한 오류로 빌드 시작 전에 중단돼 컴파일·테스트는 미실행이었다. 캐시·권한 우회 없이 종료했다.
- 후속 과제 구현 당시 Codex 샌드박스 실행 이력(2026-10-31): 강제 잠금 경합 3개, 회원탈퇴 정리 3개, 수정 응답의 시설 시간 초과·JSON 오류·연결 거부 3개 테스트를 추가하고 기존 프로필 정상/시설 500 사례의 실제 시설 요청 기록을 확인하도록 보강했다. 당시 `gradle test --offline`은 Gradle 네이티브 서비스의 `libnative-platform.dylib` 로딩 실패로 빌드 시작 전에 중단되어 **컴파일·테스트 미실행**이며, 권한·캐시 우회는 하지 않았다.
- Cloud 클라이언트 변경 전 실행 기록(Claude 실행 보고): 2026-10-31 20:1x KST, Claude가 샌드박스 밖에서 `gradle cleanTest test` 181개 통과 및 잠금 경합·탈퇴 테스트 10회 반복 통과를 확인했다. `modifyMatch`의 잠금 조회를 일반 조회로 바꾸면 경합 테스트가 실패하는 변이 확인 후 원복했다. 이는 예외 원인 단언 보강 전의 실행 기록이며, Codex가 원본 실행 로그를 재검증한 결과는 아니다.
- Cloud 클라이언트 변경 후 Codex 실행(2026-10-31): `gradle cleanTest test --offline --no-daemon`은 Gradle 네이티브 서비스의 `libnative-platform.dylib` 로딩 실패로 빌드 시작 전에 중단됐다. **기존·추가 테스트 모두 컴파일·실행 미확인**이다. 우회·네트워크 사용은 하지 않았으며, 네트워크 가능한 환경에서 새 의존성을 받은 뒤 전체 테스트를 실행해야 한다. 실제 Eureka 등록·Config 수신·컨테이너 종료 유예도 미검증이다.
- 같은 변경의 정적 확인: application.yml 3개 문서와 Compose YAML 파싱, `docker compose --env-file /dev/null -f compose.match-local.yml config --format json`의 기본값·전환값(두 Feign URL 빈 값, Cloud 활성화, prod/validate) 검증, `git diff --check`가 통과했다. Compose 검증은 가짜 비밀값으로 설정만 렌더링했으며 컨테이너 실행·네트워크 사용은 없었다.
- 회원 탈퇴 Kafka 수신(4a): `UserWithdrawnKafkaIntegrationTest`의 정상 정리·동일 ID 중복·잘못된 메시지 뒤 정상 처리·동시 PK 삽입 경합 검증을 유지한다. 정리 후 예외의 이력·업무 롤백 테스트는 **5회 연속 실패 후 6번째 성공, 처리 이력 1행**으로 보강하고, 재시도 소진 후 건너뛰던 테스트는 **장애 중 같은 파티션 후속 메시지 대기·복구 후 두 이벤트 처리**로 교체했다. 프로그래밍 오류 1회 후 즉시 건너뛰는 테스트도 추가했다. 모두 실제 그룹 오프셋을 확인한다. `UserWithdrawnKafkaStartupIntegrationTest`는 리스너가 꺼진 상태에서 발행을 완료한 뒤 UUID 기반 새 그룹으로 시작해 선발행 이벤트 처리와 오프셋 확정을 확인한다(`earliest` 테스트 덮어쓰기 없음). `MatchServiceApplicationTests`는 Kafka 없는 기본 설정의 리스너 정지를 검증한다.
- 4a Codex 샌드박스 실행(2026-10-31): `gradle cleanTest test --offline --no-daemon`은 Gradle 네이티브 서비스의 `libnative-platform.dylib` 로딩 실패로 빌드 시작 전에 중단됐다. **당시 기존·추가 테스트 모두 컴파일·실행 미확인**이었다. 우회·의존성 다운로드·네트워크 사용 없이 종료했다. 실제 유저 발행·Kafka 수신은 미검증이며, MariaDB의 READ_COMMITTED 동작은 후속 4b Claude 실행에서 확인했다.
- 4a 정적 확인: Ruby YAML 파서로 application.yml 3개 문서와 Kafka 기본값·자동 생성 차단·오프셋 설정을 확인했고, 캐시된 Spring Boot 3.5.4 메타데이터에서 관련 설정 키를 확인했다. Python YAML 파싱 시도는 PyYAML 미설치로 실행되지 않았다. `git diff --check`와 신규 파일 공백 점검도 통과했다. 이 정적 확인은 컴파일·런타임 검증을 대체하지 않는다.
- 4b 최종 실행(Claude 실행 보고, 2026-10-31): Docker MariaDB 11.4.8에서 **전체 211개 통과·skipped 0**, **MariaDB+Kafka 테스트 3회 반복 통과**. PK 컬럼 순서 단언 수정 후 결과다. **READ_COMMITTED 제거 변이 시 실제 격리 수준 단언 실패**를 확인했다. 기본 REPEATABLE READ의 CLOSED 오류 재현 테스트도 유지한다. 이 결과는 이번 Kafka 수신 보완 전의 Claude 실행 보고이며 Codex의 재실행 결과는 아니다.
- PR #7 Kafka 수신 보완 후(2026-10-31): **2026-10-31 Claude 실행 보고(샌드박스 밖, Docker MariaDB 11.4.8): `gradle cleanTest test` 213개 통과·skipped 0, `UserWithdrawnKafkaIntegrationTest`·`UserWithdrawnKafkaStartupIntegrationTest`·`MatchMariaDBIntegrationTest` 3회 반복 통과**. Codex의 `gradle test --offline --no-daemon`은 Gradle 네이티브 서비스의 `libnative-platform.dylib` 로딩 실패로 빌드 시작 전에 중단됐다. **컴파일·테스트 미실행**이며 캐시·권한 우회·네트워크 사용은 하지 않았다.
- 이번 수정의 정적 확인: application.yml 3개 문서의 `earliest`·`record`·자동 생성 차단·백오프 1000/30000ms, Compose Kafka 변수 3개의 기본값·전환값 렌더링, `git diff --check`를 확인했다. 가짜 비밀값과 `--env-file /dev/null`로 설정만 렌더링했으며 컨테이너 실행은 하지 않았다. 컴파일·런타임 검증을 대체하지 않는다.

- Cloud 클라이언트 변경 후 실행 기록(Claude의 샌드박스 밖 실행 보고): 2026-10-31 20:3x KST, 커밋 `055ad89` 기준 `gradle cleanTest test` 197개 통과, `docker build`·Compose 기동 및 `/actuator/health` 200을 확인했다. liveness·readiness는 로컬에서 404였으며 K8s에서 자동 활성화되거나 `management.endpoint.health.probes.enabled=true`로 활성화할 수 있다. `/actuator/env`·`/actuator/beans`는 401, Eureka·Config 오류 로그는 0건이며 graceful shutdown을 확인했다. 실제 Eureka 등록·Config 수신은 미검증이다. 이는 이번 Config 타임아웃 수정 전의 전달받은 실행 결과이며, Codex가 직접 재실행한 결과는 아니다.

## 임의로 정한 것 (팀 확인 필요)

1. 폴더 구조: `services/<서비스명>` 아래 서비스별 독립 Gradle 프로젝트
2. 패키지 구조: 원본과 같게 유지(`com.example.sporty.features.exerciseMatching`) — 원본과 비교하기 쉽게
3. 포트 8082, 유저 서비스 8081, 체육시설 서비스 8083 가정
4. 인증: Gateway 방식이 정해지기 전까지 매치 서비스도 Access Token을 직접 검증(원본 `JwtAuthenticationFilter` 그대로). Gateway가 사용자 ID 헤더를 넘기기로 정하면 필터만 바꾸면 된다.
5. 아래 내부 API 경로와 응답 형식

## 다른 서비스에 필요한 내부 API (제안안)

경로·형식은 매치 서비스 쪽 제안이며, 각 담당자와 확정해야 한다.

**유저 서비스**

| 용도 | 요청 | 응답 |
|---|---|---|
| 회원 존재·상태 확인 (생성·참가·내 매치) | `GET /internal/users/{userId}` | `{"userId":1,"status":"ACTIVE"}`, 없으면 404 |
| 상세 화면 참가자 프로필 | `GET /internal/users/profiles?userIds=1&userIds=2` | `[{"userId":1,"profileId":10,"nickname":"..","imageUrl":".."}]` |

**체육시설 서비스**

| 용도 | 요청 | 응답 |
|---|---|---|
| 목록·상세·내 매치에 시설 정보 표시, 생성 시 시설 확인 | `GET /internal/facility-services?ids=1&ids=2` | `[{"serviceId":1,"serviceName":"..","locationName":"..","region":"강남구","isFree":true,"reservationDeadlineAt":"2026-11-01T18:00:00"}]` |
| 지역·무료 여부 검색 | `GET /internal/facility-services/ids?region=강남&isFree=true` | `[1,2,3]` (region은 대소문자 무시 부분 일치) |

## 남은 일

- **회원 탈퇴 연동:** `UserWithdrawn` 구독·처리 이력·무제한 재시도 경계를 추가했다(기본 비활성). 유저 서비스 발행·토픽 준비 후 실제 연동과 이번 Kafka 수신 보완 후 회귀 테스트 실행이 필요하다. 수정 전 Claude 실행 결과는 위 4b 검증 상태에 기록했다.
- **AI 서비스 연동:** 원본 AI는 `MatchService.searchMatches`와 참가 여부 조회를 직접 썼다. AI 서비스가 `GET /api/matches`를 쓰고, 추천의 "이미 참가한 매치 제외"는 별도 조건이 필요하다.
- **참가 시 원격 호출 위치:** `joinMatch`는 원본 순서를 지키느라 매치 행 잠금을 잡은 뒤 유저 서비스를 호출한다. 잠금 시간이 길어지지 않게 회원 확인을 잠금 전으로 옮기는 것을 검토한다(에러 우선순위가 바뀌므로 팀 확인 필요).
- **다른 서비스 장애 시 처리:** 매치 서비스는 `503 / EXTERNAL_SERVICE_UNAVAILABLE` 매핑과 운영 Feign 연결 1000ms·응답 3000ms 적용을 완료했다. 팀 공통 규약은 아직 합의 전이며, Circuit Breaker·Fallback(선택 요구사항 5번)도 검토한다.
- **수정·참가·삭제 경합 테스트 — 완료:** `MatchLockContentionIntegrationTest`에서 A의 실제 행 잠금 획득 후 B의 조회 반환·서비스 완료가 400ms 동안 대기하고, A 커밋 후 일관된 결과가 저장되는지 단언한다(테스트 H2 `LOCK_TIMEOUT=10000`).
- **회원 탈퇴 정리의 최신 조회 — 완료:** 엔티티 대신 매치 ID만 오름차순으로 조회한 뒤 잠금 후 읽은 매치·참가 정보로 정리하며, OWNER 삭제·본인만 참가 취소·마감 재개·시작 후 마감 유지와 ID 조회 사이에 커밋된 정원 수정 보존 테스트를 추가했다. 4a에서는 호출 경계의 영속성 컨텍스트 공유를 해결했으며, 4b에서는 ID 수집 후 커밋된 참가자·정원 변경 반영을 실제 MariaDB로 확인했다(Claude 실행, 2026-10-31).
- **수정 응답 장애 테스트 — 완료:** 실제 Feign의 시설 시간 초과·JSON 오류·연결 거부에서도 수정 200·DB 반영·시설 필드 null을 단언하고, 프로필 정상/시설 500은 시설 요청 기록까지 확인한다. 이번 보강으로 시간 초과·JSON 오류의 실제 예외 원인도 단언하며, 보강분 실행 검증은 별도로 필요하다.
- **Eureka·Config 실제 연동 및 Gateway:** 클라이언트 연결 준비는 추가했으며 기본값은 비활성이다. 공통 설정 담당(이우진)의 서버가 준비되면 위 환경 변수로 활성화하고 실제 등록·설정 수신을 검증한다. Gateway 연동은 별도 후속 작업이다.
- **전체 Compose 연동:** 매치 Dockerfile·매치 단독 로컬 Compose는 추가했다. 전체 서비스 Compose 통합은 인프라 담당과 진행한다.
- **ERD 초안의 포인트·SAGA 컬럼과 새 상태값:** 팀 규칙 확정 후 3단계에서 반영.

## 탈퇴 연동 전 반드시 해결 — 두 항목 해결

- **(1) 구조적 해결 — 완료:** 탈퇴 처리는 Kafka 리스너가 `UserWithdrawnHandler.handle`을 통해 여는 새 `REQUIRES_NEW` 트랜잭션에서만 호출한다. 기존 `Match`가 적재된 영속성 컨텍스트를 공유하지 않으며, 운영에서 `removeMatchesForWithdrawal`은 이 처리 경계 밖에서 호출하지 않는다.
- **(2) READ_COMMITTED 지정·MariaDB 검증 — 완료(Claude 실행, 2026-10-31):** 처리 경계에 `Isolation.READ_COMMITTED`를 명시했다. 실제 MariaDB 11.4.8에서 ID 수집 후 A 탈퇴·정원 3 수정이 커밋되는 시나리오의 기본 REPEATABLE READ 오류(최종 2명/정원 3/CLOSED)와 READ_COMMITTED 정상 동작(최종 2명/정원 3/RECRUITING)을 모두 확인했다. 오류 재현 테스트는 유지한다. 일반적인 동시 참가·정원 변경 전체의 MariaDB 검증까지 완료한 것으로 해석하지 않는다.

## 알려진 한계

- 유저 서비스에서 ACTIVE 상태를 확인한 뒤 저장하기 전 짧은 순간에 탈퇴 정리가 끝나면, 진행 중이던 생성·참가 기록이 정리 대상 ID 수집 이후 저장되어 남을 수 있다. 같은 이벤트를 재전달해도 `processed_event`의 중복 처리로 생략된다. **매치 DB에 탈퇴 사용자 기록을 남기고 생성·참가·탈퇴를 사용자 단위 잠금으로 직렬화하는 해결은 후속 과제**이며 이번에는 구현하지 않았다.
- 일시 오류의 무제한 재시도 동안 같은 파티션의 후속 메시지도 대기한다. 재시도 불가 오류는 ERROR 로그 후 건너뛰며 별도 DLT·실패 이벤트 보관은 없으므로 Kafka 담당(이우진)과 협의가 필요하다.
- 재시도하지 않는 예외 분류(`JsonProcessingException`, `IllegalArgumentException`, `NullPointerException`, `ClassCastException`)는 원인 체인까지 보므로, 드물게 일시 오류가 이 예외를 원인으로 가지면 재시도 없이 건너뛸 수 있다.
- `processed_event`의 실제 PK 컬럼 순서는 `(event_id, consumer_name)`으로 ERD의 `(consumer_name, event_id)`와 다르다. 조회는 항상 전체 키를 사용하므로 동작 영향은 없다. 이 차이는 제출용 테이블 명세서에 기록해야 한다.
