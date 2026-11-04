# 포인트 실패 복구와 펀딩 종료 (2026-11-05)

관련 이슈: #33(포인트 멱등성), #86(종료 상태·UI), #87(Kafka 준비).
작업 브랜치: `codex/33-funding-reliability`. 병합·운영 DB 변경·배포는 수행하지 않는다.

## 차감과 복구

1. 원격 차감 전에 `match_point_debit`에 UUID, 회원, 금액, PENDING을 별도 트랜잭션으로 커밋한다.
2. 매치 트랜잭션이 이 행을 잠근 뒤 동일 UUID로 차감을 요청한다.
3. 참가자 `point_operation_id` 및 APPLIED를 매치·참가 기록과 함께 커밋한다.
4. 로컬 롤백 시 즉시 `/cancel`을 요청한다. 실패 또는 프로세스 중단 시 PENDING 기록이 남는다.
5. 복구 스케줄러가 같은 행을 잠그고 아직 PENDING인 경우에만 취소한다. 실행 중인 매치 트랜잭션이 먼저 커밋하면 APPLIED를 확인해 취소하지 않는다. 커밋 결과가 불명확한 경우도 DB 상태를 기준으로 판단한다.

신규 매치 서비스는 `/internal/points/{userId}/operations/{deduct,refund,cancel}` 전용 경로만 사용한다. 구버전 포인트 서버가 `operationId`를 무시한 채 차감하는 것을 막기 위해 이전 경로로 자동 폴백하지 않는다.

포인트 서비스는 잔액과 UUID 처리 결과를 같은 트랜잭션으로 저장한다. 취소가 차감보다 먼저 도착해도 취소 기록을 남겨 지연된 차감을 거절한다. 같은 UUID의 다른 회원·금액·작업 종류는 거절한다.

## 환불은 로컬 변경과 함께 접수

참가 취소·매치 삭제·회원 탈퇴·펀딩 실패는 원격 환불을 로컬 커밋 전에 실행하지 않는다.
`match_point_refund` PENDING 의뢰와 참가 기록 삭제/기여금 초기화를 **같은 트랜잭션**으로 저장한다.
로컬 롤백이면 환불 의뢰도 사라지고 포인트도 환불하지 않는다.
커밋 후 즉시 환불을 시도하고, 응답 유실·원격 장애면 의뢰가 남아 주기적으로 재시도한다.

따라서 API 성공은 **매치 변경과 환불 접수 완료**를 의미한다. 원격 장애 중에는 잔액 복구가 늦어질 수 있다.
새 기여금은 원래 차감 UUID로 `/cancel`, 기존 기여금은 참가자 PK에서 만든 안정적인 별도 UUID로 `/refund`를 호출한다. 삭제된 참가자라도 의뢰에는 회원·금액·ID가 남는다. 동일 요청 재전송은 이중 지급하지 않는다.
운영에서 미처리 의뢰와 실패 로그를 관찰해야 한다. 의뢰/작업 이력을 임의로 지우거나 참가자 PK를 초기화하지 않는다.

## 펀딩 실패는 되돌리지 않음

`FundingFailureService`가 매치 행을 잠그고 정원 미달을 확인해 `funding_failed=true`, `CLOSED`를 먼저 커밋한다.
이후 환불 의뢰를 접수한다. 두 단계 사이 프로세스가 중단되면 종료된 매치의 남은 양수 기여금을 복구 스케줄러가 재접수한다.
참가 취소·일정 수정은 이 매치를 다시 모집하지 않는다. 기존 CLOSED API 값은 유지한다.
상세 응답의 `fundingFailed`와 `fundedPoints`는 종료 여부와 환불 접수 후 남은 기여금 합계를 나타낸다. `fundedPoints`는 사용자의 현재 지갑 잔액이나 원격 환불 완료 여부가 아니다.

## 스케줄러 및 부하 한계

