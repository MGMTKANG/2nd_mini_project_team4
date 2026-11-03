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
