# api-gateway (Spring Cloud Gateway)

프론트엔드 요청의 단일 진입점입니다. 외부 공개 경로(`/api/**`)만 각 서비스로 라우팅합니다.

| 항목 | 값 |
  |---|---|
| 포트 | `8000` (`SERVER_PORT`로 변경 가능) |
| Health | http://localhost:8000/actuator/health |

## 라우팅

| 경로 | 대상 | URI 환경변수 (기본값) |
  |---|---|---|
| `/api/auth/**`, `/api/users/**`, `/api/profiles/**` | user-service | `USER_SERVICE_URL` (`http://localhost:8081`) |
| `/api/matches/**` | match-service | `MATCH_SERVICE_URL` (`http://localhost:8082`) |
| `/api/services/**` | facility-service | `FACILITY_SERVICE_URL` (`http://localhost:8083`) |
| `/api/ai/**` | ai-service | `AI_SERVICE_URL` (`http://localhost:8084`) |

- `/internal/**`은 **라우팅하지 않습니다** (서비스 간 내부 호출 전용).
- URI 형식은 환경에 따라 다릅니다.
    - 로컬: 기본값 사용
    - K8s Service DNS: `http://match-service:8082`
    - Eureka 사용: `lb://match-service` (+ `EUREKA_ENABLED=true`)
- 한 환경 안에서는 방식을 통일하세요. `lb://`인데 대상 서비스가 Eureka에 등록되지 않았으면 `503`이 납니다.

## 인증 / CORS

- **JWT**: gateway는 `Authorization` 헤더를 그대로 전달하고, **검증은 각 서비스**가 합니다.
- **CORS**: 각 서비스 설정을 사용합니다. gateway에서는 추가하지 않습니다 (헤더 중복 방지).

## 실행

  ```bash
  cd services/api-gateway

  # 기본: 각 서비스에 직접 연결
  ./gradlew bootRun

  # Eureka + Config 사용
  EUREKA_ENABLED=true CONFIG_ENABLED=true ./gradlew bootRun
  ```

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