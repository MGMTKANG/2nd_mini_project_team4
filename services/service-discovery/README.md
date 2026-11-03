# service-discovery (Eureka Server)

서비스 인스턴스 등록/조회를 담당하는 Eureka 서버입니다. 자기 자신은 등록하지 않습니다.

| 항목 | 값 |
  |---|---|
| 포트 | `8761` (`SERVER_PORT`로 변경 가능) |
| 대시보드 | http://localhost:8761 |
| Health | http://localhost:8761/actuator/health |

## 실행

  ```bash
  cd services/service-discovery
  ./gradlew bootRun
  ```

## 클라이언트(각 서비스) 설정

Eureka 등록은 **기본 꺼짐**입니다. 사용하려면 각 서비스에 환경변수를 지정합니다.

| 환경변수 | 기본값 | 설명 |
  |---|---|---|
| `EUREKA_ENABLED` | `false` | `true`면 Eureka에 등록/조회 |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | K8s 예: `http://service-discovery:8761/eureka/` |

## 등록 확인

  ```bash
  curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | grep -o '"app":"[^"]*"'
  ```

등록 후 반영까지 최대 30초 정도 걸릴 수 있습니다.