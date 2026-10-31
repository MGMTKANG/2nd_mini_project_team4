# match-service

운동 매칭 서비스. 모놀리식 `backend/sporty`의 `exerciseMatching` 코드를 떼어 낸 독립 Spring Boot 앱이다.
1단계는 **기능을 바꾸지 않는 분리**만 했다. 매치 상태는 기존과 같은 `RECRUITING/CLOSED`이다.

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
| 디버그용 `System.out.println` | 삭제 |

매치 생성·조회·수정·삭제, 참가·탈퇴, 정원 마감/재개, 비관적 잠금, 에러 코드와 HTTP 상태는 원본과 같다.

## 검증

- 원본 매치 테스트 11개 파일(101개)을 옮겨 모두 통과. 회원·시설 데이터는 메모리 가짜 클라이언트로 대체했다.
- 전체 설정(Feign 클라이언트 포함)이 뜨는지 확인하는 부팅 테스트 1개 추가.
- 팀 공통 규약 초안(2026-10-31, 합의 전) 기준 HTTP 계약 검증: `FeignUserClientHttpTest`, `FeignFacilityClientHttpTest`에서 실제 Feign 구현과 JDK 내장 HTTP 스텁을 사용한다.
- 경로·반복 ID 쿼리·한글/선택 파라미터·JSON 매핑·404/500·응답 시간 초과·빈 입력의 요청 생략을 검증한다. 연결 1초/응답 500ms는 테스트 전용 설정이다.
- 실행: `gradle cleanTest test --offline` (H2와 `127.0.0.1` 임의 포트 사용, 외부 서비스·추가 라이브러리 불필요).
- 옮기지 않은 테스트: `MatchIntegrationTest`(원본도 MariaDB 연결이 필요해 기본 실행에서 제외되던 테스트).
- 실제 유저·체육시설 서비스와 연결한 동작은 아직 확인하지 않았다(상대 API가 아직 없음).

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
- **다른 서비스 장애 시 처리:** 지금은 유저·체육시설 서비스가 응답하지 않으면 500이 된다. Circuit Breaker·Fallback(선택 요구사항 5번)으로 보완 가능.
- **Eureka·Config·Gateway 연동:** 공통 설정 담당(이우진)이 준비되면 `url` 대신 서비스 이름으로 찾도록 바꾼다.
- **Dockerfile:** 인프라 담당과 형식을 맞춘 뒤 추가.
- **ERD 초안의 포인트·SAGA 컬럼과 새 상태값:** 팀 규칙 확정 후 3단계에서 반영.
