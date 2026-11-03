# rabbitmq (Spring Cloud Bus 브로커)

config-service의 설정 변경 이벤트를 각 서비스로 전달하는 메시지 브로커. 로컬 실행용.

| 항목 | 값 |
|---|---|
| AMQP | `localhost:5672` |
| 관리 화면 | http://localhost:15672 (`guest` / `guest`, RabbitMQ 로컬 기본 계정) |

- 포트는 `127.0.0.1`에만 바인딩 (외부 접근 차단)
- `BUS_ENABLED=false`(기본값)이면 실행 불필요

## 실행 / 종료

```bash
docker compose -f rabbitmq/compose.yml up -d
docker compose -f rabbitmq/compose.yml down
```

Bus 동작 확인 방법은 `services/config-service/README.md`의 "Spring Cloud Bus" 참고.
