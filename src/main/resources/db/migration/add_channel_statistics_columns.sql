-- 시연용: youtube_channels 테이블에 총 조회수, 총 댓글 수, 총 동영상 수, 채널 생성일 컬럼 추가
-- 각 컬럼을 별도로 추가하는 방식 (MySQL 버전 호환성)

ALTER TABLE youtube_channels
ADD COLUMN total_view_count BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '총 조회수 (시연용)';

ALTER TABLE youtube_channels
ADD COLUMN total_comment_count BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '총 댓글 수 (시연용, 필터링 비율 계산용)';

ALTER TABLE youtube_channels
ADD COLUMN total_video_count INT UNSIGNED NULL DEFAULT NULL COMMENT '총 동영상 수 (시연용)';

ALTER TABLE youtube_channels
ADD COLUMN channel_created_at DATE NULL DEFAULT NULL COMMENT '채널 생성일 (시연용, YYYY.MM.DD 형식)';
