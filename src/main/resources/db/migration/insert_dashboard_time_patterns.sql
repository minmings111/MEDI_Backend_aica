-- ==================================================
-- dashboard_time_pattern_stats 초기 데이터 (Seed Data)
-- ==================================================
-- 
-- 각 채널별로 대시보드 시간대별 악플 통계 데이터를 삽입합니다.
-- channel_id는 실제 youtube_channels 테이블의 id 값으로 변경해야 합니다.
--
-- JSON 형식:
--   time_distribution: { "새벽 (00-06시)": 12, "오전 (06-12시)": 4, ... }
--   red_zone: 별도 칼럼으로 저장 (time_slot, count, percentage)
-- ==================================================

-- 채널 1 (예시: channel_id = 1)
INSERT INTO dashboard_time_pattern_stats (
    channel_id,
    time_distribution,
    red_zone_time_slot,
    red_zone_count,
    red_zone_percentage
) VALUES (
    1,  -- channel_id (실제 값으로 변경 필요)
    JSON_OBJECT(
        '새벽 (00-06시)', 12,
        '오전 (06-12시)', 4,
        '오후 (12-18시)', 12,
        '저녁 (18-22시)', 3,
        '심야 (22-24시)', 3
    ),
    '새벽 (00-06시)',
    12,
    35.29
);

-- 채널 2 (예시: channel_id = 2)
INSERT INTO dashboard_time_pattern_stats (
    channel_id,
    time_distribution,
    red_zone_time_slot,
    red_zone_count,
    red_zone_percentage
) VALUES (
    2,  -- channel_id (실제 값으로 변경 필요)
    JSON_OBJECT(
        '새벽 (00-06시)', 8,
        '오전 (06-12시)', 6,
        '오후 (12-18시)', 10,
        '저녁 (18-22시)', 5,
        '심야 (22-24시)', 4
    ),
    '오후 (12-18시)',
    10,
    30.30
);

-- 채널 3 (예시: channel_id = 3)
INSERT INTO dashboard_time_pattern_stats (
    channel_id,
    time_distribution,
    red_zone_time_slot,
    red_zone_count,
    red_zone_percentage
) VALUES (
    3,  -- channel_id (실제 값으로 변경 필요)
    JSON_OBJECT(
        '새벽 (00-06시)', 5,
        '오전 (06-12시)', 7,
        '오후 (12-18시)', 9,
        '저녁 (18-22시)', 6,
        '심야 (22-24시)', 5
    ),
    '오후 (12-18시)',
    9,
    28.13
);

-- 채널 4 (예시: channel_id = 4)
INSERT INTO dashboard_time_pattern_stats (
    channel_id,
    time_distribution,
    red_zone_time_slot,
    red_zone_count,
    red_zone_percentage
) VALUES (
    4,  -- channel_id (실제 값으로 변경 필요)
    JSON_OBJECT(
        '새벽 (00-06시)', 15,
        '오전 (06-12시)', 3,
        '오후 (12-18시)', 8,
        '저녁 (18-22시)', 4,
        '심야 (22-24시)', 2
    ),
    '새벽 (00-06시)',
    15,
    46.88
);

-- ==================================================
-- 데이터 확인 쿼리
-- ==================================================

-- 모든 채널의 시간대별 데이터 조회
SELECT 
    dtps.id,
    dtps.channel_id,
    yc.channel_name,
    dtps.time_distribution,
    dtps.red_zone_time_slot,
    dtps.red_zone_count,
    dtps.red_zone_percentage,
    dtps.created_at
FROM dashboard_time_pattern_stats dtps
INNER JOIN youtube_channels yc ON dtps.channel_id = yc.id
ORDER BY dtps.channel_id;

-- 특정 채널의 JSON 데이터 파싱 예시
SELECT 
    channel_id,
    JSON_EXTRACT(time_distribution, '$.새벽 (00-06시)') AS 새벽_악플수,
    JSON_EXTRACT(time_distribution, '$.오전 (06-12시)') AS 오전_악플수,
    JSON_EXTRACT(time_distribution, '$.오후 (12-18시)') AS 오후_악플수,
    JSON_EXTRACT(time_distribution, '$.저녁 (18-22시)') AS 저녁_악플수,
    JSON_EXTRACT(time_distribution, '$.심야 (22-24시)') AS 심야_악플수,
    red_zone_time_slot,
    red_zone_count,
    red_zone_percentage
FROM dashboard_time_pattern_stats
WHERE channel_id = 1;

