# config-service (Spring Cloud Config Server)

저장소 루트의 `config-repo/`에 있는 설정 파일을 각 서비스에 제공.

| 항목 | 값 |
|---|---|
| 포트 | `8888` (`SERVER_PORT`로 변경 가능. 변경 시 아래 URL·명령의 포트도 함께 변경) |
| 기본 프로필 | `native` (로컬 파일 읽기) |
| Health | http://localhost:8888/actuator/health |

> ⚠️ public 저장소이므로 `config-repo/`에 비밀번호, API 키, JWT 키 등 **민감 정보 저장 금지**. 민감 정보는 환경변수나 K8s Secret으로 주입.

## 실행

```bash
cd services/config-service
./gradlew bootRun
```

IntelliJ 실행도 가능. 실행 위치가 모듈 디렉터리든 저장소 루트든 `config-repo/`를 찾도록 기본 경로 두 개 지정.

```yaml
search-locations: "${CONFIG_NATIVE_SEARCH_LOCATIONS:file:../../config-repo/,file:./config-repo/}"
```

## 프로필

| 프로필 | 설정 출처 | 용도 |
|---|---|---|
| `native` (기본) | 로컬 `config-repo/` | 로컬 개발 |
| `git` | GitHub 저장소의 `config-repo/` (브랜치: `CONFIG_GIT_LABEL`, 기본 `develop`) | 배포 환경 후보 |

배포 환경에서 `native` 사용 시, 실행 위치에 의존하지 않도록 **절대경로 지정**.

```bash
# 실행 명령 앞에 붙여야 해당 프로세스에 전달됨
CONFIG_NATIVE_SEARCH_LOCATIONS="file:/path/to/config-repo/" java -jar config-service-0.0.1-SNAPSHOT.jar
```

K8s에서는 Deployment의 `env`에 `CONFIG_NATIVE_SEARCH_LOCATIONS` 지정.

## 설정 파일 규칙

파일명은 `config-repo/{application}-{profile}.yml` 형식.

| 파일 | 적용 대상 |
|---|---|
| `application.yml` | 모든 서비스 공통 |
| `match-service-local.yml` | `match-service`의 `local` 프로필 |
| `api-gateway.yml` | `api-gateway` (Bus 동작 확인용 `info.config.message`, 기능과 무관) |

## 동작 확인

```bash
curl -s http://localhost:8888/match-service/local; echo
```

`propertySources`가 비어 있으면(`[]`) `config-repo/`를 찾지 못한 상태. 실행 위치 또는 `CONFIG_NATIVE_SEARCH_LOCATIONS` 확인 필요.

## 클라이언트(각 서비스) 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `CONFIG_ENABLED` | `false` | `true`면 Config Server에서 설정을 받아옴 |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Config Server 주소 |

## Spring Cloud Bus (설정 일괄 갱신)

`config-repo/` 변경 후 **요청 한 번으로 모든 클라이언트 인스턴스의 설정 갱신** (재시작 불필요). 브로커는 RabbitMQ (`rabbitmq/README.md`).

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `BUS_ENABLED` | `false` | `true`면 Bus 활성화 + `/actuator/busrefresh` 노출 (config-service) |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | 브로커 주소 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | 로컬 기본 계정. 배포 환경은 환경변수로 주입 |

- `BUS_ENABLED=false`면 RabbitMQ 없이 기동, health `UP` 유지 (rabbit health 체크도 비활성)
- 현재 Bus 클라이언트: `api-gateway`
- refresh 반영 대상은 `@ConfigurationProperties` / `@RefreshScope` 빈만 해당. 포트·DB 연결 등은 재시작 필요

### 동작 확인 (gateway 2대)

모든 터미널은 저장소 루트에서 시작.

```bash
# 1) RabbitMQ
docker compose -f rabbitmq/compose.yml up -d

# 2) config-service (터미널 1)
cd services/config-service && BUS_ENABLED=true ./gradlew bootRun

# 3) gateway 2대 (bootRun 동시 실행 시 Gradle 락 충돌 → jar 사용)
#    jar 빌드 (1회)
cd services/api-gateway && ./gradlew bootJar
#    gateway 8000 (터미널 2)
cd services/api-gateway && CONFIG_ENABLED=true BUS_ENABLED=true java -jar build/libs/api-gateway-0.0.1-SNAPSHOT.jar
#    gateway 8001 (터미널 3)
cd services/api-gateway && SERVER_PORT=8001 CONFIG_ENABLED=true BUS_ENABLED=true java -jar build/libs/api-gateway-0.0.1-SNAPSHOT.jar

# 4) 현재 값 확인 → config-repo/api-gateway.yml 수정 → refresh → 재확인 (터미널 4)
for port in 8000 8001; do printf "%s -> " $port; curl -s localhost:$port/actuator/info; echo; done
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8888/actuator/busrefresh
for port in 8000 8001; do printf "%s -> " $port; curl -s localhost:$port/actuator/info; echo; done
```

| 결과 | 의미 |
|---|---|
| `busrefresh` → `204` | Bus 이벤트 발행 성공 |
| 두 인스턴스 모두 변경된 값 | 갱신 성공 (로그: `Keys refreshed [info.config.message]`) |
| `busrefresh` → `405` | `BUS_ENABLED=false` 상태 (엔드포인트 없음) |
| `/actuator/info` → `{}` | gateway에 `CONFIG_ENABLED=true` 누락 (`optional:` import라 에러 없이 기동) |

- RabbitMQ 관리 화면 Queues 탭: `springCloudBus.anonymous...` 큐가 인스턴스 수만큼 생성 (위 예시는 3개)
- 시연 후 `config-repo/api-gateway.yml`은 원래 값(`v1 - from config-repo`)으로 복구 (변경값 커밋 금지)

### 제한 사항 / 배포 시 주의

| 항목 | 내용 | 대응 |
|---|---|---|
| refresh 유실 | 클라이언트가 브로커와 끊겨 있는 동안 발행된 refresh 이벤트는 재연결 후 재전달되지 않음 (익명 큐는 연결 종료 시 삭제) | 재연결 후 `busrefresh` 재호출 또는 해당 인스턴스 재시작 |
| `busrefresh` 인증 없음 | 호출만 하면 전체 인스턴스 설정 갱신 가능 | Ingress·gateway 라우트로 **노출 금지**. K8s에서는 NetworkPolicy로 8888 포트를 클러스터 내부 접근만 허용 |
| 브로커 통신 평문 | 로컬 기본값은 TLS 미사용 | 원격 브로커 사용 시 `SPRING_RABBITMQ_SSL_ENABLED=true`로 TLS 활성화, 계정은 환경변수·K8s Secret으로 주입 (`guest` 사용 금지) |
