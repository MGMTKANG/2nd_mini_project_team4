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

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `MATCH_DB_URL` | `jdbc:mariadb://localhost:3306/match_db` | 매치 전용 DB |
| `MATCH_DB_USERNAME` / `MATCH_DB_PASSWORD` | `match` / 빈 값 | |
| `JWT_SECRET_KEY` | 없음(필수) | 유저 서비스와 같은 값 |
| `USER_SERVICE_URL` | `http://localhost:8081` | Eureka 연동 전 임시 주소 |
| `FACILITY_SERVICE_URL` | `http://localhost:8083` | Eureka 연동 전 임시 주소 |

테스트: `./gradlew test` (H2 사용, 다른 서비스 없이 실행됨)

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
- 매치 수정 후 응답 조합 중 원격 장애가 나면 같은 트랜잭션 경계 안에서 처리해 수정을 커밋하고 `200`을 반환한다. 이때 시설 조회 필드(`serviceName`, `locationName`, `region`)와 참가자 프로필 필드(`profileId`, `nickname`, `imageUrl`)는 `null`이며 시설 ID·참가 인원·역할·권한은 유지한다. 생성·참가는 저장 전 원격 확인에 실패하면 `503`이며 매치·참가자를 저장하지 않는다.
- 회원 탈퇴 정리 메서드 `removeMatchesForWithdrawal`은 신규 서비스 내 호출자가 없어 아직 연동하지 않았다.

## 검증

- 원본 매치 테스트 11개 파일(101개)을 옮겨 모두 통과. 회원·시설 데이터는 메모리 가짜 클라이언트로 대체했다.
- 전체 설정(Feign 클라이언트 포함)이 뜨는지 확인하는 부팅 테스트 1개 추가.
- 팀 공통 규약 초안(2026-10-31, 합의 전) 기준 HTTP 계약 검증: `FeignUserClientHttpTest`, `FeignFacilityClientHttpTest`에서 실제 Feign 구현과 JDK 내장 HTTP 스텁을 사용한다.
- 경로·반복 ID 쿼리·한글/선택 파라미터·JSON 매핑·404/500·응답 시간 초과·빈 입력의 요청 생략을 검증한다. 연결 1초/응답 1500ms는 테스트 전용 설정이다.
- 실행: `gradle cleanTest test --offline` (H2와 `127.0.0.1` 임의 포트 사용, 외부 서비스·추가 라이브러리 불필요).
- 옮기지 않은 테스트: `MatchIntegrationTest`(원본도 MariaDB 연결이 필요해 기본 실행에서 제외되던 테스트).
- 실제 유저·체육시설 서비스와 연결한 동작은 아직 확인하지 않았다(상대 API가 아직 없음).
- 정원·잠금 회귀 테스트 추가: `MatchCapacityControllerTest`(최소 정원 입력·전용 오류 응답), `MatchModifyIntegrationTest`(거절 후 DB 보존·마감/재개·참가와 축소 동시 요청). 동시성 검증 대상 DB는 H2이며 MariaDB 검증은 별도로 필요하다.
- 이번 수정의 컴파일·테스트는 미실행(2026-10-31): `gradle test --offline --tests 'com.example.sporty.features.exerciseMatching.controller.*' --tests 'com.example.sporty.features.exerciseMatching.service.*'`가 `~/.gradle/native/.../libnative-platform.dylib.lock` 쓰기 권한 오류로 빌드 시작 전에 중단됐다. 캐시·권한 우회 없이 종료했으며 실행 가능한 환경에서 재검증해야 한다.

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
- **다른 서비스 장애 시 처리:** 오류 매핑·운영 Feign 타임아웃을 정의하고 최종 HTTP 응답을 검증해야 한다. Circuit Breaker·Fallback(선택 요구사항 5번)도 검토한다.
- **Eureka·Config·Gateway 연동:** 공통 설정 담당(이우진)이 준비되면 `url` 대신 서비스 이름으로 찾도록 바꾼다.
- **Dockerfile:** 인프라 담당과 형식을 맞춘 뒤 추가.
- **ERD 초안의 포인트·SAGA 컬럼과 새 상태값:** 팀 규칙 확정 후 3단계에서 반영.
