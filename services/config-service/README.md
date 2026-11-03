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
