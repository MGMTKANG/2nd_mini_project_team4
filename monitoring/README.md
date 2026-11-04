# monitoring (Prometheus + Grafana, 로컬용)

각 서비스의 `/actuator/prometheus` 지표를 Prometheus로 수집하고 Grafana로 시각화하는 로컬 모니터링 환경.

| 구성 | 주소 | 비고 |
|---|---|---|
| Prometheus | http://localhost:9090 | 수집 대상 상태: http://localhost:9090/targets |
| Grafana | http://localhost:3001 | 초기 계정 `admin` / `admin` (로컬 전용). 3000은 프론트엔드와 충돌하므로 3001 사용 |

## 구성 파일

| 파일 | 역할 |
|---|---|
| `compose.yml` | Prometheus, Grafana 컨테이너 정의 |
| `prometheus/prometheus.yml` | 수집 대상(`host.docker.internal:808x` 등) 정의 |
| `grafana/provisioning/datasources/datasource.yml` | Grafana 기동 시 Prometheus 데이터소스 자동 등록 (uid `prometheus`) |
| `grafana/provisioning/dashboards/dashboards.yml` | 같은 폴더의 대시보드 JSON 자동 등록 (Sporty 폴더) |
| `grafana/provisioning/dashboards/sporty-gateway.json` | `Sporty - API Gateway` 대시보드 정의 |

## 실행

사전 조건: Docker Desktop 실행 상태.

```bash
# 저장소 루트에서
docker compose -f monitoring/compose.yml up -d
docker compose -f monitoring/compose.yml ps

# 종료
docker compose -f monitoring/compose.yml down
```

## 수집 대상

| 대상 | 포트 | 상태 |
|---|---|---|
| service-discovery | 8761 | 적용 완료 |
| config-service | 8888 | 적용 완료 |
| api-gateway | 8000 | 적용 완료 |
| user-service | 8081 | 서비스별 적용 필요 (아래 참고) |
| match-service | 8082 | 서비스별 적용 필요 |
| facility-service | 8083 | 서비스별 적용 필요 |
| ai-service | 8084 | 서비스별 적용 필요 |

- Prometheus는 컨테이너 안에서 동작하므로 PC에서 실행 중인 서비스는 `localhost` 대신 `host.docker.internal`로 접근.
- 서비스가 꺼져 있거나 Prometheus 미적용 상태면 targets 화면에서 `DOWN`으로 표시 (정상).

## Grafana 대시보드

http://localhost:3001 접속 후 `admin` / `admin` 로그인 (비밀번호 변경 화면은 Skip 가능).

### Sporty - API Gateway (자동 등록)

**Dashboards → Sporty → Sporty - API Gateway**. 모든 API 요청이 gateway(8000)를 거치므로, 서비스별 Prometheus 적용 여부와 무관하게 서비스(routeId)별 트래픽 확인 가능.

| 위치 | 패널 | 확인 내용 |
|---|---|---|
| 1행 | 전체 처리량 / 5xx 에러율 / p95 응답시간 | 현재 상태 요약 |
| 2행 | 라우트별 처리량, 상태코드별 처리량 | 부하 시 트래픽 증가, 장애 시 2xx → 5xx 전환 |
| 3행 | 라우트별 p95 응답시간, 라우트별 5xx 에러율 | 부하 시 지연 상승, 장애 서비스 식별 |
| 4행 | gateway CPU, Heap | 부하 중 gateway 자원 사용량 |

- 지표 출처: gateway의 `spring_cloud_gateway_requests_seconds_*` (태그 `routeId`, `httpStatusCode`, `outcome`)
- p95 패널은 gateway `application.yml`의 `management.metrics.distribution.percentiles-histogram.spring.cloud.gateway.requests: true` 필요 (`_bucket` 지표 생성)
- 요청이 없는 구간은 에러율·p95가 `No data`로 표시 (정상)
- 화면에서 수정 후 저장 불가 (`allowUiUpdates: false`). 변경은 JSON 파일 수정 후 `docker compose -f monitoring/compose.yml restart grafana`

### JVM 대시보드 (수동 Import)

1. **Dashboards → New → Import** 에서 ID `4701` (JVM Micrometer) 입력 후 **Load**
2. 데이터소스로 `Prometheus` 선택 후 **Import**
3. 상단 `application` 선택 상자에서 서비스별 지표 확인

수동 Import한 대시보드는 컨테이너를 내리면 초기화됨 (`down` 후 재실행 시 다시 Import 필요).

## 서비스에 Prometheus 적용하기

**① `build.gradle`** (`spring-boot-starter-actuator` 아래)

```gradle
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

**② `src/main/resources/application.yml`**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

`application` 태그가 있어야 Grafana에서 서비스별 구분 가능.

**③ Spring Security 사용 서비스**

`/actuator/prometheus`가 인증 대상이면 Prometheus 수집 시 `401` 발생. `PublicEndpoints` 등에 `GET /actuator/prometheus` 허용 추가 필요.

**확인**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:808x/actuator/prometheus   # 200이면 정상
```

| 증상 | 원인 |
|---|---|
| `404` | Prometheus 의존성 미반영. IntelliJ 실행 시 `build.gradle` 수정 후 **Gradle 다시 불러오기**(`Ctrl+Shift+O`) 필요 |
| `401` | 보안 설정에서 `/actuator/prometheus` 미허용 |

## 참고

- 로컬 개발용 구성. K8s 환경의 수집 방식(Prometheus/Grafana 설치, 수집 대상 지정)은 DevOps와 별도 협의.
- api-gateway의 `/actuator/prometheus`는 외부 진입 포트(8000)에서 열리므로, 배포 환경에서는 Ingress가 `/api/**`만 gateway로 전달하도록 구성 필요.
