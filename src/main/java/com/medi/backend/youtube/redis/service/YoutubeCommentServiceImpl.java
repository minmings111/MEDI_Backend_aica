package com.medi.backend.youtube.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadListResponse;
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
 * - 증분 동기화: video:{video_id}:comments:filter (필터링 작업용)
 * - Type: String
 * - Value: [{comment_id: "...", text_original: "...", ...}, ...]
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
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public long syncTop20VideoComments(Integer userId, Map<String, List<RedisYoutubeVideo>> videosByChannel, SyncOptions options) {
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
                            // 댓글 조회 및 저장 (옵션에 따라 제한 적용)
                            // Python 코드 참고: channel_comment_fetcher.py의 fetch_comments_for_video 메서드
                            // - 댓글이 비활성화된 경우(commentsDisabled) 처리
                            // - HttpError 예외 처리
                            commentCount = fetchAndSaveComments(yt, videoId, redisKey, options);
                            
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
                            throw saveException;  // 예외를 다시 던져서 상위 catch에서 처리
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
            throw new RuntimeException("syncTop20VideoComments failed", e);
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
                    
                    // 증분 동기화: video:{videoId}:comments:filter (필터링 작업용)
                    String redisKey = "video:" + videoId + ":comments:filter";
                    
                    // 기존 댓글 백업
                    String existingComments = stringRedisTemplate.opsForValue().get(redisKey);
                    
                    long commentCount = 0;
                    try {
                        // 댓글 조회 및 저장 (옵션에 따라 제한 적용)
                        commentCount = fetchAndSaveComments(yt, videoId, redisKey, options);
                        
                        if (commentCount == 0 && existingComments != null && !existingComments.isEmpty()) {
                            log.warn("댓글 조회 실패 또는 댓글 없음. 기존 댓글 복구: {}", videoId);
                            stringRedisTemplate.opsForValue().set(redisKey, existingComments);
                        }
                        
                        totalCommentCount += commentCount;
                        log.debug("영상 {}의 댓글 {}개 저장 완료", videoId, commentCount);
                    } catch (Exception saveException) {
                        if (existingComments != null && !existingComments.isEmpty()) {
                            log.warn("댓글 저장 실패. 기존 댓글 복구: {}", videoId);
                            stringRedisTemplate.opsForValue().set(redisKey, existingComments);
                        }
                        throw saveException;
                    }
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
     * 영상의 댓글을 조회하여 Redis에 저장
     * 
     * 변경사항:
     * - 기존: List로 개별 저장 (각 댓글을 List의 요소로 추가)
     * - 신규: 전체 댓글을 하나의 JSON 배열 문자열로 저장 (String 타입)
     * 
     * 참고: channel_comment_fetcher.py의 fetch_comments_for_video 메서드 구조를 참고하여 구현
     * - Python 코드: backend/src/main/java/com/medi/backend/youtube/redis/channel_comment_fetcher.py
     * 
     * 주요 참고 사항:
     * 1. 페이지네이션: nextPageToken을 사용하여 모든 댓글 수집 (Python: while True + nextPageToken)
     * 2. 에러 처리: commentsDisabled, disabledComments 등 특정 에러 케이스 처리
     * 3. maxResults: YouTube API 최대값 100 사용 (Python: page_size 1~100)
     * 4. order: "time" 사용 (Python: "relevance" | "time" 선택 가능)
     * 5. part: "snippet,replies" 사용 (Python: part="snippet,replies")
     * 
     * Redis 저장 형식:
     * - Key: video:{video_id}:comments:init 또는 video:{video_id}:comments:filter
     * - Type: String
     * - Value: [{comment_id: "...", text_original: "...", ...}, ...]
     * 
     * @param yt YouTube API 클라이언트 객체
     * @param videoId YouTube 영상 ID
     * @param redisKey Redis 저장 키 (예: "video:{videoId}:comments:init" 또는 "video:{videoId}:comments:filter")
     * @return 저장된 댓글 개수
     * @throws Exception YouTube API 호출 실패 시
     */
    private long fetchAndSaveComments(YouTube yt, String videoId, String redisKey, SyncOptions options) throws Exception {
        // 옵션에 따라 기본 또는 전체 메타데이터 사용
        boolean useFullMetadata = options != null && options.isIncludeFullMetadata();
        
        // 변경: 댓글을 List로 수집 (나중에 한 번에 JSON 배열로 변환)
        // 초기 동기화: RedisYoutubeComment 사용, 증분 동기화: RedisYoutubeCommentFull 사용
        List<Object> allComments = new ArrayList<>();
        String nextPageToken = null;
        
        // 댓글 개수 제한: 옵션에 따라 결정 (null이면 제한 없음)
        Integer maxCommentCount = options != null ? options.getMaxCommentCount() : null;

        // Python 코드 참고: while True 대신 do-while 사용 (최소 1번은 실행)
        do {
            // 댓글 개수 제한 체크 (제한이 설정된 경우에만)
            if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                break;
            }
            
            // ⭐ YouTube CommentThreads API 요청 생성
            // API 엔드포인트: youtube.commentThreads.list
            // 용도: 특정 영상의 댓글 스레드 조회 (최상위 댓글 + 대댓글)
            // Python 코드 참고: commentThreads().list(part="snippet,replies")
            YouTube.CommentThreads.List req = yt.commentThreads()
                .list(Arrays.asList("snippet", "replies"));  // snippet: 댓글 내용, 작성자 등 / replies: 대댓글
            req.setVideoId(videoId);
            
            // Python 코드 참고: order 파라미터 (기본값 "relevance", 여기서는 "time" 사용)
            // Python: order=self.order (choices: "relevance" | "time")
            req.setOrder("time");  // 시간순 정렬
            
            // Python 코드 참고: maxResults (Python: page_size, 기본값 100, 범위 1~100)
            // YouTube API 최대값 100 사용
            req.setMaxResults(100L);  // 한 페이지당 최대 100개 댓글
            
            // Python 코드 참고: pageToken=next_page_token
            if (nextPageToken != null) {
                req.setPageToken(nextPageToken);  // 페이지네이션: 다음 페이지 토큰 설정
            }

            // ⭐ 실제 YouTube CommentThreads API 호출 실행
            // 이 시점에서 YouTube 서버로 HTTP 요청이 전송됨
            CommentThreadListResponse resp = req.execute();

            // Python 코드 참고: items = response.get("items", [])
            if (resp.getItems() != null) {
                // Python 코드 참고: for thread in threads 루프
                for (CommentThread thread : resp.getItems()) {
                    // 댓글 개수 제한 체크 (제한이 설정된 경우에만)
                    if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                        break;
                    }
                    
                    Comment top = thread.getSnippet().getTopLevelComment();
                    
                    // 최상위 댓글 변환 및 리스트에 추가
                    // Python 코드 참고: extract_comment_info(thread)로 댓글 정보 추출
                    // 옵션에 따라 기본 또는 전체 메타데이터 사용
                    if (useFullMetadata) {
                        // totalReplyCount는 CommentThread의 snippet에서 가져옴
                        Long totalReplyCount = null;
                        if (thread.getSnippet().getTotalReplyCount() != null) {
                            totalReplyCount = thread.getSnippet().getTotalReplyCount().longValue();
                        }
                        RedisYoutubeCommentFull topComment = redisMapper.toRedisCommentFull(top, null, totalReplyCount);
                        if (topComment != null) {
                            allComments.add(topComment);
                            
                            // 댓글 개수 제한 체크 (제한이 설정된 경우에만)
                            if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                                break;
                            }
                        }
                    } else {
                        RedisYoutubeComment topComment = redisMapper.toRedisComment(top, null);
                        if (topComment != null) {
                            allComments.add(topComment);
                            
                            // 댓글 개수 제한 체크 (제한이 설정된 경우에만)
                            if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                                break;
                            }
                        }
                    }

                    // 대댓글 처리
                    // Python 코드 참고: replies도 동일하게 처리
                    if (thread.getReplies() != null 
                        && thread.getReplies().getComments() != null) {
                        for (Comment reply : thread.getReplies().getComments()) {
                            // 댓글 개수 제한 체크 (제한이 설정된 경우에만)
                            if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() >= maxCommentCount) {
                                break;
                            }
                            
                            // 옵션에 따라 기본 또는 전체 메타데이터 사용
                            if (useFullMetadata) {
                                // 대댓글은 totalReplyCount가 없음 (null 전달)
                                RedisYoutubeCommentFull replyComment = redisMapper.toRedisCommentFull(
                                    reply, top.getId(), null
                                );
                                if (replyComment != null) {
                                    allComments.add(replyComment);
                                }
                            } else {
                                RedisYoutubeComment replyComment = redisMapper.toRedisComment(
                                    reply, top.getId()
                                );
                                if (replyComment != null) {
                                    allComments.add(replyComment);
                                }
                            }
                        }
                    }
                }
            }

            // Python 코드 참고: next_page_token = response.get("nextPageToken")
            nextPageToken = resp.getNextPageToken();
            
            // Python 코드 참고: if not next_page_token: break
            // do-while의 조건문에서 처리됨
            // 댓글 개수 제한이 설정된 경우에만 제한 체크
        } while (nextPageToken != null && 
                 (maxCommentCount == null || maxCommentCount <= 0 || allComments.size() < maxCommentCount));
        
        // 제한 적용: 초과분 제거 (제한이 설정된 경우에만)
        if (maxCommentCount != null && maxCommentCount > 0 && allComments.size() > maxCommentCount) {
            allComments = allComments.subList(0, maxCommentCount);
        }

        // 변경: 전체 댓글을 하나의 JSON 배열 문자열로 저장
        if (!allComments.isEmpty()) {
            saveCommentsToRedis(redisKey, allComments);
        }

        return allComments.size();
    }

    /**
     * 댓글 리스트를 Redis에 JSON 배열 형식으로 저장
     * 
     * 변경사항:
     * - 기존: List에 개별 JSON 문자열로 저장 (각 댓글이 List의 요소)
     * - 신규: 전체를 하나의 JSON 배열 문자열로 저장 (String 타입)
     * 
     * 참고: channel_comment_fetcher.py의 데이터 저장 구조
     * - Python 코드: json.dump(comments_list, f) → 파일에 JSON 배열로 저장
     * - Java: Redis String에 JSON 배열 문자열로 저장
     * 
     * 저장 형식:
     * - Redis Key: video:{video_id}:comments:init 또는 video:{video_id}:comments:filter
     * - Redis Value Type: String
     * - Value 예시: [{"comment_id":"Ugy123","text_original":"좋은 영상",...}, {...}]
     * 
     * TTL 설정:
     * - 3일 후 자동 삭제 (만료)
     * 
     * @param redisKey Redis 저장 키
     * @param comments 저장할 댓글 리스트 (RedisYoutubeComment 또는 RedisYoutubeCommentFull)
     */
    private void saveCommentsToRedis(String redisKey, List<Object> comments) {
        try {
            // 전체 댓글 리스트를 하나의 JSON 배열 문자열로 변환
            // Python 코드: json.dump(comments_list, f)
            // Java: objectMapper.writeValueAsString(List) → "[{...}, {...}, ...]"
            String jsonArray = objectMapper.writeValueAsString(comments);
            
            // Redis에 String 타입으로 저장
            // 기존: opsForList().rightPush() (List 타입)
            // 신규: opsForValue().set() (String 타입)
            stringRedisTemplate.opsForValue().set(redisKey, jsonArray);
            
            // TTL 설정: 3일 후 자동 삭제
            stringRedisTemplate.expire(redisKey, Duration.ofDays(3));
            
            log.debug("댓글 {}개를 Redis에 저장 완료: key={}", comments.size(), redisKey);
        } catch (JsonProcessingException e) {
            log.error("댓글 리스트 직렬화 실패: key={}, size={}", redisKey, comments.size(), e);
            throw new RuntimeException("댓글 JSON 변환 실패", e);
        }
    }

}
