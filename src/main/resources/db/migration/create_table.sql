-- ==================================================
-- Medi Project - MySQL Schema (실제 테이블 구조 완전 반영)
-- ==================================================

CREATE SCHEMA IF NOT EXISTS `medi` 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `medi`;

SELECT DATABASE();

-- ==================================================
-- 1단계: 최상위 부모 테이블
-- ==================================================

-- 1-1. users 테이블
-- ✅ phone UNIQUE 제약조건 추가
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password VARCHAR(255) NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NULL UNIQUE,  -- ✅ UNIQUE 추가
    provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL' 
        CHECK (provider IN ('LOCAL', 'GOOGLE')),
    provider_id VARCHAR(255) NULL COMMENT 'Google sub ID',
    profile_image VARCHAR(2048) NULL COMMENT 'OAuth 프로필 이미지 URL',
    
    is_terms_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_email (email),
    INDEX idx_provider (provider),
    UNIQUE KEY uk_provider_id (provider, provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '사용자 정보 (일반 로그인 + OAuth 지원)';

-- 1-2. email_verifications 테이블
CREATE TABLE email_verifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_email_code (email, code),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '이메일 인증 정보';

-- 1-3. subscription_plans 테이블
CREATE TABLE subscription_plans (
    id INT AUTO_INCREMENT PRIMARY KEY,
    plan_name VARCHAR(50) NOT NULL UNIQUE COMMENT 'e.g., Basic, Pro, Premium',
    price DECIMAL(12, 2) NOT NULL,
    channel_limit INT NOT NULL DEFAULT 1,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '구독 플랜 정보';

-- ==================================================
-- 2단계: 1단계 테이블을 참조하는 테이블들
-- ==================================================

-- 2-1. youtube_oauth_tokens 테이블
-- ✅ refresh_count, is_revoked 제거 (실제 테이블에 없음)
CREATE TABLE youtube_oauth_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    google_email VARCHAR(254) NOT NULL,
    access_token MEDIUMTEXT NOT NULL COMMENT 'Encrypted',
    refresh_token MEDIUMTEXT NOT NULL COMMENT 'Encrypted',
    access_token_expires_at DATETIME NOT NULL,
    token_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' 
        CHECK (token_status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    last_used_at DATETIME NULL COMMENT '토큰 마지막 사용 시간 (API 호출 시점)',
    last_refreshed_at DATETIME NULL COMMENT '토큰 마지막 갱신 시간 (새 액세스 토큰 발급)',
    
    CONSTRAINT fk_user_oauth_token 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE KEY uk_user_google_email (user_id, google_email),
    INDEX idx_user_oauth (user_id),
    INDEX idx_token_status (token_status),
    INDEX idx_last_refreshed (last_refreshed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'YouTube OAuth 토큰 정보 (토큰 추적 포함)';

-- 2-2. user_subscriptions 테이블
CREATE TABLE user_subscriptions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    plan_id INT NOT NULL,
    start_date DATETIME NOT NULL,
    end_date DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_subscription 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_plan_subscription 
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_user_status (user_id, status),
    INDEX idx_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '사용자 구독 정보';

-- 2-3. payment_methods 테이블
CREATE TABLE payment_methods (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    pg_billing_key VARCHAR(255) NOT NULL UNIQUE COMMENT 'PG: payment gateway',
    card_type VARCHAR(50),
    card_last_four VARCHAR(4),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_payment_method 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_user_payment (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '결제 수단 정보';

-- 2-4. user_global_rules 테이블
CREATE TABLE user_global_rules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    rule_type VARCHAR(30) NOT NULL 
        CHECK (rule_type IN ('KEYWORD_BLACKLIST', 'KEYWORD_WHITELIST', 
                             'USER_BLACKLIST', 'EXCLUDE_FROM_SCANNING')),
    value VARCHAR(255) COMMENT 'e.g., 금지 키워드 또는 차단할 유저 이름',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_global_rule 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_user_rule (user_id, rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '사용자 전역 필터링 규칙';

-- 2-5. user_filter_preferences 테이블 (댓글 필터링 3단계 폼 설정)
CREATE TABLE user_filter_preferences (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT 'users 테이블 FK',
    channel_id INT NULL COMMENT 'youtube_channels FK (채널별 설정 시 사용, NULL이면 전역 설정)',
    
    -- Step 1: 카테고리 선택
    selected_categories JSON NULL COMMENT '선택한 카테고리 배열 ["profanity", "appearance", ...]',
    
    -- Step 2: 카테고리별 텍스트 입력 (키워드 배열)
    custom_rule_keywords JSON NULL COMMENT '카테고리별 키워드 {"profanity": ["ㅅㅂ", "병X"], "appearance": ["못생겼다"]}',
    
    -- Step 3: 예시 라벨링 결과
    dislike_examples JSON NULL COMMENT '숨기고 싶다고 표시한 댓글 예시 배열',
    allow_examples JSON NULL COMMENT '괜찮다고 표시한 댓글 예시 배열',
    
    -- 메타 정보
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성화 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_filter_pref 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_channel_filter_pref 
        FOREIGN KEY (channel_id) REFERENCES youtube_channels(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    
    INDEX idx_user_id (user_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_user_active (user_id, is_active),
    
    UNIQUE KEY uk_user_channel_pref (user_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '사용자 필터링 설정 (3단계 폼 데이터 저장)';

-- 2-6. filter_example_comments 테이블 (예시 댓글 마스터)
CREATE TABLE filter_example_comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category_id VARCHAR(50) NOT NULL COMMENT '카테고리 ID (profanity, appearance, common 등)',
    comment_text TEXT NOT NULL COMMENT '예시 댓글 내용',
    suggested_label VARCHAR(20) NOT NULL 
        CHECK (suggested_label IN ('allow', 'block'))
        COMMENT '추천 라벨 (사용자에게 힌트)',
    difficulty_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'
        CHECK (difficulty_level IN ('EASY', 'MEDIUM', 'HARD'))
        COMMENT '판단 난이도 (EASY: 명확, HARD: 애매한 케이스)',
    usage_count INT DEFAULT 0 COMMENT '사용 횟수 (통계용)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '활성화 여부',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_category (category_id),
    INDEX idx_active (is_active),
    INDEX idx_difficulty (difficulty_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '필터링 예시 댓글 마스터 (카테고리별)';

-- 2-7. user_example_responses 테이블 (사용자 예시 응답 기록, 선택)
CREATE TABLE user_example_responses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    preference_id INT NOT NULL COMMENT 'user_filter_preferences FK',
    example_comment_id INT NOT NULL COMMENT 'filter_example_comments FK',
    user_label VARCHAR(20) NOT NULL 
        CHECK (user_label IN ('allow', 'block'))
        COMMENT '사용자가 선택한 라벨',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_response_user 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_user_response_pref 
        FOREIGN KEY (preference_id) REFERENCES user_filter_preferences(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_user_response_example 
        FOREIGN KEY (example_comment_id) REFERENCES filter_example_comments(id) 
        ON DELETE CASCADE,
    
    INDEX idx_user_pref (user_id, preference_id),
    UNIQUE KEY uk_user_example (user_id, preference_id, example_comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '사용자별 예시 라벨링 응답 기록 (학습 데이터)';

-- 2-8. daily_comment_stats 테이블 (일별 댓글 통계)
CREATE TABLE daily_comment_stats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    channel_id INT NOT NULL COMMENT 'youtube_channels FK',
    video_id INT NOT NULL COMMENT 'youtube_videos FK',
    stat_date DATE NOT NULL COMMENT '집계 날짜',
    total_count INT NOT NULL DEFAULT 0 COMMENT 'AI가 분석한 전체 댓글 수 (neutral + filtered + suggestion)',
    filtered_count INT NOT NULL DEFAULT 0 COMMENT '필터링된 댓글 수',
    youtube_total_count BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'YouTube Data API에서 가져온 실제 전체 댓글 수',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_dcs_channel
        FOREIGN KEY (channel_id) REFERENCES youtube_channels(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_dcs_video
        FOREIGN KEY (video_id) REFERENCES youtube_videos(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    
    UNIQUE KEY uk_video_date (video_id, stat_date),
    INDEX idx_channel_date (channel_id, stat_date),
    INDEX idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '날짜별 전체/필터링 댓글 통계 (YouTube 실제 댓글 수 포함)';

-- ==================================================
-- 3단계: 2단계 테이블을 참조하는 테이블들
-- ==================================================

-- 3-1. payments 테이블
CREATE TABLE payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    subscription_id INT NOT NULL,
    method_id INT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    pg_transaction_id VARCHAR(255) COMMENT 'Payment Gateway Transaction ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_payment 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_subscription_payment 
        FOREIGN KEY (subscription_id) REFERENCES user_subscriptions(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_method_payment 
        FOREIGN KEY (method_id) REFERENCES payment_methods(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_user_payment_user (user_id),
    INDEX idx_subscription_payment (subscription_id),
    INDEX idx_payment_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '결제 내역';

-- 3-2. youtube_channels 테이블
-- ✅ sync_status, last_sync_error 제거 (실제 테이블에 없음)
-- ✅ oauth_token_id NULL 허용 (실제 테이블과 일치)
-- ✅ deleted_at 포함 (소프트 삭제)
CREATE TABLE youtube_channels (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    oauth_token_id INT NULL,
    youtube_channel_id VARCHAR(50) NOT NULL UNIQUE COMMENT 'ID from YouTube API',
    channel_name VARCHAR(255) NOT NULL,
    channel_handle VARCHAR(100) UNIQUE,
    thumbnail_url VARCHAR(2048),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    last_synced_at DATETIME NULL COMMENT '마지막 전체 동기화 시간',
    last_video_published_at DATETIME NULL COMMENT '마지막 새 영상 발행 시간 (증분 조회 기준점)',
    uploads_playlist_id VARCHAR(64) NULL UNIQUE COMMENT 'YouTube 업로드 플레이리스트 ID (예: UUxxxxxxxxxx)',
    deleted_at DATETIME NULL DEFAULT NULL COMMENT '소프트 삭제용 컬럼',

    -- ✅ 구독자 수 (YouTube API statistics.subscriberCount)
    subscriber_count BIGINT UNSIGNED NULL COMMENT 'statistics.subscriberCount',

    CONSTRAINT fk_user_channel 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_oauth_channel 
        FOREIGN KEY (oauth_token_id) REFERENCES youtube_oauth_tokens(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,

    INDEX idx_user_channel (user_id),
    INDEX idx_youtube_channel_id (youtube_channel_id),
    INDEX idx_last_synced (last_synced_at),
    INDEX idx_deleted_at (deleted_at),
    INDEX idx_user_id_deleted_at (user_id, deleted_at),
    INDEX idx_subscriber_count (subscriber_count)  -- ❓ 필요 시 정렬/조회용
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'YouTube 채널 정보 (증분 동기화 메타 + 소프트 삭제 + 구독자 수)';

-- ==================================================
-- 4단계: 3단계 테이블을 참조하는 테이블들
-- ==================================================

-- 4-1. youtube_videos 테이블
CREATE TABLE youtube_videos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    channel_id INT NOT NULL,
    youtube_video_id VARCHAR(50) NOT NULL UNIQUE COMMENT 'ID from YouTube API',
    title TEXT,
    
    view_count BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'statistics.viewCount',
    like_count BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'statistics.likeCount',
    comment_count BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'statistics.commentCount',
    
    published_at DATETIME NOT NULL,
    thumbnail_url VARCHAR(2048),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_channel_video 
        FOREIGN KEY (channel_id) REFERENCES youtube_channels(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_channel_video (channel_id),
    INDEX idx_youtube_video_id (youtube_video_id),
    INDEX idx_published_at (published_at),
    INDEX idx_channel_published_desc (channel_id, published_at DESC),
    INDEX idx_like_count (like_count),
    INDEX idx_comment_count (comment_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'YouTube 비디오 정보 (통계 포함)';

-- 4-2. youtube_channel_rules 테이블
CREATE TABLE youtube_channel_rules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    channel_id INT NOT NULL,
    rule_type VARCHAR(30) NOT NULL 
        CHECK (rule_type IN ('KEYWORD_BLACKLIST', 'KEYWORD_WHITELIST', 
                             'USER_BLACKLIST', 'EXCLUDE_FROM_SCANNING')),
    value VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_channel_rule 
        FOREIGN KEY (channel_id) REFERENCES youtube_channels(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_channel_rule (channel_id, rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '채널별 필터링 규칙';

-- ==================================================
-- 5단계: 4단계 테이블을 참조하는 테이블들
-- ==================================================

-- 5-1. youtube_comments 테이블
CREATE TABLE youtube_comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    video_id INT NOT NULL,
    youtube_comment_id VARCHAR(255) NOT NULL UNIQUE,
    comment_text TEXT NOT NULL,
    commenter_name VARCHAR(100) NOT NULL,
    published_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'DB에 저장된 시간',
    
    author_channel_id VARCHAR(255) NULL COMMENT '작성자 채널 ID',
    like_count BIGINT NULL DEFAULT 0 COMMENT '좋아요 수',
    updated_at DATETIME NULL COMMENT '댓글 수정 시간',
    parent_id VARCHAR(255) NULL COMMENT '대댓글인 경우 부모 댓글 ID',
    total_reply_count INT NULL DEFAULT 0 COMMENT '대댓글 개수',
    can_rate BOOLEAN NULL DEFAULT TRUE COMMENT '평가 가능 여부',
    viewer_rating VARCHAR(20) NULL COMMENT '시청자 평가 (like, none, dislike)',
    
    CONSTRAINT fk_video_comment 
        FOREIGN KEY (video_id) REFERENCES youtube_videos(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_video_comment (video_id),
    INDEX idx_youtube_comment_id (youtube_comment_id),
    INDEX idx_published_at (published_at),
    INDEX idx_video_published_desc (video_id, published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'YouTube 댓글 정보 (DESC 인덱스로 최신 댓글 조회 최적화)';

-- 5-2. youtube_video_rules 테이블
CREATE TABLE youtube_video_rules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    video_id INT NOT NULL,
    rule_type VARCHAR(30) NOT NULL 
        CHECK (rule_type IN ('KEYWORD_BLACKLIST', 'KEYWORD_WHITELIST', 
                             'USER_BLACKLIST', 'EXCLUDE_FROM_SCANNING')),
    value VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_video_rule 
        FOREIGN KEY (video_id) REFERENCES youtube_videos(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_video_rule (video_id, rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT '비디오별 필터링 규칙';

-- ==================================================
-- 6단계: 5단계 테이블을 참조하는 테이블들 (AI 분석 관련)
-- ==================================================

-- 6-1. ai_comment_analysis_result 테이블
-- ✅ 팀원 제공 SQL 반영 (status: 'filtered', 'content_suggestion', 'normal')
-- ✅ reason, analyzed_at 컬럼 추가
CREATE TABLE ai_comment_analysis_result (
    id INT AUTO_INCREMENT PRIMARY KEY,
    youtube_comment_id INT NOT NULL UNIQUE,
    detected_category VARCHAR(50) COMMENT 'e.g., SPAM, HATE_SPEECH',
    harmfulness_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'
        CHECK (harmfulness_level IN ('LOW', 'MEDIUM', 'HIGH'))
        COMMENT 'AI가 판별한 해로움 수준 (약, 중, 강)',
    detection_source VARCHAR(30) NOT NULL DEFAULT 'AI_MODEL'
        CHECK (detection_source IN ('AI_MODEL', 'USER_KEYWORD', 'USER_CONTEXT'))
        COMMENT 'AI 자체 판단, 사용자 키워드 필터, 사용자 문맥 필터 등',
    ai_model_version VARCHAR(50) COMMENT 'e.g., v1, v1.1 ...',
    status VARCHAR(20) NOT NULL DEFAULT 'filtered'
        CHECK (status IN ('filtered', 'content_suggestion', 'normal')),
    reason VARCHAR(100) NULL
        COMMENT '필터링 이유 (AI 서버의 reason 필드)',
    analyzed_at DATETIME NULL
        COMMENT 'AI가 분석한 시간 (AI 서버의 analyzed_at 필드)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        COMMENT 'DB에 저장된 시간',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '사용자가 status를 변경한 시간',

    CONSTRAINT fk_comment_analysis
        FOREIGN KEY (youtube_comment_id) REFERENCES youtube_comments(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_status (status),
    INDEX idx_harmfulness_level (harmfulness_level),
    INDEX idx_detection_source (detection_source),
    INDEX idx_status_created_desc (status, created_at DESC),
    INDEX idx_analyzed_at (analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'AI 댓글 분석 결과 (상태별 조회 최적화)';

-- 6-2. ai_analysis_summary 테이블
-- ✅ 팀원 제공 SQL 반영 (비디오별 분석 요약)
CREATE TABLE ai_analysis_summary (
    id INT AUTO_INCREMENT PRIMARY KEY,
    video_id INT NOT NULL,
    youtube_video_id VARCHAR(50) NOT NULL,
    youtube_channel_id VARCHAR(50) NULL,

    -- 통계 데이터
    neutral_count INT DEFAULT 0 COMMENT '중립 댓글 수',
    filtered_count INT DEFAULT 0 COMMENT '필터링된 댓글 수',
    suggestion_count INT DEFAULT 0 COMMENT '제안 댓글 수',

    -- 위험도 요약
    risk_summary TEXT COMMENT 'AI 위험도 요약',

    -- 분석 시간
    analysis_timestamp DATETIME NOT NULL COMMENT 'AI 분석 시간',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 외래키
    CONSTRAINT fk_summary_video 
        FOREIGN KEY (video_id) REFERENCES youtube_videos(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,

    -- 인덱스
    INDEX idx_video_id (video_id),
    INDEX idx_youtube_video_id (youtube_video_id),
    INDEX idx_analysis_timestamp (analysis_timestamp),

    -- 유니크 제약 (비디오당 하나의 분석 요약)
    UNIQUE KEY uk_video_summary (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'AI 분석 요약 (비디오 단위)';

-- 6-3. ai_channel_profiling 테이블
-- ✅ 새로 만든 프로파일링 테이블 (JSON 형태로 저장)
CREATE TABLE ai_channel_profiling (
    id INT AUTO_INCREMENT PRIMARY KEY,
    channel_id INT NOT NULL COMMENT 'youtube_channels.id (FK)',
    youtube_channel_id VARCHAR(50) NOT NULL COMMENT 'YouTube 채널 ID (UC...) - 빠른 조회용',
    
    -- JSON 데이터 컬럼
    profile_data JSON NOT NULL COMMENT 'profileData 전체 (creatorProfile 포함)',
    comment_ecosystem JSON NOT NULL COMMENT 'commentEcosystem 데이터',
    channel_communication JSON NOT NULL COMMENT 'channelCommunication 데이터',
    metadata JSON NOT NULL COMMENT 'metadata 데이터',
    
    -- 타임스탬프
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- 외래키
    CONSTRAINT fk_profiling_channel 
        FOREIGN KEY (channel_id) REFERENCES youtube_channels(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    
    -- 인덱스
    INDEX idx_channel_id (channel_id),
    INDEX idx_youtube_channel_id (youtube_channel_id),
    
    -- 유니크 제약 (채널당 하나의 프로파일링 결과)
    UNIQUE KEY uk_channel_profiling (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'AI 채널 프로파일링 결과 (JSON 형태로 저장)';

-- ==================================================
-- 7단계: 기타 유틸리티 테이블
-- ==================================================

-- 7-1. youtube_comment_sync_cursor 테이블
-- YouTube 댓글 동기화 커서 백업 테이블
CREATE TABLE youtube_comment_sync_cursor (
    video_id VARCHAR(50) PRIMARY KEY COMMENT 'YouTube 영상 ID (예: dQw4w9WgXcQ)',
    last_sync_time DATETIME NOT NULL COMMENT '마지막 댓글 동기화 시간',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '커서 수정 시간',
    
    INDEX idx_updated (updated_at) COMMENT '오래된 커서 정리용'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'YouTube 댓글 동기화 커서 (Redis 백업용)';

-- ==================================================
-- 테이블 생성 완료 확인
-- ==================================================

USE medi;

SHOW TABLES;

-- 각 테이블 구조 확인
DESC ai_analysis_summary;
DESC ai_channel_profiling;
DESC ai_comment_analysis_result;
DESC email_verifications;
DESC payment_methods;
DESC payments;
DESC subscription_plans;
DESC user_global_rules;
DESC user_subscriptions;
DESC users;
DESC youtube_channel_rules;
DESC youtube_channels;
DESC youtube_comments;
DESC youtube_comment_sync_cursor;
DESC youtube_oauth_tokens;
DESC youtube_video_rules;
DESC youtube_videos;

-- ==================================================
-- 인덱스 상세 확인
-- ==================================================

SHOW INDEXES FROM youtube_channels;
SHOW INDEXES FROM youtube_videos;
SHOW INDEXES FROM youtube_comments;
SHOW INDEXES FROM youtube_oauth_tokens;
SHOW INDEXES FROM ai_comment_analysis_result;
SHOW INDEXES FROM ai_analysis_summary;
SHOW INDEXES FROM ai_channel_profiling;

-- ==================================================
<<<<<<< HEAD
-- filter_example_comments 초기 데이터 (Seed Data)
-- ==================================================

INSERT INTO filter_example_comments 
(category_id, comment_text, suggested_label, difficulty_level, is_active) VALUES

-- 1. 욕설·비속어 (profanity) 총 12개
('profanity', '야 이 미친 새끼가 ㅅㅂ', 'block', 'EASY', TRUE),
('profanity', '개같은 놈이 왜 또 깝치노', 'block', 'EASY', TRUE),
('profanity', '지랄하고 자빠졌네 진짜', 'block', 'EASY', TRUE),
('profanity', '병신같이 왜 이렇게 느려?', 'block', 'EASY', TRUE),
('profanity', 'ㅋㅋㅋㅋ 개노잼 ㅅㄱ', 'block', 'EASY', TRUE),
('profanity', '엿같네 씨발 진짜', 'block', 'EASY', TRUE),
('profanity', '좆같이 만들었네 누가', 'block', 'EASY', TRUE),
('profanity', '아오 짜증나 씨부랄', 'block', 'EASY', TRUE),
('profanity', '개새끼가 어따 대고', 'block', 'EASY', TRUE),
('profanity', '닥치고 꺼져 이 병신아', 'block', 'EASY', TRUE),
('profanity', '와 진짜 재앙이네 ㅋㅋㅋ', 'allow', 'HARD', TRUE),      -- 은유적
('profanity', '미쳤나봐 완전 미친놈이네', 'block', 'MEDIUM', TRUE),

-- 2. 외모·신체 비하 (appearance) 총 12개
('appearance', '와 돼지새끼가 사람 코스프레하네', 'block', 'EASY', TRUE),
('appearance', '키 작아서 열등감 있나 봄 ㅋㅋ', 'block', 'EASY', TRUE),
('appearance', '얼굴 망했네 성형이라도 해라', 'block', 'EASY', TRUE),
('appearance', '다리 왜 그렇게 짧아 난쟁이냐?', 'block', 'EASY', TRUE),
('appearance', '대머리 새끼 또 깝치네', 'block', 'EASY', TRUE),
('appearance', '코 왜 그렇게 크냐 코끼리냐', 'block', 'EASY', TRUE),
('appearance', '뚱뚱해서 화면 다 가림', 'block', 'EASY', TRUE),
('appearance', '눈이 찢어져서 중국인 같네', 'block', 'EASY', TRUE),
('appearance', '이빨 왜 그렇게 삐뚤어?', 'block', 'EASY', TRUE),
('appearance', '피부 왜 그렇게 더러워?', 'block', 'EASY', TRUE),
('appearance', '얼굴은 좀 아쉽지만 스타일은 괜찮네', 'block', 'HARD', TRUE),
('appearance', '얼굴은 평타인데 몸매가 아쉽네', 'block', 'MEDIUM', TRUE),

-- 3. 인신공격·모욕 (personal_attack) 총 12개
('personal_attack', '너 같은 쓰레기가 어따 대고', 'block', 'EASY', TRUE),
('personal_attack', '인생 존망이라 그러는 거지?', 'block', 'EASY', TRUE),
('personal_attack', '니가 뭔데 지껄이노 꼴값떨지마', 'block', 'EASY', TRUE),
('personal_attack', '얼굴이 증거다 ㅋㅋ 인생 다 보이네', 'block', 'EASY', TRUE),
('personal_attack', '존재 자체가 민폐야 꺼져', 'block', 'EASY', TRUE),
('personal_attack', '부모님이 불쌍하겠다 너 낳아서', 'block', 'EASY', TRUE),
('personal_attack', '머리속에 똥만 차있나 보네', 'block', 'EASY', TRUE),
('personal_attack', '너는 평생 찐따로 살듯', 'block', 'EASY', TRUE),
('personal_attack', '사회생활 못 할 새끼', 'block', 'EASY', TRUE),
('personal_attack', '입만 벌리면 역겨워 진짜', 'block', 'EASY', TRUE),
('personal_attack', '너는 왜 맨날 그렇게 생각하냐?', 'allow', 'HARD', TRUE),
('personal_attack', '솔직히 말해서 좀 실망했음', 'allow', 'MEDIUM', TRUE),

-- 4. 혐오·차별 발언 (hate_speech) 총 12개
('hate_speech', '여자가 게임하면 다 저모양이지', 'block', 'EASY', TRUE),
('hate_speech', '전라도 새끼들은 믿으면 안 됨', 'block', 'EASY', TRUE),
('hate_speech', '장애인이 왜 여기 와서 방해하냐', 'block', 'EASY', TRUE),
('hate_speech', '흑인들은 원래 범죄자 많아', 'block', 'EASY', TRUE),
('hate_speech', '김치녀들 또 돈 타령 ㅉㅉ', 'block', 'EASY', TRUE),
('hate_speech', '홍어새끼들 냄새나서 못 살겠네', 'block', 'EASY', TRUE),
('hate_speech', '동성애자는 그냥 병이다', 'block', 'EASY', TRUE),
('hate_speech', '지방대 나온 주제에 잘난 척', 'block', 'EASY', TRUE),
('hate_speech', '이슬람 놈들 다 테러리스트야', 'block', 'EASY', TRUE),
('hate_speech', '노인네들은 그냥 집에나 있어', 'block', 'EASY', TRUE),
('hate_speech', '요즘 애들은 버릇이 없어', 'allow', 'HARD', TRUE),
('hate_speech', '남자들 대부분 다 똑같더라', 'block', 'MEDIUM', TRUE),

-- 5. 성적 발언·희롱 (sexual) 총 13개 (가장 오탐 많아서 하나 더 넣음)
('sexual', '가슴 진짜 크다 한 번 빨고 싶네', 'block', 'EASY', TRUE),
('sexual', '치마 짧아서 팬티 보이던데 일부러지?', 'block', 'EASY', TRUE),
('sexual', '뒤태 미쳤어 뒤치기하고 싶다', 'block', 'EASY', TRUE),
('sexual', '오늘 좀 꼴림 ㅎㅎ 침대에서 보자', 'block', 'EASY', TRUE),
('sexual', '다리 벌리고 있는 거 봐 섹시하네', 'block', 'EASY', TRUE),
('sexual', '야한 속옷 입었나 보네 색기 장난 아님', 'block', 'EASY', TRUE),
('sexual', '엉덩이 진짜 탱탱하다 만져보고 싶어', 'block', 'EASY', TRUE),
('sexual', '입으로 해줄 줄 알겠네', 'block', 'EASY', TRUE),
('sexual', '몸매가 남자들 다 꼴리게 생겼네', 'block', 'EASY', TRUE),
('sexual', '야 너 가슴으로 뭐 하냐 ㅋㅋ', 'block', 'EASY', TRUE),
('sexual', '와 몸매 관리 진짜 잘했네 부럽다', 'allow', 'HARD', TRUE),
('sexual', '섹시한 게 죄냐 ㅋㅋ', 'block', 'MEDIUM', TRUE),
('sexual', '와 진짜 예쁘게 생겼네', 'allow', 'HARD', TRUE),

-- 6. 스팸·광고·도배 (spam) 총 13개
('spam', '로또 1등 당첨번호 알려드림 ↓↓↓↓', 'block', 'EASY', TRUE),
('spam', '제휴 문의 카톡 ID: money1234', 'block', 'EASY', TRUE),
('spam', 'ㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇㅇ', 'block', 'EASY', TRUE),
('spam', '저희 쇼핑몰 오시면 90% 할인!!', 'block', 'EASY', TRUE),
('spam', '대출 필요하신 분들만 010-XXXX-XXXX', 'block', 'EASY', TRUE),
('spam', 'ㄹㅇㄹㅇㄹㅇㄹㅇㄹㅇㄹㅇㄹㅇㄹㅇㄹㅇ', 'block', 'EASY', TRUE),
('spam', '구독 이벤트 진행중이에요~!!!', 'block', 'EASY', TRUE),
('spam', '비트코인 100배 간다 지금 사세요', 'block', 'EASY', TRUE),
('spam', 'ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ', 'block', 'EASY', TRUE),
('spam', '파워볼 자동프로그램 팝니다 100% 당첨', 'block', 'EASY', TRUE),
('spam', 'ㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠㅠ', 'allow', 'MEDIUM', TRUE),
('spam', '와 진짜 미쳤다 ㄷㄷㄷㄷㄷㄷㄷㄷ', 'allow', 'HARD', TRUE),
('spam', 'ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ', 'allow', 'MEDIUM', TRUE);
=======
-- 마이그레이션: ai_channel_profiling 테이블 컬럼 제거
-- ==================================================
-- profiling_completed_at과 version 컬럼 제거 (metadata JSON에 이미 포함되어 있음)
ALTER TABLE ai_channel_profiling 
    DROP INDEX IF EXISTS idx_profiling_completed_at,
    DROP COLUMN IF EXISTS profiling_completed_at,
    DROP COLUMN IF EXISTS version;
>>>>>>> 508329a6b64a1cb080a11a877c2f67e2c7596136

