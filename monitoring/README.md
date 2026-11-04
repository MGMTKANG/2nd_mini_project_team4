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
| `k6/matches.js` | k6 부하 테스트 스크립트 (스모크 / 부하 프로필) |

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

### 동작 확인 (정상 → 장애 → 복구)

match-service의 공개 API `GET /api/matches`를 gateway 경유로 반복 호출하고, match-service 중지·재시작 시 지표 변화 확인. 모든 터미널은 저장소 루트에서 시작.

```bash
# 1) 모니터링 (터미널 1)
docker compose -f monitoring/compose.yml up -d

# 2) gateway (터미널 2)
cd services/api-gateway && ./gradlew bootRun

# 3) match-service + MariaDB (터미널 1). 최초 1회 .env 복사 (가짜 비밀값, git 제외 대상)
cd services/match-service && cp -n .env.example .env
docker compose -f compose.match-local.yml up -d --build

# 4) 준비 확인 (터미널 1, 기동 후 1~2분)
curl -s -o /dev/null -w "%{http_code}\n" localhost:8000/api/matches   # 200이면 준비 완료

# 5) 트래픽 발생 (터미널 3, 확인 중 계속 실행 / 종료는 Ctrl+C)
while true; do curl -s -o /dev/null -w "%{http_code} " localhost:8000/api/matches; sleep 0.5; done

# 6) 장애 → 복구 (터미널 1, services/match-service 위치)
docker compose -f compose.match-local.yml stop match-service
docker compose -f compose.match-local.yml start match-service    # 장애 화면 확인 후 실행
```

| 단계 | 대시보드 변화 (반영까지 1~2분) |
|---|---|
| 정상 | 상태코드별 처리량에 `200`(초록), 5xx 에러율 0% |
| 장애 (`stop`) | `200` 감소·`500`(빨강) 증가, 5xx 에러율 빨간 배경, 라우트별 5xx 에러율 `match-service` 100% |
| 복구 (`start`) | 기동 완료(30초~1분) 후 `200`으로 복귀, 에러율 0%로 하락 |

- 발표용 캡처는 우측 상단 시간 범위를 정상·장애·복구 구간 전체가 들어가도록 지정
- 정리: `docker compose -f compose.match-local.yml down -v` (match 로컬 DB 볼륨 포함 삭제), `docker compose -f monitoring/compose.yml down`

### JVM 대시보드 (수동 Import)

1. **Dashboards → New → Import** 에서 ID `4701` (JVM Micrometer) 입력 후 **Load**
2. 데이터소스로 `Prometheus` 선택 후 **Import**
3. 상단 `application` 선택 상자에서 서비스별 지표 확인

수동 Import한 대시보드는 컨테이너를 내리면 초기화됨 (`down` 후 재실행 시 다시 Import 필요).

## 부하 테스트 (k6)

`k6/matches.js`로 gateway 경유 `GET /api/matches`(공개 API)에 부하를 주고 대시보드 지표 변화 확인. k6 설치 없이 Docker 이미지로 실행.

사전 조건: 위 "동작 확인" 1~4단계 완료 (모니터링, gateway, match-service 실행 및 `200` 확인).

| TC | 시나리오 | 부하 | 합격 기준 | 대시보드 확인 |
|---|---|---|---|---|
| K-1 | 스모크 | 1 VU · 30초 | 실패율 0%, p95 < 500ms, `checks` 100% | 기준선 처리량·응답시간 |
| K-2 | 부하 | 0 → 20 → 50 VU 단계 증가 · 약 3분 20초 | 실패율 < 1%, p95 < 1s, `checks` > 99% | 처리량 단계별 증가, p95 변화, gateway CPU·Heap |
| K-3 | 부하 중 장애 | K-2 실행 중 match-service 중지 → 재시작 | 기준 미달(의도된 실패) 후 회복 | 부하 상태에서 5xx 에러율 급증 → 복구 |

```bash
# 저장소 루트에서. 스크립트는 표준입력(<)으로 전달 (Windows 경로 마운트 문제 회피)

# K-1 스모크
docker run --rm -i grafana/k6:1.0.0 run - < monitoring/k6/matches.js

# K-2 부하
docker run --rm -i -e PROFILE=load grafana/k6:1.0.0 run - < monitoring/k6/matches.js

# K-3 부하 중 장애: K-2 실행 후 약 1분 시점에 다른 터미널(services/match-service 위치)에서
docker compose -f compose.match-local.yml stop match-service
docker compose -f compose.match-local.yml start match-service    # 30초 정도 뒤 실행
```

- VU 1명당 초당 약 1회 요청 (`sleep(1)`) → 50 VU 구간은 약 50 RPS
- 대상 주소 기본값 `http://host.docker.internal:8000` (컨테이너 안의 `localhost`는 내 PC가 아님). 변경 시 `-e BASE_URL=...`
- 결과 요약의 `http_req_failed`, `http_req_duration`의 `p(95)`, `checks`(상태 `200` 비율) 확인. 요청 timeout 5초 (응답 없는 요청도 실패로 기록). 기준 통과 시 `✓`, 미달 시 `✗` 표시 및 종료 코드 99 (K-3은 미달이 정상)
- 발표용 캡처는 대시보드 시간 범위를 테스트 구간 전체로 지정

## 문제 해결

| 증상 | 원인 / 조치 |
|---|---|
| `up` 시 `ports are not available ... bind: An attempt was made to access a socket in a way forbidden by its access permissions` | Windows(WinNAT)가 해당 포트(9090 등)를 예약 범위로 점유. `netsh interface ipv4 show excludedportrange protocol=tcp`로 확인 후 **관리자 PowerShell**에서 `net stop winnat` → `net start winnat`, 이후 다시 `up -d` |
| `failed to connect to the docker API at npipe:...` | Docker Desktop 미실행. 실행 후 재시도 |
| 대시보드 패널에 데이터소스 오류 또는 전체 `No data` | datasource 설정(uid) 변경이 기존 컨테이너에 미반영. `restart`가 아닌 `down` 후 `up -d` |
| Prometheus targets에서 `api-gateway`(8000) `DOWN` | gateway 미실행 상태 |
| p95 패널만 `No data` | gateway 재시작 전이라 histogram 미반영. `curl -s localhost:8000/actuator/prometheus \| grep -c '^spring_cloud_gateway_requests_seconds_bucket'` 결과가 0이면 gateway 재시작 |
| gateway 경유 `/api/matches`가 `500` | match-service 미기동 또는 기동 중. `curl localhost:8082/actuator/health`로 직접 확인 |
| k6 결과 `http_req_failed` 100%, 에러 `connection refused` | gateway 미실행, 또는 `BASE_URL`을 `localhost`로 지정 (컨테이너 기준이라 `host.docker.internal` 필요) |

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
