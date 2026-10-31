# match-service

운동 매칭 서비스. 모놀리식 `backend/sporty`의 `exerciseMatching` 코드를 떼어 낸 독립 Spring Boot 앱이다.
1단계는 원본 매치 테스트 101개로 확인한 범위에서 도메인 로직을 유지하며 분리했다. 매치 상태는 기존과 같은 `RECRUITING/CLOSED`이다.

- 담당: 김승진
- 소유 DB: `match_db` (`match`, `match_participant`)
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
| `MATCH_JPA_DDL_AUTO` | `update` | Hibernate 스키마 처리. 운영 권장값은 `validate` |
| `SPRING_PROFILES_ACTIVE` | 미지정 시 `local` | `local` / `dev` / `prod`. Compose 기본값도 `local` |

기본값은 Eureka·Config 없이 단독 실행한다. 기존 `.env` import는 유지한다. `SPRING_PROFILES_ACTIVE=dev` 또는 `prod`에서는 `show-sql=false`이며, 프로필만 바꿔도 Eureka·Config가 켜지지 않는다. Config 서버가 준비되면 `CONFIG_ENABLED=true CONFIG_SERVER_URL=http://<설정서버>:8888`로 활성화하고 재시작한다. 설정 조회의 서비스 이름은 `match-service`이다.

Eureka 전환 방법:

1. 서버와 상대 서비스 등록이 준비되면 `EUREKA_ENABLED=true EUREKA_URL=http://<유레카서버>:8761/eureka/`로 설정한다.
2. `USER_SERVICE_URL='' FACILITY_SERVICE_URL=''`로 명시적으로 비우면 `@FeignClient`의 `name`(`user-service`, `facility-service`)을 Spring Cloud LoadBalancer가 찾아 호출한다. 변수를 생략하면 기존 직접 주소를 사용한다.
3. 앱을 재시작하고 `match-service` 등록과 상대 서비스 호출을 확인한다. Compose도 `.env`의 같은 변수를 전달하며, 주소는 컨테이너에서 접근 가능한 값으로 설정한다.

운영 DB는 명시적인 스키마 마이그레이션을 먼저 적용한 뒤 `MATCH_JPA_DDL_AUTO=validate`로 실행하는 것을 권장한다. `validate`는 스키마를 생성·변경하지 않으므로 마이그레이션과 DB별 사전 검증이 필요하다. 기본 `update`는 현재 로컬 동작을 유지한다.

Actuator 웹 노출은 `health,info`뿐이며 두 경로는 비로그인 접근을 허용한다. Compose 앱 healthcheck는 미설정이다. K8s에서는 `management.endpoint.health.probes.enabled=true`(환경 변수 `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`)로 활성화하고, liveness는 `/actuator/health/liveness`, readiness는 `/actuator/health/readiness`를 사용한다. 두 probe 경로도 비로그인 접근을 허용한다.

앱은 graceful shutdown과 종료 단계 제한 `20s`를 사용하며, Compose는 `stop_grace_period: 30s`로 종료를 기다린다.

테스트: `./gradlew test` (H2 사용, 다른 서비스 없이 실행됨. 새 의존성을 최초로 받으려면 네트워크가 가능한 빌드 환경 필요)

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
- 회원 탈퇴 정리 메서드 `removeMatchesForWithdrawal`은 신규 서비스 내 호출자가 없어 아직 연동하지 않았다.

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

- **회원 탈퇴 연동:** 원본은 `UserService`가 `removeMatchesForWithdrawal`을 같은 트랜잭션에서 호출했다. 지금은 호출하는 곳이 없다. Kafka 이벤트(예: `UserWithdrawn`) 구독 또는 내부 API로 연결해야 한다.
- **AI 서비스 연동:** 원본 AI는 `MatchService.searchMatches`와 참가 여부 조회를 직접 썼다. AI 서비스가 `GET /api/matches`를 쓰고, 추천의 "이미 참가한 매치 제외"는 별도 조건이 필요하다.
- **참가 시 원격 호출 위치:** `joinMatch`는 원본 순서를 지키느라 매치 행 잠금을 잡은 뒤 유저 서비스를 호출한다. 잠금 시간이 길어지지 않게 회원 확인을 잠금 전으로 옮기는 것을 검토한다(에러 우선순위가 바뀌므로 팀 확인 필요).
- **다른 서비스 장애 시 처리:** 매치 서비스는 `503 / EXTERNAL_SERVICE_UNAVAILABLE` 매핑과 운영 Feign 연결 1000ms·응답 3000ms 적용을 완료했다. 팀 공통 규약은 아직 합의 전이며, Circuit Breaker·Fallback(선택 요구사항 5번)도 검토한다.
- **수정·참가·삭제 경합 테스트 — 완료:** `MatchLockContentionIntegrationTest`에서 A의 실제 행 잠금 획득 후 B의 조회 반환·서비스 완료가 400ms 동안 대기하고, A 커밋 후 일관된 결과가 저장되는지 단언한다(테스트 H2 `LOCK_TIMEOUT=10000`).
- **회원 탈퇴 정리의 최신 조회 — 완료(현재 H2 테스트 범위):** 엔티티 대신 매치 ID만 오름차순으로 조회한 뒤 잠금 후 읽은 매치·참가 정보로 정리하며, OWNER 삭제·본인만 참가 취소·마감 재개·시작 후 마감 유지와 ID 조회 사이에 커밋된 정원 수정 보존 테스트를 추가했다. 아래 탈퇴 연동 전 해결 항목은 남아 있다.
- **수정 응답 장애 테스트 — 완료:** 실제 Feign의 시설 시간 초과·JSON 오류·연결 거부에서도 수정 200·DB 반영·시설 필드 null을 단언하고, 프로필 정상/시설 500은 시설 요청 기록까지 확인한다. 이번 보강으로 시간 초과·JSON 오류의 실제 예외 원인도 단언하며, 보강분 실행 검증은 별도로 필요하다.
- **Eureka·Config 실제 연동 및 Gateway:** 클라이언트 연결 준비는 추가했으며 기본값은 비활성이다. 공통 설정 담당(이우진)의 서버가 준비되면 위 환경 변수로 활성화하고 실제 등록·설정 수신을 검증한다. Gateway 연동은 별도 후속 작업이다.
- **전체 Compose 연동:** 매치 Dockerfile·매치 단독 로컬 Compose는 추가했다. 전체 서비스 Compose 통합은 인프라 담당과 진행한다.
- **ERD 초안의 포인트·SAGA 컬럼과 새 상태값:** 팀 규칙 확정 후 3단계에서 반영.

## 탈퇴 연동 전 반드시 해결

- `removeMatchesForWithdrawal`을 호출하는 트랜잭션에 이미 `Match`가 적재돼 있으면 영속성 컨텍스트 공유로 잠금 조회가 최신 값을 다시 읽지 않을 수 있다. 호출 경계 설계 시 해결해야 한다.
- MariaDB 기본 격리 수준(`REPEATABLE READ`)에서는 잠금 전 일반 조회(ID 수집)의 스냅샷 때문에 이후 인원 집계가 과거 값일 수 있다. H2로는 검증할 수 없으므로 MariaDB 통합 테스트로 확인해야 한다.
