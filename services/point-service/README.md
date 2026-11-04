# Point service: durable debit and compensation

This service uses demo balances, not real payments. The default initial balance is 10,000.

## Internal API contract

- `POST /internal/points/{userId}/operations/deduct`: `{ "amount": 2000, "operationId": "<UUID>" }`
- `POST /internal/points/{userId}/operations/cancel`: same user, amount and **original debit UUID**.
- `POST /internal/points/{userId}/operations/refund`: a **different stable UUID**, for legacy contributions without a recorded debit UUID.
- Successful responses keep the existing `{ "balance": ... }` shape. A repeated operation returns its stored result; that result is not a live balance query.
- Same ID with different user, amount or operation kind returns `409 POINT_OPERATION_CONFLICT`.
- Deduct after cancellation returns `409 POINT_OPERATION_CANCELLED`.
- Insufficient funds returns `409 INSUFFICIENT_POINTS` without changing the balance.
- The `/operations/*` routes require a UUID on every request. Missing, blank, malformed IDs and nonpositive amounts are rejected before invoking the service.
- Legacy `/internal/points/{userId}/deduct` and `/refund` routes retain their amount-only/optional-ID compatibility. Missing-ID requests are **not idempotent**. New match-service exclusively uses `/operations/*`; it must never fall back to legacy routes.
- Dedicated routes make mixed-version deployment fail before moving money: an old point-service does not recognize `/operations/deduct`, so it cannot ignore the new ID and perform an unrecorded debit. The caller retains its journal and retries compensation after compatible instances are available.

`point_idempotency_operation` binds the UUID to its parameters. An upsert acquires the operation lock before the wallet lock; wallet creation also uses an upsert to serialize first-time concurrent requests. The wallet update and operation result commit in one MariaDB transaction. Cancel before deduct records a CANCELLED tombstone without crediting money, blocking a delayed debit. Do not delete operation records while their callers can retry.

Normal refunds of newly recorded contributions use `cancel`, not standalone `refund`. The standalone refund is a compatibility path for legacy participation rows and must use a deterministic participant-based UUID.

## Deployment (not performed by this change)

1. Apply `docs/sql/2026-11-05-point-idempotency.sql` **in point_db** using the reviewed DB migration procedure. This adds a table and does not alter balances. Existing `point_wallet.id` must already have AUTO_INCREMENT (see the 2026-11-04 migration).
2. Deploy point-service first. Old match-service continues using amount-only requests during this interval, with its previous reliability limits.
3. Apply the match-service journal/contribution schema migration and deploy the new match-service; new requests carry stable IDs and failed transactions are compensated through `/operations/cancel`.
4. Verify debit, duplicate request, cancel, duplicate cancel and delayed-debit rejection in the deployment environment. Local tests do not establish deployment success.

Keep point-service at the compatible version while new match-service uses the operation protocol. Old instances reject the dedicated routes before moving money; partial rollout can therefore cause temporary request failures and delay pending compensation, but must not silently route new calls to the old amount-only protocol.

## Verification

`gradle test --no-daemon --console=plain`

`PointIdempotencyMariaDBTest` uses a local disposable MariaDB 11.4.8 Testcontainer. It covers concurrent first use of the same key, first wallet creation with different keys, debit/cancel races, duplicate legacy refund, cancellation before debit, operation parameter conflicts, insufficient funds and transaction rollback. It skips explicitly when Docker is unavailable. `PointInternalControllerTest` checks legacy HTTP validation, error codes, internal cancellation access and amount-only compatibility. `PointOperationControllerTest` checks all three dedicated routes, required UUID/positive amount validation and internal access without user tokens.

Verified locally on 2026-11-05: 26 tests passed, 0 failed, 0 skipped (10 MariaDB integration + 7 legacy HTTP contract + 9 dedicated-route HTTP contract tests). The MariaDB tests apply the actual operation SQL migration twice, then start Hibernate with `ddl-auto=validate`. Cloud migration and deployment were not performed.
