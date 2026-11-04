-- 빈 match_db에 적용하는 현재 엔티티 4개 테이블의 전체 스키마.
-- 적용 후 MATCH_JPA_DDL_AUTO=validate로 기동한다. 기존 테이블을 변경하지 않는다.
-- target_points/contribution_points는 2026-11-03 SAGA 데모(point-service 연동)로 추가됨.
-- 기존에 이 파일로 이미 match_db를 만들었다면 2026-11-03-match-points-saga.sql을 추가로 적용한다.
CREATE TABLE IF NOT EXISTS `match` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(50) NOT NULL,
    description VARCHAR(500) NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    max_participant INT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'RECRUITING',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    skill_level VARCHAR(30) NOT NULL DEFAULT 'BEGINNER',
    sport_type VARCHAR(15) NOT NULL,
    gender_group VARCHAR(10) NOT NULL,
    service_id VARCHAR(50) NOT NULL,
    target_points BIGINT NULL,
    funding_failed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT ck_match_valid_fields CHECK (
        CHAR_LENGTH(TRIM(title)) > 0 AND max_participant >= 1
        AND end_at > start_at AND CHAR_LENGTH(TRIM(service_id)) > 0
        AND (target_points IS NULL OR target_points > 0)
    )
) ENGINE=InnoDB;

-- user_id와 service_id는 다른 서비스 DB의 논리 참조이며 FK를 만들지 않는다.
CREATE TABLE IF NOT EXISTS match_participant (
    match_participant_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    role VARCHAR(20) NOT NULL,
    contribution_points BIGINT NULL,
    point_operation_id VARCHAR(36) NULL,
    PRIMARY KEY (match_participant_id),
    CONSTRAINT uk_match_participant_match_user UNIQUE (match_id, user_id),
    CONSTRAINT fk_match_participant_match FOREIGN KEY (match_id) REFERENCES `match` (id)
) ENGINE=InnoDB;

-- 운영 SQL의 복합 PK는 ERD 순서다. Hibernate 생성 스키마의 컬럼 순서와 구분한다.
CREATE TABLE IF NOT EXISTS processed_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id CHAR(36) NOT NULL,
    processed_at DATETIME NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
) ENGINE=InnoDB;

-- 사용자 ID는 유저 서비스가 발급하며, 탈퇴 기록은 재적용 후에도 보존한다.
CREATE TABLE IF NOT EXISTS match_user_state (
    user_id BIGINT NOT NULL,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

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
