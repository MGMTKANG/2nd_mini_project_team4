-- 운영에서 MATCH_JPA_DDL_AUTO=validate로 시작하기 전에 match_db에 적용한다.
-- user_id는 논리 참조이며 다른 서비스 DB를 참조하는 FK는 없다.
CREATE TABLE IF NOT EXISTS match_user_state (
    user_id BIGINT NOT NULL,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;
