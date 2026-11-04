-- Run before deploying match-service. Does not rewrite existing contribution amounts.
ALTER TABLE match_participant ADD COLUMN IF NOT EXISTS point_operation_id VARCHAR(36) NULL;
CREATE TABLE IF NOT EXISTS match_point_debit (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX ix_match_point_debit_recovery (status, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS match_point_refund (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    kind VARCHAR(8) NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX ix_match_point_refund_recovery (status, operation_id)
) ENGINE=InnoDB;
