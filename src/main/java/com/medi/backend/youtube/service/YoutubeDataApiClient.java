package com.medi.backend.youtube.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CaptionListResponse;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.CommentThreadListResponse;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.VideoListResponse;
import com.medi.backend.youtube.config.YoutubeDataApiProperties;
import com.medi.backend.youtube.exception.NoAvailableApiKeyException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class YoutubeDataApiClient {

    private final YoutubeDataApiProperties properties;
    private final AtomicInteger rotatingIndex = new AtomicInteger();
    private final YouTube youtube;

    public YoutubeDataApiClient(YoutubeDataApiProperties properties) {
        this.properties = properties;
        try {
            this.youtube = new YouTube.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> {
                    })
                    .setApplicationName("medi")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("YouTube Data API 클라이언트 초기화 실패", e);
        }
    }

    public boolean hasApiKeys() {
        return properties.getApiKeys() != null && properties.getApiKeys().stream().anyMatch(this::isUsableKey);
    }

    public PlaylistItemListResponse fetchPlaylistItems(String playlistId, String pageToken)
            throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.PlaylistItems.List request = youtube.playlistItems()
                    .list(List.of("snippet", "contentDetails"));
            request.setPlaylistId(playlistId);
            request.setMaxResults(50L);
            if (pageToken != null) {
                request.setPageToken(pageToken);
            }
            request.setKey(apiKey);
            return request.execute();
        });
    }

    public VideoListResponse fetchVideoStatistics(List<String> videoIds) throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.Videos.List request = youtube.videos().list(List.of("statistics"));
            request.setId(videoIds);
            request.setKey(apiKey);
            return request.execute();
        });
    }

    /**
     * 비디오 상세 정보 조회 (API 키 사용)
     * snippet, statistics, contentDetails 포함
     * 
     * @param videoIds 비디오 ID 리스트
     * @return 비디오 상세 정보 응답
     * @throws IOException API 호출 실패 시
     */
    public VideoListResponse fetchVideoDetails(List<String> videoIds) throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.Videos.List request = youtube.videos()
                    .list(List.of("snippet", "statistics", "contentDetails"));
            request.setId(videoIds);
            request.setKey(apiKey);
            return request.execute();
        });
    }

    public ChannelListResponse fetchChannelDetails(String channelId) throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.Channels.List request = youtube.channels()
                    .list(List.of("snippet", "contentDetails", "statistics"));
            request.setId(List.of(channelId));
            request.setKey(apiKey);
            return request.execute();
        });
    }

    /**
     * 댓글 스레드 조회 (API 키 사용)
     * 
     * @param videoId 비디오 ID
     * @param pageToken 페이지네이션 토큰 (null이면 첫 페이지)
     * @param maxResults 최대 결과 수 (기본 100)
     * @return 댓글 스레드 목록 응답
     * @throws IOException API 호출 실패 시
     */
    public CommentThreadListResponse fetchCommentThreads(String videoId, String pageToken, Long maxResults) 
            throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.CommentThreads.List request = youtube.commentThreads()
                    .list(List.of("snippet", "replies"));
            request.setVideoId(videoId);
            request.setOrder("time");
            if (maxResults != null) {
                request.setMaxResults(maxResults);
            } else {
                request.setMaxResults(100L);
            }
            if (pageToken != null) {
                request.setPageToken(pageToken);
            }
            request.setKey(apiKey);
            return request.execute();
        });
    }

    /**
     * 영상 검색 (API 키 사용)
     * 
     * @param channelId 채널 ID
     * @param pageToken 페이지네이션 토큰 (null이면 첫 페이지)
     * @param maxResults 최대 결과 수 (기본 50)
     * @return 검색 결과 목록 응답
     * @throws IOException API 호출 실패 시
     */
    public SearchListResponse fetchSearch(String channelId, String pageToken, Long maxResults) 
            throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.Search.List request = youtube.search().list(List.of("snippet"));
            request.setChannelId(channelId);
            request.setType(List.of("video"));
            request.setOrder("date");
            if (maxResults != null) {
                request.setMaxResults(maxResults);
            } else {
                request.setMaxResults(50L);
            }
            if (pageToken != null) {
                request.setPageToken(pageToken);
            }
            request.setKey(apiKey);
            return request.execute();
        });
    }

    /**
     * 자막 목록 조회 (API 키 사용)
     * 
     * @param videoId 비디오 ID
     * @return 자막 목록 응답
     * @throws IOException API 호출 실패 시
     */
    public CaptionListResponse fetchCaptions(String videoId) throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.Captions.List request = youtube.captions()
                    .list(List.of("snippet"), videoId);
            request.setKey(apiKey);
            return request.execute();
        });
    }

    private <T> T executeWithApiKey(ApiCall<T> call) throws IOException {
        List<String> validKeys = getValidKeys();
        if (validKeys.isEmpty()) {
            throw new NoAvailableApiKeyException("사용 가능한 YouTube Data API 키가 없습니다");
        }

        int start = Math.floorMod(rotatingIndex.getAndIncrement(), validKeys.size());
        log.info("🔄 API 키 rotation 시작: totalKeys={}, startIndex={}", validKeys.size(), start);
        
        for (int i = 0; i < validKeys.size(); i++) {
            int currentIndex = (start + i) % validKeys.size();
            String key = validKeys.get(currentIndex);
            String keyPreview = key != null && key.length() > 10 ? key.substring(0, 10) + "..." : key;
            
            log.info("🔑 API 키 시도 중: index={}/{}, key={}", currentIndex, validKeys.size() - 1, keyPreview);
            
            try {
                T result = call.execute(key);
                log.info("✅ API 키 사용 성공: index={}, key={}", currentIndex, keyPreview);
                return result;
            } catch (GoogleJsonResponseException e) {
                if (isQuotaError(e)) {
                    String reason = extractReason(e);
                    log.warn("⚠️ YouTube Data API quota 소진: key index={}/{}, reason={}, key={}, 다음 키로 시도...", 
                        currentIndex, validKeys.size() - 1, reason, keyPreview);
                    
                    // 마지막 키인 경우
                    if (i == validKeys.size() - 1) {
                        log.error("❌ 모든 YouTube Data API 키의 할당량이 소진되었습니다! (총 {}개 키 모두 시도)", validKeys.size());
                    } else {
                        log.info("⏭️ 다음 API 키로 시도: {}/{}", i + 1, validKeys.size());
                    }
                    continue;
                }
                // quota 에러가 아닌 다른 403 에러는 그대로 던지기
                log.error("❌ API 키 사용 실패 (quota 이외의 에러): index={}, key={}, status={}, reason={}", 
                    currentIndex, keyPreview, e.getStatusCode(), extractReason(e));
                throw e;
            }
        }
        throw new NoAvailableApiKeyException("모든 YouTube Data API 키의 할당량이 소진되었습니다");
    }

    private List<String> getValidKeys() {
        if (properties.getApiKeys() == null) {
            return List.of();
        }
        return properties.getApiKeys().stream()
                .filter(this::isUsableKey)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isUsableKey(String key) {
        return key != null && !key.isBlank();
    }

    private boolean isQuotaError(GoogleJsonResponseException e) {
        if (e.getStatusCode() != 403 || e.getDetails() == null || e.getDetails().getErrors() == null) {
            return false;
        }
        return e.getDetails().getErrors().stream()
                .map(err -> err.getReason())
                .filter(Objects::nonNull)
                .anyMatch(reason -> reason.equals("quotaExceeded")
                        || reason.equals("dailyLimitExceeded")
                        || reason.equals("userRateLimitExceeded"));
    }

    private String extractReason(GoogleJsonResponseException e) {
        if (e.getDetails() == null || e.getDetails().getErrors() == null) {
            return null;
        }
        return e.getDetails().getErrors().stream()
                .map(err -> err.getReason())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T execute(String apiKey) throws IOException;
    }
}

