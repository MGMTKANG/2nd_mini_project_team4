-- Adds target_points (crowdfunding goal = facility reservation cost) to `match`.
-- No preflight query is needed: the column does not exist yet, so no existing row
-- can violate the updated check constraint.
-- Apply while application writes are paused. MariaDB DDL commits implicitly.

ALTER TABLE `match` ADD COLUMN target_points BIGINT NULL;
ALTER TABLE `match` DROP CONSTRAINT ck_match_valid_fields;
ALTER TABLE `match` ADD CONSTRAINT ck_match_valid_fields CHECK
    (CHAR_LENGTH(TRIM(title)) > 0 AND max_participant >= 1 AND end_at > start_at AND service_id > 0
        AND (target_points IS NULL OR target_points > 0));
