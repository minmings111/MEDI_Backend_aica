package com.medi.backend.youtube.redis.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadListResponse;
import com.medi.backend.youtube.dto.YoutubeCommentSyncCursorDto;
import com.medi.backend.youtube.mapper.YoutubeCommentSyncCursorMapper;
import com.medi.backend.youtube.redis.dto.RedisYoutubeComment;
import com.medi.backend.youtube.redis.dto.RedisYoutubeCommentFull;
import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;
import com.medi.backend.youtube.redis.dto.SyncOptions;
import com.medi.backend.youtube.redis.mapper.YoutubeCommentMapper;
import com.medi.backend.youtube.redis.util.YoutubeApiClientUtil;
import com.medi.backend.youtube.redis.util.YoutubeErrorUtil;
import com.medi.backend.youtube.service.YoutubeOAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * YouTube 댓글 동기화 서비스 구현체
 * 
 * 주요 기능:
 * 1. 사용자의 채널별 조회수 상위 20개 영상의 댓글 조회
 * 2. YouTube API를 통해 댓글 데이터 수집
 * 3. Redis에 JSON 배열 형식으로 저장
 * 
 * Redis 저장 형식:
 * - 초기 동기화: video:{video_id}:comments:init (채널 프로파일링용)
 * Type: String (JSON 배열)
 * - 증분 동기화: video:{video_id}:comments (원본 데이터, 절대 수정 금지)
 * Type: Hash
 * Field: comment_id, Value: JSON 문자열 (전체 메타데이터)
 * - 필터링 결과: video:{video_id}:classification (FastAPI agent가 저장)
 * Type: Hash
 * Field: comment_id, Value: JSON 문자열 (분류 결과)
 * 
 * 참고:
 * - channel_comment_fetcher.py의 로직을 Java로 구현
 * - AI 서버(Python/TypeScript)와의 데이터 호환성 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeCommentServiceImpl implements YoutubeCommentService {

    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeCommentMapper redisMapper;
    private final YoutubeCommentSyncCursorMapper cursorMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final com.medi.backend.youtube.service.YoutubeDataApiClient youtubeDataApiClient;
    private final com.medi.backend.youtube.config.YoutubeDataApiProperties youtubeDataApiProperties;

    private static final Duration COMMENT_HASH_TTL = Duration.ofDays(3);
    private static final Duration PROCESSED_SET_TTL = Duration.ofDays(30);
    private static final Duration DEFAULT_CURSOR_LOOKBACK = Duration.ofDays(30);
    private static final int MAX_PAGE_LIMIT = 50;
    private static final int CONSECUTIVE_OLD_PAGE_THRESHOLD = 5;
    private static final ZoneId YOUTUBE_TIME_ZONE = ZoneId.of("UTC");
    private static final DateTimeFormatter CURSOR_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public long syncTop10VideoComments(Integer userId, Map<String, List<RedisYoutubeVideo>> videosByChannel,
            SyncOptions options) {
        try {
            // 1. videosByChannel 검증 (이미 조회된 결과를 재사용하여 중복 API 호출 방지)
            if (videosByChannel == null || videosByChannel.isEmpty()) {
                log.warn("비디오 리스트가 비어있습니다: userId={}", userId);
                return 0;
            }

            // 옵션이 null이면 기본 옵션 사용
            if (options == null) {
                options = SyncOptions.initialSync();
            }

            // 2. YouTube API 클라이언트 생성 (재사용성 향상)
            YouTube yt = YoutubeApiClientUtil.buildClientForUser(youtubeOAuthService, userId);

            long totalCommentCount = 0;

            // 3. 각 채널의 상위 20개 영상의 댓글 조회 및 Redis 저장
            for (Map.Entry<String, List<RedisYoutubeVideo>> entry : videosByChannel.entrySet()) {
                String channelId = entry.getKey();
                List<RedisYoutubeVideo> videos = entry.getValue();

                log.debug("채널 {}의 {}개 영상 댓글 조회 시작", channelId, videos.size());

                for (RedisYoutubeVideo video : videos) {
                    try {
                        String videoId = video.getYoutubeVideoId();

                        // 보안: videoId null 체크 및 빈 문자열 검증
                        if (videoId == null || videoId.isBlank()) {
                            log.warn("영상 ID가 없습니다. 건너뜁니다: {}", video);
                            continue;
                        }

                        // 변경: Redis Key 형식 변경
                        // 초기 동기화: video:{videoId}:comments:init (채널 프로파일링용)
                        String redisKey = "video:" + videoId + ":comments:init";

                        // 부분 실패 방지: 기존 댓글 백업 (String 타입으로 저장되어 있음)
                        String existingComments = stringRedisTemplate.opsForValue().get(redisKey);

                        long commentCount = 0;
                        try {
                            commentCount = fetchAndSaveCommentsSnapshot(yt, videoId, redisKey, options);

                            // 부분 실패 처리: 새 댓글이 없고 기존 댓글이 있었으면 복구
                            if (commentCount == 0 && existingComments != null && !existingComments.isEmpty()) {
                                log.warn("댓글 조회 실패 또는 댓글 없음. 기존 댓글 복구: {}", videoId);
                                stringRedisTemplate.opsForValue().set(redisKey, existingComments);
                            }

                            totalCommentCount += commentCount;
                            log.debug("영상 {}의 댓글 {}개 저장 완료", videoId, commentCount);
                        } catch (Exception saveException) {
                            // 부분 실패 처리: 저장 실패 시 기존 댓글 복구
                            if (existingComments != null && !existingComments.isEmpty()) {
                                log.warn("댓글 저장 실패. 기존 댓글 복구: {}", videoId);
                                stringRedisTemplate.opsForValue().set(redisKey, existingComments);
                            }
                            throw saveException; // 예외를 다시 던져서 상위 catch에서 처리
                        }
                    } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                        // Python 코드 참고: HttpError 예외 처리
                        // Python: if reason in {"commentsDisabled", "disabledComments"}
                        String errorReason = YoutubeErrorUtil.extractErrorReason(e);
                        if ("commentsDisabled".equals(errorReason) || "disabledComments".equals(errorReason)) {
                            log.info("영상 {}의 댓글이 비활성화되어 있습니다", video.getYoutubeVideoId());
                        } else {
                            log.error("영상 {}의 댓글 조회 실패: {} (reason: {})",
                                    video.getYoutubeVideoId(), e.getMessage(), errorReason);
                        }
                        // 한 영상 실패해도 다른 영상은 계속 처리
                    } catch (Exception e) {
                        log.error("영상 {}의 댓글 조회 실패: {}", video.getYoutubeVideoId(), e.getMessage());
                        // 한 영상 실패해도 다른 영상은 계속 처리
                    }
                }
            }

            log.info("각 채널별 조회수 상위 20개 영상의 댓글 동기화 완료: userId={}, 총 댓글 수={}",
                    userId, totalCommentCount);
            return totalCommentCount;

        } catch (Exception e) {
            log.error("각 채널별 조회수 상위 20개 영상 댓글 동기화 실패: userId={}", userId, e);
            throw new RuntimeException("syncTop10VideoComments failed", e);
        }
    }

    @Override
    public long syncVideoComments(Integer userId, List<String> videoIds, SyncOptions options) {
        try {
            if (videoIds == null || videoIds.isEmpty()) {
                log.warn("비디오 ID 리스트가 비어있습니다: userId={}", userId);
                return 0;
            }

            // 옵션이 null이면 증분 동기화 옵션 사용
            if (options == null) {
                options = SyncOptions.incrementalSync();
            }

            // OAuth 토큰 가져오기
            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = YoutubeApiClientUtil.buildClient(token);

            long totalCommentCount = 0;

            // 각 비디오의 댓글 조회 및 Redis 저장
            for (String videoId : videoIds) {
                try {
                    if (videoId == null || videoId.isBlank()) {
                        log.warn("유효하지 않은 비디오 ID: {}", videoId);
                        continue;
                    }

                    String commentsKey = buildCommentsKey(videoId);
                    String processedKey = buildProcessedKey(videoId);
                    String cursorKey = buildCursorKey(videoId);

                    LocalDateTime cursorTime = getLastSyncTime(cursorKey, videoId);

                    IncrementalFetchResult incrementalResult = fetchAndSaveCommentsIncremental(yt, videoId, commentsKey,
                            cursorTime, options);
                    totalCommentCount += incrementalResult.getNewCount();
                    log.debug("영상 {}의 새 댓글 {}개 저장 완료", videoId, incrementalResult.getNewCount());

                    if (incrementalResult.getLatestPublishedAt() != null) {
                        updateLastSyncTime(cursorKey, videoId, incrementalResult.getLatestPublishedAt());
                    }

                    stringRedisTemplate.expire(commentsKey, COMMENT_HASH_TTL);
                    stringRedisTemplate.expire(processedKey, PROCESSED_SET_TTL);
                } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                    String errorReason = YoutubeErrorUtil.extractErrorReason(e);
                    if ("commentsDisabled".equals(errorReason) || "disabledComments".equals(errorReason)) {
                        log.info("영상 {}의 댓글이 비활성화되어 있습니다", videoId);
                    } else {
                        log.error("영상 {}의 댓글 조회 실패: {} (reason: {})", videoId, e.getMessage(), errorReason);
                    }
                } catch (Exception e) {
                    log.error("영상 {}의 댓글 조회 실패: {}", videoId, e.getMessage());
                }
            }

            log.info("비디오 댓글 동기화 완료: userId={}, 비디오={}개, 총 댓글 수={}",
                    userId, videoIds.size(), totalCommentCount);
            return totalCommentCount;

        } catch (Exception e) {
            log.error("비디오 댓글 동기화 실패: userId={}", userId, e);
            throw new RuntimeException("syncVideoComments failed", e);
        }
    }

    /**
     * 영상의 댓글을 조회하여 Redis Hash에 증분 저장
     */
    private IncrementalFetchResult fetchAndSaveCommentsIncremental(YouTube yt,
            String videoId,
            String commentsKey,
            LocalDateTime cursorTime,
            SyncOptions options) throws Exception {
        // 옵션에 따라 기본 또는 전체 메타데이터 사용
        boolean useFullMetadata = options != null && options.isIncludeFullMetadata();

        Set<Object> existingKeys = stringRedisTemplate.opsForHash().keys(commentsKey);
        Set<String> existingIds = existingKeys.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        List<Object> newComments = new ArrayList<>();
        String nextPageToken = null;
        int pageCount = 0;
        int consecutiveOldPages = 0;
        LocalDateTime latestPublishedAt = null;

        // 댓글 개수 제한: 옵션에 따라 결정 (null이면 제한 없음)
        Integer maxCommentCount = options != null ? options.getMaxCommentCount() : null;
        LocalDateTime cursorThreshold = cursorTime != null
                ? cursorTime
                : LocalDateTime.now().minus(DEFAULT_CURSOR_LOOKBACK);

        do {
            if (maxCommentCount != null && maxCommentCount > 0 && newComments.size() >= maxCommentCount) {
                break;
            }
            pageCount++;

            // ⭐ YouTube CommentThreads API 요청 생성
            // API 엔드포인트: youtube.commentThreads.list
            // 용도: 특정 영상의 댓글 스레드 조회 (최상위 댓글 + 대댓글)
            // Python 코드 참고: commentThreads().list(part="snippet,replies")
            YouTube.CommentThreads.List req = yt.commentThreads()
                    .list(Arrays.asList("snippet", "replies")); // snippet: 댓글 내용, 작성자 등 / replies: 대댓글
            req.setVideoId(videoId);

            // Python 코드 참고: order 파라미터 (기본값 "relevance", 여기서는 "time" 사용)
            // Python: order=self.order (choices: "relevance" | "time")
            req.setOrder("time"); // 시간순 정렬

            // Python 코드 참고: maxResults (Python: page_size, 기본값 100, 범위 1~100)
            // YouTube API 최대값 100 사용
            req.setMaxResults(100L); // 한 페이지당 최대 100개 댓글

            // Python 코드 참고: pageToken=next_page_token
            if (nextPageToken != null) {
                req.setPageToken(nextPageToken); // 페이지네이션: 다음 페이지 토큰 설정
            }

            // ⭐ 실제 YouTube CommentThreads API 호출 실행
            // API 키 fallback: API 키 우선 시도, 실패 시 OAuth 토큰 사용
            CommentThreadListResponse resp;
            try {
                if (youtubeDataApiClient.hasApiKeys()) {
                    try {
                        resp = youtubeDataApiClient.fetchCommentThreads(videoId, nextPageToken, 100L);
                    } catch (com.medi.backend.youtube.exception.NoAvailableApiKeyException ex) {
                        if (!youtubeDataApiProperties.isEnableFallback()) {
                            throw ex;
                        }
                        log.debug("YouTube Data API 키 사용 불가, OAuth 토큰으로 폴백: videoId={}", videoId);
                        resp = req.execute();
                    }
                } else {
                    resp = req.execute();
                }
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                // API 키 쿼터 초과 등 403 에러 처리
                if (youtubeDataApiClient.hasApiKeys() && youtubeDataApiProperties.isEnableFallback()
                        && e.getStatusCode() == 403) {
                    String errorReason = com.medi.backend.youtube.redis.util.YoutubeErrorUtil.extractErrorReason(e);
                    if ("quotaExceeded".equals(errorReason) || "dailyLimitExceeded".equals(errorReason)
                            || "userRateLimitExceeded".equals(errorReason)) {
                        log.debug("YouTube Data API 키 쿼터 초과, OAuth 토큰으로 폴백: videoId={}", videoId);
                        resp = req.execute();
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }

            // Python 코드 참고: items = response.get("items", [])
            int newInPage = 0;

            if (resp.getItems() != null) {
                // Python 코드 참고: for thread in threads 루프
                for (CommentThread thread : resp.getItems()) {
                    if (maxCommentCount != null && maxCommentCount > 0 && newComments.size() >= maxCommentCount) {
                        break;
                    }

                    Comment top = thread.getSnippet().getTopLevelComment();
                    if (top == null) {
                        continue;
                    }
                    String topId = top.getId();
                    if (topId == null || topId.isBlank()) {
                        continue;
                    }
                    LocalDateTime topPublishedAt = toLocalDateTime(top.getSnippet().getPublishedAt());
                    if (topPublishedAt != null && !topPublishedAt.isAfter(cursorThreshold)) {
                        continue;
                    }
                    boolean alreadyExists = existingIds.contains(topId);

                    if (!alreadyExists && useFullMetadata) {
                        Long totalReplyCount = null;
                        if (thread.getSnippet().getTotalReplyCount() != null) {
                            totalReplyCount = thread.getSnippet().getTotalReplyCount().longValue();
                        }
                        RedisYoutubeCommentFull topComment = redisMapper.toRedisCommentFull(top, null, totalReplyCount);
                        if (topComment != null) {
                            newComments.add(topComment);
                            existingIds.add(topId);
                            if (maxCommentCount != null && maxCommentCount > 0
                                    && newComments.size() >= maxCommentCount) {
                                break;
                            }
                            newInPage++;
                            latestPublishedAt = max(latestPublishedAt, topPublishedAt);
                        }
                    } else if (!alreadyExists) {
                        RedisYoutubeComment topComment = redisMapper.toRedisComment(top, null);
                        if (topComment != null) {
                            newComments.add(topComment);
                            existingIds.add(topId);
                            if (maxCommentCount != null && maxCommentCount > 0
                                    && newComments.size() >= maxCommentCount) {
                                break;
                            }
                            newInPage++;
                            latestPublishedAt = max(latestPublishedAt, topPublishedAt);
                        }
                    }

                    // 대댓글 처리
                    // Python 코드 참고: replies도 동일하게 처리
                    if (thread.getReplies() != null
                            && thread.getReplies().getComments() != null) {
                        for (Comment reply : thread.getReplies().getComments()) {
                            // 댓글 개수 제한 체크 (제한이 설정된 경우에만)
                            if (maxCommentCount != null && maxCommentCount > 0
                                    && newComments.size() >= maxCommentCount) {
                                break;
                            }

                            String replyId = reply != null ? reply.getId() : null;
                            if (replyId == null || existingIds.contains(replyId)) {
                                continue;
                            }
                            LocalDateTime replyPublishedAt = reply != null
                                    ? toLocalDateTime(reply.getSnippet().getPublishedAt())
                                    : null;
                            if (replyPublishedAt != null && !replyPublishedAt.isAfter(cursorThreshold)) {
                                continue;
                            }

                            if (useFullMetadata) {
                                RedisYoutubeCommentFull replyComment = redisMapper.toRedisCommentFull(
                                        reply, top.getId(), null);
                                if (replyComment != null) {
                                    newComments.add(replyComment);
                                    existingIds.add(replyId);
                                    newInPage++;
                                    latestPublishedAt = max(latestPublishedAt, replyPublishedAt);
                                }
                            } else {
                                RedisYoutubeComment replyComment = redisMapper.toRedisComment(
                                        reply, top.getId());
                                if (replyComment != null) {
                                    newComments.add(replyComment);
                                    existingIds.add(replyId);
                                    newInPage++;
                                    latestPublishedAt = max(latestPublishedAt, replyPublishedAt);
                                }
                            }
                        }
                    }
                }
            }

            // Python 코드 참고: next_page_token = response.get("nextPageToken")
            nextPageToken = resp.getNextPageToken();

            if (newInPage == 0) {
                consecutiveOldPages++;
            } else {
                consecutiveOldPages = 0;
            }

            if (consecutiveOldPages >= CONSECUTIVE_OLD_PAGE_THRESHOLD) {
                log.info("커서 이전 댓글 페이지가 연속 {}회 발생하여 조회를 중단합니다. videoId={}",
                        consecutiveOldPages, videoId);
                break;
            }

            if (pageCount >= MAX_PAGE_LIMIT) {
                log.warn("페이지 한도({})에 도달하여 조회를 중단합니다. videoId={}", MAX_PAGE_LIMIT, videoId);
                break;
            }

        } while (nextPageToken != null &&
                (maxCommentCount == null || maxCommentCount <= 0 || newComments.size() < maxCommentCount));

        if (maxCommentCount != null && maxCommentCount > 0 && newComments.size() > maxCommentCount) {
            newComments = newComments.subList(0, maxCommentCount);
        }

        if (!newComments.isEmpty()) {
            saveCommentsToRedisHash(commentsKey, newComments);
        }

        return new IncrementalFetchResult(newComments.size(), latestPublishedAt);
    }

    /**
     * 초기 동기화용: 영상의 댓글을 조회하여 JSON 배열(String)로 저장
     */
    private long fetchAndSaveCommentsSnapshot(YouTube yt, String videoId, String redisKey, SyncOptions options)
            throws Exception {
        boolean useFullMetadata = options != null && options.isIncludeFullMetadata();
        List<Object> allComments = new ArrayList<>();
        String nextPageToken = null;
        Integer maxCommentCount = options != null ? options.getMaxCommentCount() : null;

        do {
            if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                break;
            }

            YouTube.CommentThreads.List req = yt.commentThreads()
                    .list(Arrays.asList("snippet", "replies"));
            req.setVideoId(videoId);
            req.setOrder("time");
            req.setMaxResults(100L);
            if (nextPageToken != null) {
                req.setPageToken(nextPageToken);
            }

            // ⭐ 실제 YouTube CommentThreads API 호출 실행
            // API 키 fallback: API 키 우선 시도, 실패 시 OAuth 토큰 사용
            CommentThreadListResponse resp;
            try {
                if (youtubeDataApiClient.hasApiKeys()) {
                    try {
                        resp = youtubeDataApiClient.fetchCommentThreads(videoId, nextPageToken, 100L);
                    } catch (com.medi.backend.youtube.exception.NoAvailableApiKeyException ex) {
                        if (!youtubeDataApiProperties.isEnableFallback()) {
                            throw ex;
                        }
                        log.debug("YouTube Data API 키 사용 불가, OAuth 토큰으로 폴백: videoId={}", videoId);
                        resp = req.execute();
                    }
                } else {
                    resp = req.execute();
                }
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                // API 키 쿼터 초과 등 403 에러 처리
                if (youtubeDataApiClient.hasApiKeys() && youtubeDataApiProperties.isEnableFallback()
                        && e.getStatusCode() == 403) {
                    String errorReason = com.medi.backend.youtube.redis.util.YoutubeErrorUtil.extractErrorReason(e);
                    if ("quotaExceeded".equals(errorReason) || "dailyLimitExceeded".equals(errorReason)
                            || "userRateLimitExceeded".equals(errorReason)) {
                        log.debug("YouTube Data API 키 쿼터 초과, OAuth 토큰으로 폴백: videoId={}", videoId);
                        resp = req.execute();
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
            if (resp.getItems() != null) {
                for (CommentThread thread : resp.getItems()) {
                    if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                        break;
                    }

                    Comment top = thread.getSnippet().getTopLevelComment();
                    if (useFullMetadata) {
                        Long totalReplyCount = null;
                        if (thread.getSnippet().getTotalReplyCount() != null) {
                            totalReplyCount = thread.getSnippet().getTotalReplyCount().longValue();
                        }
                        RedisYoutubeCommentFull topComment = redisMapper.toRedisCommentFull(top, null, totalReplyCount);
                        if (topComment != null) {
                            allComments.add(topComment);
                            if (maxCommentCount != null && maxCommentCount > 0
                                    && allComments.size() >= maxCommentCount) {
                                break;
                            }
                        }
                    } else {
                        RedisYoutubeComment topComment = redisMapper.toRedisComment(top, null);
                        if (topComment != null) {
                            allComments.add(topComment);
                            if (maxCommentCount != null && maxCommentCount > 0
                                    && allComments.size() >= maxCommentCount) {
                                break;
                            }
                        }
                    }

                    if (thread.getReplies() != null
                            && thread.getReplies().getComments() != null) {
                        for (Comment reply : thread.getReplies().getComments()) {
                            if (maxCommentCount != null && maxCommentCount > 0
                                    && allComments.size() >= maxCommentCount) {
                                break;
                            }

                            if (useFullMetadata) {
                                RedisYoutubeCommentFull replyComment = redisMapper.toRedisCommentFull(
                                        reply, top != null ? top.getId() : null, null);
                                if (replyComment != null) {
                                    allComments.add(replyComment);
                                }
                            } else {
                                RedisYoutubeComment replyComment = redisMapper.toRedisComment(
                                        reply, top != null ? top.getId() : null);
                                if (replyComment != null) {
                                    allComments.add(replyComment);
                                }
                            }
                        }
                    }
                }
            }

            nextPageToken = resp.getNextPageToken();
        } while (nextPageToken != null &&
                (maxCommentCount == null || maxCommentCount <= 0 || allComments.size() < maxCommentCount));

        if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() > maxCommentCount) {
            allComments = allComments.subList(0, maxCommentCount);
        }

        if (!allComments.isEmpty()) {
            saveCommentsToRedis(redisKey, allComments);
        }

        return allComments.size();
    }

    private void saveCommentsToRedisHash(String commentsKey, List<Object> comments) {
        for (Object comment : comments) {
            String commentId = extractCommentId(comment);
            if (commentId == null || commentId.isBlank()) {
                continue;
            }
            try {
                String json = objectMapper.writeValueAsString(comment);
                stringRedisTemplate.opsForHash().put(commentsKey, commentId, json);
            } catch (JsonProcessingException e) {
                log.error("댓글 JSON 변환 실패: key={}, commentId={}", commentsKey, commentId, e);
            }
        }
        stringRedisTemplate.expire(commentsKey, Duration.ofDays(3));
    }

    private void saveCommentsToRedis(String redisKey, List<Object> comments) {
        try {
            String jsonArray = objectMapper.writeValueAsString(comments);
            stringRedisTemplate.opsForValue().set(redisKey, jsonArray);
            stringRedisTemplate.expire(redisKey, Duration.ofDays(3));
            log.debug("댓글 {}개를 Redis에 저장 완료: key={}", comments.size(), redisKey);
        } catch (JsonProcessingException e) {
            log.error("댓글 리스트 직렬화 실패: key={}, size={}", redisKey, comments.size(), e);
            throw new RuntimeException("댓글 JSON 변환 실패", e);
        }
    }

    private String extractCommentId(Object comment) {
        if (comment instanceof RedisYoutubeCommentFull) {
            return ((RedisYoutubeCommentFull) comment).getCommentId();
        }
        if (comment instanceof RedisYoutubeComment) {
            return ((RedisYoutubeComment) comment).getCommentId();
        }
        return null;
    }

    private String buildCommentsKey(String videoId) {
        return "video:" + videoId + ":comments";
    }

    private String buildProcessedKey(String videoId) {
        return "video:" + videoId + ":processed";
    }

    private String buildCursorKey(String videoId) {
        return "video:" + videoId + ":last_sync_time";
    }

    private LocalDateTime getLastSyncTime(String cursorKey, String videoId) {
        LocalDateTime redisCursor = readCursorFromRedis(cursorKey);
        if (redisCursor != null) {
            return redisCursor;
        }

        LocalDateTime dbCursor = readCursorFromDatabase(videoId);
        if (dbCursor != null) {
            stringRedisTemplate.opsForValue().set(cursorKey, dbCursor.format(CURSOR_FORMATTER));
            log.debug("DB 커서를 Redis로 복구: videoId={}, cursor={}", videoId, dbCursor);
            return dbCursor;
        }

        LocalDateTime fallback = LocalDateTime.now().minus(DEFAULT_CURSOR_LOOKBACK);
        log.debug("커서가 없어 기본값 사용: videoId={}, fallback={}", videoId, fallback);
        return fallback;
    }

    private void updateLastSyncTime(String cursorKey, String videoId, LocalDateTime latestTime) {
        if (latestTime == null) {
            return;
        }
        String isoString = latestTime.format(CURSOR_FORMATTER);
        stringRedisTemplate.opsForValue().set(cursorKey, isoString);

        if (videoId != null && !videoId.isBlank()) {
            YoutubeCommentSyncCursorDto cursorDto = new YoutubeCommentSyncCursorDto();
            cursorDto.setVideoId(videoId);
            cursorDto.setLastSyncTime(latestTime);
            cursorMapper.upsert(cursorDto);
        }
    }

    private LocalDateTime readCursorFromRedis(String cursorKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(cursorKey);
            if (value == null || value.isBlank()) {
                return null;
            }
            return LocalDateTime.parse(value, CURSOR_FORMATTER);
        } catch (Exception e) {
            log.warn("커서 값을 파싱할 수 없습니다. key={}, error={}", cursorKey, e.getMessage());
            return null;
        }
    }

    private LocalDateTime readCursorFromDatabase(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return null;
        }
        YoutubeCommentSyncCursorDto cursor = cursorMapper.findByVideoId(videoId);
        return cursor != null ? cursor.getLastSyncTime() : null;
    }

    private LocalDateTime toLocalDateTime(DateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(dateTime.getValue()),
                YOUTUBE_TIME_ZONE);
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static class IncrementalFetchResult {
        private final int newCount;
        private final LocalDateTime latestPublishedAt;

        IncrementalFetchResult(int newCount, LocalDateTime latestPublishedAt) {
            this.newCount = newCount;
            this.latestPublishedAt = latestPublishedAt;
        }

        int getNewCount() {
            return newCount;
        }

        LocalDateTime getLatestPublishedAt() {
            return latestPublishedAt;
        }
    }

}
