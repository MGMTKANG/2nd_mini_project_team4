-- Apply before deploying the Point image that uses IDENTITY generation.
ALTER TABLE point_db.point_wallet
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
