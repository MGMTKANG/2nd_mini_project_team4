# api-gateway (Spring Cloud Gateway)

프론트엔드 요청의 단일 진입점. 외부 공개 경로(`/api/**`)만 각 서비스로 라우팅.

| 항목 | 값 |
|---|---|
| 포트 | `8000` (`SERVER_PORT`로 변경 가능. 변경 시 아래 URL·명령의 포트도 함께 변경) |
| Health | http://localhost:8000/actuator/health |

## 라우팅

| 경로 | 대상 | URI 환경변수 (기본값) |
|---|---|---|
| `/api/auth/**`, `/api/users/**`, `/api/profiles/**` | user-service | `USER_SERVICE_URL` (`http://localhost:8081`) |
| `/api/matches/**` | match-service | `MATCH_SERVICE_URL` (`http://localhost:8082`) |
| `/api/services/**` | facility-service | `FACILITY_SERVICE_URL` (`http://localhost:8083`) |
| `/api/ai/**` | ai-service | `AI_SERVICE_URL` (`http://localhost:8084`) |

- `/internal/**`은 **라우팅 대상에서 제외** (서비스 간 내부 호출 전용).
- 환경별 URI 형식
  - 로컬: 기본값 사용
  - K8s Service DNS: `http://match-service:8082`
  - Eureka 사용: `lb://match-service` (+ `EUREKA_ENABLED=true`)
- 한 환경 안에서는 방식 통일 필요. `lb://` 설정인데 대상 서비스가 Eureka에 미등록이면 `503` 발생.

## 인증 / CORS

- **JWT**: gateway는 `Authorization` 헤더를 그대로 전달, **검증은 각 서비스에서 수행**.
- **CORS**: 각 서비스 설정 사용. gateway에서는 미설정 (헤더 중복 방지).

## 실행

```bash
cd services/api-gateway

# 기본: 각 서비스에 직접 연결 (localhost:808x)
./gradlew bootRun

# Eureka 등록 + Config 사용 (라우팅은 여전히 localhost:808x로 직접 연결)
EUREKA_ENABLED=true CONFIG_ENABLED=true ./gradlew bootRun

# Eureka로 라우팅 (대상 서비스들도 EUREKA_ENABLED=true로 등록되어 있어야 함)
EUREKA_ENABLED=true \
USER_SERVICE_URL=lb://user-service \
MATCH_SERVICE_URL=lb://match-service \
FACILITY_SERVICE_URL=lb://facility-service \
AI_SERVICE_URL=lb://ai-service \
./gradlew bootRun
```

> `EUREKA_ENABLED`는 Eureka 등록/조회에만 관여. 라우팅 방식은 `*_SERVICE_URL` 값(`http://...` 또는 `lb://...`)으로 결정.

## 인프라 전체 로컬 실행 순서

`service-discovery`(8761) → `config-service`(8888) → `api-gateway`(8000) → 각 서비스

## 라우팅 확인 (대상 서비스 미기동 상태)

```bash
for p in /actuator/health /api/users/me /api/matches /api/services /api/ai/matches /internal/users/1; do
  printf "%-22s -> " "$p"; curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8000$p"
done
```

| 결과 | 의미 |
|---|---|
| `200` | gateway 정상 (`/actuator/health`) |
| `500` + 로그에 `Connection refused ...:808x` | 라우팅 성공, 대상 서비스 미기동 |
| `404` | 라우팅 대상 아님 (`/internal/**` 등) |
