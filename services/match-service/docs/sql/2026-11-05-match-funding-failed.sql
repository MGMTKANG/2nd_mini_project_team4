-- match-service 새 버전 기동 전에 기존 match_db에 적용한다.
-- 이 스크립트는 기존 매치를 실패 처리하거나 포인트를 변경하지 않는다.
ALTER TABLE `match`
    ADD COLUMN IF NOT EXISTS funding_failed BOOLEAN NOT NULL DEFAULT FALSE;

-- 과거에 이미 환불한 CLOSED 매치는 현재 데이터만으로 일반 마감과 구분할 수 없다.
-- 운영 이력으로 확인된 대상만 별도 검토하며 일괄 추정/갱신하지 않는다.
