-- match·match_participant가 있는 기존 match_db에 적용한다.
-- 2026-11-01-match-user-state.sql과 함께 적용한 뒤 validate로 기동한다.
-- 기존 테이블의 구조·데이터를 변경하지 않는다. PK 순서는 ERD 기준이다.
CREATE TABLE IF NOT EXISTS processed_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id CHAR(36) NOT NULL,
    processed_at DATETIME NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
) ENGINE=InnoDB;
