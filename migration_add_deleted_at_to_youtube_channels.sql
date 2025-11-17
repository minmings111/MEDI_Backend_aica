-- youtube_channels 테이블에 deleted_at 컬럼 추가 (소프트 삭제용)
-- 실행 전 백업 권장

ALTER TABLE youtube_channels 
ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL 
COMMENT '소프트 삭제용 컬럼. NULL이면 활성, 값이 있으면 삭제됨';

-- 기존 인덱스 확인 후 필요시 인덱스 추가
-- 삭제되지 않은 채널 조회 성능 향상을 위해
CREATE INDEX idx_youtube_channels_deleted_at ON youtube_channels(deleted_at);
CREATE INDEX idx_youtube_channels_user_id_deleted_at ON youtube_channels(user_id, deleted_at);

