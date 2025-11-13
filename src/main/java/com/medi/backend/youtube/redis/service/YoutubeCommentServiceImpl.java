package com.medi.backend.youtube.redis.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadListResponse;
import com.medi.backend.youtube.redis.dto.YoutubeComment;
import com.medi.backend.youtube.redis.mapper.YoutubeCommentMapper;
import com.medi.backend.youtube.service.YoutubeOAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeCommentServiceImpl implements YoutubeCommentService {

    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeVideoService videoService;
    private final YoutubeCommentMapper redisMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public long syncTop20VideoComments(Integer userId) {
        try {
            // 1. 각 채널마다 조회수 상위 20개 영상 조회
            Map<String, List<com.medi.backend.youtube.redis.dto.YoutubeVideo>> videosByChannel = 
                videoService.getTop20VideosByChannel(userId);

            if (videosByChannel.isEmpty()) {
                log.warn("조회수 상위 20개 영상이 없습니다: userId={}", userId);
                return 0;
            }

            // 2. OAuth 토큰 가져오기
            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = buildClient(token);

            long totalCommentCount = 0;

            // 3. 각 채널의 상위 20개 영상의 댓글 조회 및 Redis 저장
            for (Map.Entry<String, List<com.medi.backend.youtube.redis.dto.YoutubeVideo>> entry : videosByChannel.entrySet()) {
                String channelId = entry.getKey();
                List<com.medi.backend.youtube.redis.dto.YoutubeVideo> videos = entry.getValue();
                
                log.debug("채널 {}의 {}개 영상 댓글 조회 시작", channelId, videos.size());
                
                for (com.medi.backend.youtube.redis.dto.YoutubeVideo video : videos) {
                    try {
                        String videoId = video.getYoutubeVideoId();
                        
                        // 보안: videoId null 체크 및 빈 문자열 검증
                        if (videoId == null || videoId.isBlank()) {
                            log.warn("영상 ID가 없습니다. 건너뜁니다: {}", video);
                            continue;
                        }
                        
                        String redisKey = "video:" + videoId + ":comments";
                        
                        // 부분 실패 방지: 기존 댓글 백업
                        List<String> existingComments = stringRedisTemplate.opsForList().range(redisKey, 0, -1);
                        
                        long commentCount = 0;
                        try {
                            // 기존 댓글 삭제 (덮어쓰기)
                            stringRedisTemplate.delete(redisKey);

                            // 댓글 조회 및 저장
                            // Python 코드 참고: channel_comment_fetcher.py의 fetch_comments_for_video 메서드
                            // - 댓글이 비활성화된 경우(commentsDisabled) 처리
                            // - HttpError 예외 처리
                            commentCount = fetchAndSaveComments(yt, videoId, redisKey);
                            
                            // 부분 실패 처리: 새 댓글이 없고 기존 댓글이 있었으면 복구
                            if (commentCount == 0 && existingComments != null && !existingComments.isEmpty()) {
                                log.warn("댓글 조회 실패 또는 댓글 없음. 기존 댓글 복구: {}", videoId);
                                for (String comment : existingComments) {
                                    stringRedisTemplate.opsForList().rightPush(redisKey, comment);
                                }
                            }
                            
                            totalCommentCount += commentCount;
                            log.debug("영상 {}의 댓글 {}개 저장 완료", videoId, commentCount);
                        } catch (Exception saveException) {
                            // 부분 실패 처리: 저장 실패 시 기존 댓글 복구
                            if (existingComments != null && !existingComments.isEmpty()) {
                                log.warn("댓글 저장 실패. 기존 댓글 복구: {}", videoId);
                                for (String comment : existingComments) {
                                    stringRedisTemplate.opsForList().rightPush(redisKey, comment);
                                }
                            }
                            throw saveException;  // 예외를 다시 던져서 상위 catch에서 처리
                        }
                    } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                        // Python 코드 참고: HttpError 예외 처리
                        // Python: if reason in {"commentsDisabled", "disabledComments"}
                        String errorReason = extractErrorReason(e);
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

    private YouTube buildClient(String accessToken) throws Exception {
        return new YouTube.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("medi").build();
    }

    /**
     * 영상의 댓글을 조회하여 Redis에 저장
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
     * @param yt YouTube API 클라이언트 객체
     * @param videoId YouTube 영상 ID
     * @param redisKey Redis 저장 키 (예: "video:{videoId}:comments")
     * @return 저장된 댓글 개수
     * @throws Exception YouTube API 호출 실패 시
     */
    private long fetchAndSaveComments(YouTube yt, String videoId, String redisKey) throws Exception {
        long count = 0;
        String nextPageToken = null;

        // Python 코드 참고: while True 대신 do-while 사용 (최소 1번은 실행)
        do {
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
                    Comment top = thread.getSnippet().getTopLevelComment();
                    
                    // 최상위 댓글 변환 및 저장
                    // Python 코드 참고: extract_comment_info(thread)로 댓글 정보 추출
                    YoutubeComment topComment = redisMapper.toRedisComment(top, null);
                    if (topComment != null) {
                        saveCommentToRedis(redisKey, topComment);
                        count++;
                    }

                    // 대댓글 처리
                    // Python 코드 참고: replies도 동일하게 처리
                    if (thread.getReplies() != null 
                        && thread.getReplies().getComments() != null) {
                        for (Comment reply : thread.getReplies().getComments()) {
                            YoutubeComment replyComment = redisMapper.toRedisComment(
                                reply, top.getId()
                            );
                            if (replyComment != null) {
                                saveCommentToRedis(redisKey, replyComment);
                                count++;
                            }
                        }
                    }
                }
            }

            // Python 코드 참고: next_page_token = response.get("nextPageToken")
            nextPageToken = resp.getNextPageToken();
            
            // Python 코드 참고: if not next_page_token: break
            // do-while의 조건문에서 처리됨
        } while (nextPageToken != null);

        // TTL 설정 및 리스트 크기 제한
        if (count > 0) {
            stringRedisTemplate.expire(redisKey, Duration.ofDays(3));
            stringRedisTemplate.opsForList().trim(redisKey, 0, 999);
        }

        return count;
    }

    /**
     * 댓글을 Redis에 저장 (JSON 형식)
     * 
     * 참고: channel_comment_fetcher.py의 데이터 저장 구조
     * - Python 코드는 JSON 파일로 저장하지만, 여기서는 Redis List에 저장
     * - Python: _save_per_video 메서드에서 JSON 파일로 저장
     * - Java: Redis List에 JSON 문자열로 저장 (key: "video:{videoId}:comments")
     * 
     * 저장 형식:
     * - Redis Key: "video:{videoId}:comments"
     * - Redis Value Type: List
     * - List 요소: JSON 문자열 (YoutubeComment 객체를 JSON으로 직렬화)
     * 
     * @param redisKey Redis 저장 키
     * @param comment 저장할 댓글 객체
     */
    private void saveCommentToRedis(String redisKey, YoutubeComment comment) {
        try {
            // Python 코드 참고: json.dump(data, f) → Java: objectMapper.writeValueAsString()
            String json = objectMapper.writeValueAsString(comment);
            // Python 코드 참고: 파일 저장 → Java: Redis List에 저장
            stringRedisTemplate.opsForList().rightPush(redisKey, json);
        } catch (JsonProcessingException e) {
            log.error("댓글 직렬화 실패: {}", comment, e);
        }
    }

    /**
     * Google API 에러 응답에서 에러 원인(reason) 추출
     * 
     * 참고: channel_comment_fetcher.py의 _extract_error_reason 메서드
     * Python 코드:
     *   data = json.loads(error.content.decode("utf-8"))
     *   errors = data.get("error", {}).get("errors", [])
     *   return errors[0].get("reason", "")
     * 
     * @param e GoogleJsonResponseException 예외 객체
     * @return 에러 원인 문자열 (예: "commentsDisabled", "disabledComments")
     */
    private String extractErrorReason(com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
        try {
            com.google.api.client.googleapis.json.GoogleJsonError error = e.getDetails();
            if (error != null && error.getErrors() != null && !error.getErrors().isEmpty()) {
                com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo firstError = 
                    error.getErrors().get(0);
                if (firstError != null) {
                    return firstError.getReason();
                }
            }
        } catch (Exception ex) {
            log.debug("에러 원인 추출 실패: {}", ex.getMessage());
        }
        return "";
    }
}
