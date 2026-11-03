-- match·match_participant가 있는 기존 match_db에 적용한다 (2026-11-02-match-schema.sql 이후).
-- 포인트 SAGA 데모(point-service 연동)로 추가된 두 컬럼. 기존 데이터는 모두 NULL(펀딩 없는 매치)로 유지된다.
ALTER TABLE `match`
    ADD COLUMN IF NOT EXISTS target_points BIGINT NULL;

ALTER TABLE match_participant
    ADD COLUMN IF NOT EXISTS contribution_points BIGINT NULL;
