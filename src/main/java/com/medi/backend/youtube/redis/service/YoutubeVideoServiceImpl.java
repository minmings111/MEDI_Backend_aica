package com.medi.backend.youtube.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;
import com.medi.backend.youtube.redis.dto.SyncOptions;
import com.medi.backend.youtube.redis.mapper.YoutubeVideoMapper;
import com.medi.backend.youtube.redis.util.YoutubeApiClientUtil;
import com.medi.backend.youtube.service.YoutubeOAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * YouTube 비디오 정보 조회 및 Redis 저장 서비스 구현체
 * 
 * 주요 기능:
 * 1. 사용자의 채널별 조회수 상위 20개 영상 조회
 * 2. 채널별 Top20 비디오 ID Set 저장 (Redis Set)
 * 3. 개별 비디오 메타데이터 저장 (Redis String, JSON 형식)
 * 
 * Redis 저장 형식:
 * 1. channel:{channel_id}:top20_video_ids (Set 타입) - 비디오 ID 목록
 * 2. video:{video_id}:meta:json (String 타입) - 비디오 메타데이터
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeVideoServiceImpl implements YoutubeVideoService {

    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeVideoMapper redisMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, List<RedisYoutubeVideo>> getTop20VideosByChannel(Integer userId, List<String> channelIds) {
        try {
            // 1. 채널 ID 리스트 검증
            if (channelIds == null || channelIds.isEmpty()) {
                log.warn("채널 ID 리스트가 비어있습니다: userId={}", userId);
                return Collections.emptyMap();
            }

            // 2. make a YouTube API Client
            YouTube yt = YoutubeApiClientUtil.buildClientForUser(youtubeOAuthService, userId);

            // 3. each channel, get the top 20 videos by view count
            Map<String, List<RedisYoutubeVideo>> videosByChannel = new HashMap<>();
            
            for (String channelId : channelIds) {
                try {
                    if (channelId == null || channelId.isBlank()) {
                        log.warn("유효하지 않은 채널 ID: {}", channelId);
                        continue;
                    }
                    
                    // 3-1. get the list of videos of the channel from YouTube API
                    List<SearchResult> searchResults = fetchChannelVideos(yt, channelId);
                    
                    if (searchResults.isEmpty()) {
                        videosByChannel.put(channelId, Collections.emptyList());
                        continue;
                    }

                    // 3-2. extract the list of video IDs
                    List<String> videoIds = searchResults.stream()
                        .map(result -> result.getId().getVideoId())
                        .filter(id -> id != null)
                        .collect(Collectors.toList());

                    if (videoIds.isEmpty()) {
                        videosByChannel.put(channelId, Collections.emptyList());
                        continue;
                    }

                    // 3-3. get the details of the videos from YouTube API (include view count)
                    List<Video> videos = fetchVideoDetails(yt, videoIds);

                    // 3-4. 쇼츠 제거 및 조회수 순으로 정렬하여 상위 20개 선택
                    List<Video> top20Videos = videos.stream()
                        .filter(video -> !isShortsVideo(video))  // 쇼츠 제거
                        .sorted(Comparator.comparing(
                            video -> {
                                if (video.getStatistics() != null && video.getStatistics().getViewCount() != null) {
                                    return video.getStatistics().getViewCount().longValue();
                                }
                                return 0L;
                            },
                            Comparator.reverseOrder()  // 조회수 내림차순 정렬
                        ))
                        .limit(20)  // 조회수 순으로 20개만 선택
                        .collect(Collectors.toList());

                    // 3-5. convert the videos to Redis DTO (pass channelId, only basic metadata)
                    List<RedisYoutubeVideo> channelVideos = new ArrayList<>();
                    for (Video video : top20Videos) {
                        RedisYoutubeVideo redisVideo = redisMapper.toRedisVideo(video, channelId);
                        if (redisVideo != null) {
                            channelVideos.add(redisVideo);
                        }
                    }

                    videosByChannel.put(channelId, channelVideos);
                    
                    // 3-6. save the top 20 video IDs and video metadata to Redis
                    saveTop20VideoIdsToRedis(channelId, channelVideos);
                    saveVideoMetadataToRedis(channelVideos);
                    
                    log.debug("채널 {}의 조회수 상위 20개 영상 조회 및 Redis 저장 완료: {}개", channelId, channelVideos.size());
                    
                } catch (Exception e) {
                    log.error("채널 {}의 영상 조회 실패: {}", channelId, e.getMessage());
                    videosByChannel.put(channelId, Collections.emptyList());
                    // if one channel fails, continue processing other channels
                }
            }

            log.info("사용자 {}의 각 채널별 조회수 상위 20개 영상 조회 완료: {}개 채널", 
                userId, videosByChannel.size());
            return videosByChannel;

        } catch (Exception e) {
            log.error("각 채널별 조회수 상위 20개 영상 조회 실패: userId={}", userId, e);
            throw new RuntimeException("getTop20VideosByChannel failed", e);
        }
    }


    /**
     * 채널의 영상 목록 조회 (social-comment-saas 방식: youtube.search.list 사용)
     */
    private List<SearchResult> fetchChannelVideos(YouTube yt, String channelId) throws Exception {
        List<SearchResult> allResults = new ArrayList<>();
        String nextPageToken = null;

        do {
            // ⭐ YouTube Search API 요청 생성
            // API 엔드포인트: youtube.search.list
            // 용도: 특정 채널의 모든 영상 목록 조회 (비디오 ID만)
            YouTube.Search.List searchReq = yt.search().list(Arrays.asList("snippet"));
            searchReq.setChannelId(channelId);
            searchReq.setMaxResults(50L);
            searchReq.setOrder("date");
            searchReq.setType(Arrays.asList("video"));
            
            if (nextPageToken != null) {
                searchReq.setPageToken(nextPageToken);
            }

            // ⭐ 실제 YouTube Search API 호출 실행
            // 이 시점에서 YouTube 서버로 HTTP 요청이 전송됨
            SearchListResponse response = searchReq.execute();
            
            if (response.getItems() != null) {
                allResults.addAll(response.getItems());
            }

            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null);

        return allResults;
    }

    /**
     * 비디오 상세 정보 조회 (조회수 등 통계 포함)
     */
    private List<Video> fetchVideoDetails(YouTube yt, List<String> videoIds) throws Exception {
        List<Video> videos = new ArrayList<>();

        // YouTube API는 한 번에 최대 50개까지만 조회 가능
        for (int i = 0; i < videoIds.size(); i += 50) {
            int end = Math.min(i + 50, videoIds.size());
            List<String> batch = videoIds.subList(i, end);

            // ⭐ YouTube Videos API 요청 생성
            // API 엔드포인트: youtube.videos.list
            // 용도: 비디오 ID 목록으로 상세 정보 조회 (조회수, 좋아요 수 등 통계 포함)
            // contentDetails: 비디오 길이(duration) 정보 포함 (쇼츠 필터링용)
            YouTube.Videos.List req = yt.videos().list(
                Arrays.asList("snippet", "statistics", "contentDetails")  // snippet: 제목, 썸네일 등 / statistics: 조회수, 좋아요 수 등 / contentDetails: 비디오 길이
            );
            req.setId(batch);
            
            // ⭐ 실제 YouTube Videos API 호출 실행
            // 이 시점에서 YouTube 서버로 HTTP 요청이 전송됨
            VideoListResponse resp = req.execute();

            if (resp.getItems() != null) {
                videos.addAll(resp.getItems());
            }
        }

        return videos;
    }
    
    /**
     * 비디오가 쇼츠인지 확인
     * 
     * 쇼츠 판단 기준: 비디오 길이가 30분 30초(1830초) 미만인 경우
     * 
     * @param video YouTube API Video 객체
     * @return 쇼츠이면 true, 아니면 false
     */
    private boolean isShortsVideo(Video video) {
        if (video == null || video.getContentDetails() == null) {
            return false;
        }
        
        String duration = video.getContentDetails().getDuration();
        if (duration == null || duration.isBlank()) {
            return false;
        }
        
        // ISO 8601 duration 형식 파싱 (예: "PT1M30S" = 1분 30초, "PT30M30S" = 30분 30초)
        // 쇼츠 판단 기준: 30분 30초(1830초) 미만
        try {
            long totalSeconds = parseDurationToSeconds(duration);
            return totalSeconds > 0 && totalSeconds < 1830;  // 30분 30초(1830초) 미만이면 쇼츠
        } catch (Exception e) {
            log.warn("비디오 duration 파싱 실패: videoId={}, duration={}", video.getId(), duration, e);
            return false;
        }
    }
    

    // ISO 8601 duration -> seconds
    private long parseDurationToSeconds(String duration) {
        if (duration == null || duration.isBlank()) {
            return 0;
        }
        
        try {
            // java.time.Duration.parse()를 사용하여 ISO 8601 duration 형식 파싱
            Duration parsedDuration = Duration.parse(duration);
            return parsedDuration.getSeconds();
        } catch (Exception e) {
            log.warn("ISO 8601 duration 파싱 실패: duration={}", duration, e);
            return 0;
        }
    }
    
    /**
     * 특정 비디오들의 메타데이터를 조회하여 Redis에 저장 (증분 동기화용)
     * 
     * API 호출 최소화:
     * - 비디오 ID 리스트를 50개씩 배치로 묶어서 한 번에 조회
     * - 이미 조회한 Video 객체를 재사용하여 추가 API 호출 방지
     * 
     * 주의: 초기 동기화와 증분 동기화 모두 기본 메타데이터만 저장합니다.
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param videoIds 비디오 ID 리스트
     * @param options 동기화 옵션 (사용하지 않음, 기본 메타데이터만 저장)
     * @return 저장된 비디오 개수
     */
    @Override
    public int syncVideoMetadata(Integer userId, List<String> videoIds, SyncOptions options) {
        try {
            if (videoIds == null || videoIds.isEmpty()) {
                log.warn("비디오 ID 리스트가 비어있습니다: userId={}", userId);
                return 0;
            }
            
            // OAuth 토큰 가져오기
            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = YoutubeApiClientUtil.buildClient(token);
            
            // 비디오 상세 정보 조회 (배치 처리로 API 호출 최소화)
            // ⭐ YouTube Videos API 호출: 50개씩 묶어서 한 번에 조회
            List<Video> videos = fetchVideoDetails(yt, videoIds);
            
            if (videos.isEmpty()) {
                log.warn("비디오 상세 정보를 가져올 수 없습니다: userId={}, videoIds={}", userId, videoIds.size());
                return 0;
            }
            
            // 채널 ID 추출 (비디오에서 가져오기)
            Map<String, String> videoIdToChannelId = new HashMap<>();
            for (Video video : videos) {
                if (video.getSnippet() != null && video.getSnippet().getChannelId() != null) {
                    videoIdToChannelId.put(video.getId(), video.getSnippet().getChannelId());
                }
            }
            
            // 기본 메타데이터 DTO로 변환 (초기/증분 모두 동일)
            List<RedisYoutubeVideo> redisVideos = new ArrayList<>();
            for (Video video : videos) {
                String channelId = videoIdToChannelId.get(video.getId());
                RedisYoutubeVideo redisVideo = redisMapper.toRedisVideo(video, channelId);
                if (redisVideo != null) {
                    redisVideos.add(redisVideo);
                }
            }
            
            // Redis에 저장 (기본 메타데이터만)
            saveVideoMetadataToRedis(redisVideos);
            
            log.info("비디오 메타데이터 동기화 완료: userId={}, 비디오={}개", userId, redisVideos.size());
            return redisVideos.size();
            
        } catch (Exception e) {
            log.error("비디오 메타데이터 동기화 실패: userId={}", userId, e);
            throw new RuntimeException("syncVideoMetadata failed", e);
        }
    }

    /**
     * 채널별 Top20 비디오 ID Set을 Redis에 저장
     * 
     * Redis 저장 형식:
     * - Key: channel:{channel_id}:top20_video_ids
     * - Type: Set
     * - Value: 비디오 ID 목록 (예: ["td7kfwpTDcA", "o6Ju5r82EwA", ...])
     * 
     * 저장 방식:
     * 1. 기존 Set 삭제 (덮어쓰기)
     * 2. 새로운 비디오 ID들을 Set에 추가
     * 3. TTL 설정 (3일)
     * 
     * Set 타입 사용 이유:
     * - 중복 제거
     * - O(1) 시간 복잡도로 특정 비디오 ID 존재 여부 확인 가능
     * - AI 서버에서 빠르게 Top20 비디오 목록 조회 가능
     * 
     * @param channelId YouTube 채널 ID
     * @param top20Videos 상위 20개 비디오 리스트
     */
    private void saveTop20VideoIdsToRedis(String channelId, List<RedisYoutubeVideo> top20Videos) {
        if (top20Videos.isEmpty()) {
            return;
        }

        try {
            String setKey = "channel:" + channelId + ":top20_video_ids";
            
            // 1. 기존 Set 삭제 (덮어쓰기)
            stringRedisTemplate.delete(setKey);
            
            // 2. 새로운 비디오 ID들을 Set에 추가
            // SADD channel:{channel_id}:top20_video_ids "video_id_1" "video_id_2" ...
            for (RedisYoutubeVideo video : top20Videos) {
                if (video.getYoutubeVideoId() != null) {
                    stringRedisTemplate.opsForSet().add(setKey, video.getYoutubeVideoId());
                }
            }
            
            // 3. TTL 설정: 3일 후 자동 삭제
            stringRedisTemplate.expire(setKey, Duration.ofDays(3));
            
            log.debug("채널 {}의 Top20 비디오 ID Set 저장 완료: {}개", channelId, top20Videos.size());
        } catch (Exception e) {
            log.error("채널 {}의 Top20 비디오 ID Set 저장 실패", channelId, e);
            // 저장 실패해도 진행 (비지니스 로직에 영향 없음)
        }
    }

    /**
     * 개별 비디오 메타데이터를 Redis에 저장
     * 
     * Redis 저장 형식:
     * - Key: video:{video_id}:meta:json
     * - Type: String (JSON)
     * - Value: {channel_id, video_id, video_title, video_tags}
     * 
     * 저장 방식:
     * - 기본 메타데이터만 저장 (초기/증분 동기화 모두 동일)
     * - RedisYoutubeVideo DTO 객체를 직접 JSON으로 직렬화
     * - @JsonProperty를 통해 스네이크 케이스로 자동 변환
     * 
     * TTL 설정:
     * - 3일 후 자동 삭제 (만료)
     * 
     * @param videos 저장할 비디오 리스트 (기본 메타데이터)
     */
    private void saveVideoMetadataToRedis(List<RedisYoutubeVideo> videos) {
        for (RedisYoutubeVideo video : videos) {
            try {
                String videoId = video.getYoutubeVideoId();
                if (videoId == null || videoId.isBlank()) {
                    continue;
                }

                String metaKey = "video:" + videoId + ":meta:json";
                
                // 기본 메타데이터만 저장 (초기/증분 동기화 모두 동일)
                String metaJson = objectMapper.writeValueAsString(video);
                
                // Redis에 String 타입으로 저장
                stringRedisTemplate.opsForValue().set(metaKey, metaJson);
                
                // TTL 설정: 3일 후 자동 삭제
                stringRedisTemplate.expire(metaKey, Duration.ofDays(3));
                
                log.debug("비디오 {} 메타데이터 저장 완료", videoId);
            } catch (JsonProcessingException e) {
                log.error("비디오 {} 메타데이터 직렬화 실패", video.getYoutubeVideoId(), e);
                // 저장 실패해도 진행 (비지니스 로직에 영향 없음)
            } catch (Exception e) {
                log.error("비디오 {} 메타데이터 저장 실패", video.getYoutubeVideoId(), e);
                // 저장 실패해도 진행 (비지니스 로직에 영향 없음)
            }
        }
    }
    
}