기본 복구 간격/최초 지연은 60초(`match.points.recovery.delay-ms`), 미확정 차감은 생성 30초 후부터 조회한다.
차감 취소, 환불 의뢰 처리, 펀딩 종료 후 환불 접수의 세 작업은 최대 50개씩 순환 조회해 실패한 선두 기록이 뒤 기록을 영구히 막지 않도록 한다.
`match.points.recovery.enabled=false`는 세 복구 스케줄러를 비활성화하므로 운영에서 임의로 끄지 않는다.
DB 행 잠금과 포인트 작업 UUID가 여러 인스턴스의 중복 실행을 조정한다. 커서는 효율을 위한 메모리 상태이며 재시작 시 처음부터 재조회해도 안전하다.

차감 의뢰 준비 및 커밋 후 처리에 REQUIRES_NEW를 사용하므로 동시 요청 수에 비해 연결 풀이 작으면 추가 연결 대기가 생길 수 있다. 운영 부하·pool 크기 검증은 별도로 필요하다. 원격 장애가 지속되면 복구 완료 시각을 보장하지 않으며 PENDING 의뢰를 유지한다.
이 변경은 사용자의 별도 HTTP 매치 생성 요청 두 번을 하나로 합치는 기능은 아니다.

## 배포 전 필수 순서 (이번 작업에서는 미실행)

1. point_db: `services/point-service/docs/sql/2026-11-05-point-idempotency.sql` 적용.
2. 새 point-service 배포 후 operationId·cancel 계약 확인. 이전 match-service 요청도 잠시 호환된다.
3. match_db: `docs/sql/2026-11-05-match-funding-failed.sql` 및 `docs/sql/2026-11-05-point-debit-recovery.sql` 적용.
4. 새 match-service 배포. 운영 `ddl-auto=validate`라 새 스키마 없이 배포하면 시작에 실패한다.
5. 프런트 반영 후 생성·참여·탈퇴·삭제·펀딩 실패·일시 장애 후 잔액 복구를 확인한다.

기존 잔액·기여금은 마이그레이션에서 수정하지 않는다. 과거에 이미 환불된 CLOSED 매치는 일반 마감과 자동 구분할 수 없어 실패 상태를 추정해 소급 변경하지 않는다.
새 match-service가 동작하는 동안 point-service를 구버전으로 되돌리면 cancel API와 멱등성이 없어지므로 함께 검토해야 한다.
Kafka 브로커가 아직 없으므로 회원 탈퇴 이벤트의 운영 E2E는 미완료다. `deploy/kafka-withdrawal/README.md`의 준비 절차를 따른다.

## 검증 범위

- 실제 로컬 MariaDB: 포인트 중복·동시 최초 지갑 생성·취소 선행·응답 유실·롤백 및 운영 SQL 재적용.
- 실제 로컬 MariaDB + 포인트 fake: 매치 저장 실패 보상, 차감/환불 응답 유실, 프로세스 중단 상태 복구, 진행 중 커밋과 복구의 행 잠금 경합, 로컬 삭제 롤백 시 원격 환불 금지.
- HTTP 계약: 차감/취소에 같은 operationId 전달, 취소·충돌을 잔액 부족과 구분.
- 프런트: 종료된 펀딩 참여 차단, 실제 기여금 표시, 잔액 부족 안내, 수정 불가 비용, 최소 정원 2명.
- Kafka: 서버에 접속하지 않는 입력 검증 및 Kustomize 렌더링.

검증 명령:

```sh
gradle -p services/match-service test --console=plain
gradle -p services/point-service test --console=plain
CI=true npm --prefix frontend test -- --watchAll=false --runInBand
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/kafka-withdrawal -p 'test_*.py' -v
```

운영 서버, 실제 클라우드 DB, 배포 사이트의 로그인 사용자 흐름은 이번 코드 테스트로 검증한 것이 아니다.

### 로컬 실행 결과

2026-11-05 실행: 매치 전체 322개 통과(실패/건너뜀 0). 이후 전용 포인트 API 분리 및 구버전 경로 폴백 금지 검증을 추가하고 영향받은 핵심 28개(Feign, 복구 MariaDB, 펀딩 종료, 재시도, 운영 스키마)를 재실행해 모두 통과했다.
포인트 전체 26개(실제 MariaDB 10 + HTTP 16), 프런트 전체 27 suites/264개, Kafka 설정 6개가 통과했다. 프런트 production build와 git diff --check도 통과했다.
원격 CI·CodeRabbit 검토는 PR에서 별도로 확인하며 이 로컬 결과와 혼동하지 않는다.
