-- Apply to point_db before deploying the new point-service. Never clear this table on retry.
CREATE TABLE IF NOT EXISTS point_idempotency_operation (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    operation_status VARCHAR(16) NOT NULL,
    result_balance BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_point_idempotency_amount CHECK (amount > 0),
    CONSTRAINT ck_point_idempotency_kind CHECK (kind IN ('DEBIT', 'REFUND')),
    CONSTRAINT ck_point_idempotency_status CHECK (operation_status IN ('NEW', 'DEDUCTED', 'CANCELLED', 'REFUNDED'))
) ENGINE=InnoDB;
