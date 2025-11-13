package com.medi.backend.youtube.redis.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.redis.dto.YoutubeVideo;
import com.medi.backend.youtube.redis.mapper.YoutubeVideoMapper;
import com.medi.backend.youtube.service.YoutubeOAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeVideoServiceImpl implements YoutubeVideoService {

    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeChannelMapper channelMapper;
    private final YoutubeVideoMapper redisMapper;

    @Override
    public Map<String, List<YoutubeVideo>> getTop20VideosByChannel(Integer userId) {
        try {
            // 1. OAuth 토큰 가져오기
            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = buildClient(token);

            // 2. 사용자의 등록된 채널 목록 조회 (DB에서 이미 저장된 채널 사용)
            List<YoutubeChannelDto> channels = channelMapper.findByUserId(userId);
            if (channels.isEmpty()) {
                log.warn("사용자 {}의 등록된 채널이 없습니다", userId);
                return Collections.emptyMap();
            }

            // 3. 각 채널마다 조회수 상위 20개 영상 수집
            Map<String, List<YoutubeVideo>> videosByChannel = new HashMap<>();
            
            for (YoutubeChannelDto channel : channels) {
                try {
                    String channelId = channel.getYoutubeChannelId();
                    
                    // 3-1. 채널의 영상 목록 조회
                    List<SearchResult> searchResults = fetchChannelVideos(yt, channelId);
                    
                    if (searchResults.isEmpty()) {
                        videosByChannel.put(channelId, Collections.emptyList());
                        continue;
                    }

                    // 3-2. 비디오 ID 목록 추출
                    List<String> videoIds = searchResults.stream()
                        .map(result -> result.getId().getVideoId())
                        .filter(id -> id != null)
                        .collect(Collectors.toList());

                    if (videoIds.isEmpty()) {
                        videosByChannel.put(channelId, Collections.emptyList());
                        continue;
                    }

                    // 3-3. 비디오 상세 정보 가져오기 (조회수 포함)
                    List<Video> videos = fetchVideoDetails(yt, videoIds);

                    // 3-4. Redis DTO로 변환 (channelId 전달)
                    List<YoutubeVideo> channelVideos = new ArrayList<>();
                    for (Video video : videos) {
                        YoutubeVideo redisVideo = redisMapper.toRedisVideo(video, channelId);
                        if (redisVideo != null) {
                            channelVideos.add(redisVideo);
                        }
                    }

                    // 3-5. 조회수 기준으로 정렬하여 상위 20개 선택
                    List<YoutubeVideo> top20Videos = channelVideos.stream()
                        .sorted(Comparator.comparing(
                            YoutubeVideo::getViewCount,
                            Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                        .limit(20)
                        .collect(Collectors.toList());

                    videosByChannel.put(channelId, top20Videos);
                    log.debug("채널 {}의 조회수 상위 20개 영상 조회 완료: {}개", channelId, top20Videos.size());
                    
                } catch (Exception e) {
                    log.error("채널 {}의 영상 조회 실패: {}", channel.getYoutubeChannelId(), e.getMessage());
                    videosByChannel.put(channel.getYoutubeChannelId(), Collections.emptyList());
                    // 한 채널 실패해도 다른 채널은 계속 처리
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

    private YouTube buildClient(String accessToken) throws Exception {
        return new YouTube.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("medi").build();
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
            YouTube.Videos.List req = yt.videos().list(
                Arrays.asList("snippet", "statistics")  // snippet: 제목, 썸네일 등 / statistics: 조회수, 좋아요 수 등
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
}
