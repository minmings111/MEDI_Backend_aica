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
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
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

    public ChannelListResponse fetchChannelDetails(String channelId) throws IOException {
        return executeWithApiKey(apiKey -> {
            YouTube.Channels.List request = youtube.channels()
                    .list(List.of("snippet", "contentDetails", "statistics"));
            request.setId(List.of(channelId));
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
        for (int i = 0; i < validKeys.size(); i++) {
            String key = validKeys.get((start + i) % validKeys.size());
            try {
                return call.execute(key);
            } catch (GoogleJsonResponseException e) {
                if (isQuotaError(e)) {
                    log.warn("YouTube Data API quota 소진: key index={} reason={}", (start + i) % validKeys.size(), extractReason(e));
                    continue;
                }
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

