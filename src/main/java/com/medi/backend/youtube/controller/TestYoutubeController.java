package com.medi.backend.youtube.controller;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.mapper.YoutubeVideoMapper;
import com.medi.backend.youtube.redis.dto.RedisSyncResult;
import com.medi.backend.youtube.redis.service.YoutubeRedisSyncService;
import com.medi.backend.youtube.service.YoutubeDataApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 테스트용으로 YouTube Data API를 직접 호출해 채널/영상 정보를 저장하는 컨트롤러.
 * OAuth 토큰과 별개로 API Key 기반 호출만으로 DB 저장이 되는지 확인할 때 사용한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/test/youtube")
@RequiredArgsConstructor
public class TestYoutubeController {

    private final YoutubeDataApiClient youtubeDataApiClient;
    private final YoutubeChannelMapper channelMapper;
    private final YoutubeVideoMapper videoMapper;
    private final YoutubeRedisSyncService youtubeRedisSyncService;

    @PostMapping("/save")
    public ResponseEntity<?> saveChannelAndVideos(
            @RequestParam(value = "channelId", required = false) String channelId,
            @RequestParam(value = "userId", defaultValue = "1") Integer userId,
            @RequestParam(value = "maxResults", defaultValue = "10") Integer maxResults) {
        try {
            String targetChannelId = (channelId == null || channelId.isBlank())
                    ? "UCSe8ABLP0nCsEAvaQYm6GcA" : channelId.trim();

            ChannelListResponse channelResponse = youtubeDataApiClient.fetchChannelDetails(targetChannelId);
            if (channelResponse.getItems() == null || channelResponse.getItems().isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", "채널을 찾을 수 없습니다.",
                        "channelId", targetChannelId
                ));
            }

            Channel channel = channelResponse.getItems().get(0);
            YoutubeChannelDto channelDto = mapChannel(userId, channel);
            channelMapper.upsert(channelDto);

            YoutubeChannelDto savedChannel = channelMapper.findByYoutubeChannelId(channelDto.getYoutubeChannelId());
            if (savedChannel == null) {
                return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "채널 저장 결과를 조회하지 못했습니다."
                ));
            }

            if (savedChannel.getUploadsPlaylistId() == null) {
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "message", "채널에 업로드 플레이리스트가 없습니다.",
                        "channelId", targetChannelId
                ));
            }

            PlaylistItemListResponse playlistResponse = youtubeDataApiClient.fetchPlaylistItems(savedChannel.getUploadsPlaylistId(), null);
            if (playlistResponse.getItems() == null || playlistResponse.getItems().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "채널은 저장되었지만 영상이 없습니다.",
                        "channel", savedChannel
                ));
            }

            List<PlaylistItem> playlistItems = playlistResponse.getItems().stream()
                    .filter(Objects::nonNull)
                    .limit(Math.max(1, maxResults))
                    .collect(Collectors.toList());

            List<String> videoIds = playlistItems.stream()
                    .map(item -> item.getContentDetails() != null ? item.getContentDetails().getVideoId() : null)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());

            Map<String, Video> statisticsMap = new HashMap<>();
            if (!CollectionUtils.isEmpty(videoIds)) {
                VideoListResponse statsResponse = youtubeDataApiClient.fetchVideoStatistics(videoIds);
                if (statsResponse.getItems() != null) {
                    statisticsMap = statsResponse.getItems().stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(Video::getId, v -> v));
                }
            }

            for (PlaylistItem item : playlistItems) {
                if (item.getContentDetails() == null) {
                    continue;
                }
                String videoId = item.getContentDetails().getVideoId();
                if (videoId == null || videoId.isBlank()) {
                    continue;
                }

                YoutubeVideoDto videoDto = mapVideo(savedChannel.getId(), item, statisticsMap.get(videoId));
                videoMapper.upsert(videoDto);
            }

            List<YoutubeVideoDto> savedVideos = videoMapper.findByChannelId(savedChannel.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "채널/영상 저장 테스트가 완료되었습니다.",
                    "channel", savedChannel,
                    "videos", savedVideos
            ));
        } catch (Exception e) {
            log.error("테스트 저장 중 오류", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkSaved(@RequestParam(value = "userId", defaultValue = "1") Integer userId) {
        List<YoutubeChannelDto> channels = channelMapper.findByUserId(userId);
        List<Map<String, Object>> summary = channels.stream()
                .map(channel -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("channelId", channel.getId());
                    map.put("channelName", channel.getChannelName());
                    map.put("youtubeChannelId", channel.getYoutubeChannelId());
                    map.put("videoCount", videoMapper.findByChannelId(channel.getId()).size());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "channels", summary
        ));
    }

    @PostMapping("/redis/sync")
    public ResponseEntity<?> testRedisSync(@RequestParam("userId") Integer userId) {
        try {
            log.info("Redis 동기화 테스트 시작: userId={}", userId);
            RedisSyncResult result = youtubeRedisSyncService.syncToRedis(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Redis 동기화 완료",
                "result", Map.of(
                    "channelCount", result.getChannelCount(),
                    "videoCount", result.getVideoCount(),
                    "commentCount", result.getCommentCount(),
                    "success", result.isSuccess(),
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : ""
                )
            ));
        } catch (Exception e) {
            log.error("Redis 동기화 테스트 실패: userId={}", userId, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    private YoutubeChannelDto mapChannel(Integer userId, Channel channel) {
        YoutubeChannelDto dto = new YoutubeChannelDto();
        dto.setUserId(userId);
        dto.setOauthTokenId(null);
        dto.setYoutubeChannelId(channel.getId());
        if (channel.getSnippet() != null) {
            dto.setChannelName(channel.getSnippet().getTitle());
            dto.setChannelHandle(channel.getSnippet().getCustomUrl());
            if (channel.getSnippet().getThumbnails() != null
                    && channel.getSnippet().getThumbnails().getDefault() != null) {
                dto.setThumbnailUrl(channel.getSnippet().getThumbnails().getDefault().getUrl());
            }
        }
        if (channel.getContentDetails() != null && channel.getContentDetails().getRelatedPlaylists() != null) {
            dto.setUploadsPlaylistId(channel.getContentDetails().getRelatedPlaylists().getUploads());
        }
        dto.setLastSyncedAt(LocalDateTime.now());
        dto.setLastVideoPublishedAt(null);
        return dto;
    }

    private YoutubeVideoDto mapVideo(Integer channelId, PlaylistItem item, Video statistics) {
        YoutubeVideoDto dto = new YoutubeVideoDto();
        dto.setChannelId(channelId);
        dto.setYoutubeVideoId(item.getContentDetails().getVideoId());
        if (item.getSnippet() != null) {
            dto.setTitle(item.getSnippet().getTitle());
            if (item.getSnippet().getThumbnails() != null
                    && item.getSnippet().getThumbnails().getDefault() != null) {
                dto.setThumbnailUrl(item.getSnippet().getThumbnails().getDefault().getUrl());
            }
            dto.setPublishedAt(toLocalDateTime(item.getContentDetails().getVideoPublishedAt()));
        }

        if (statistics != null && statistics.getStatistics() != null) {
            if (statistics.getStatistics().getViewCount() != null) {
                dto.setViewCount(statistics.getStatistics().getViewCount().longValue());
            }
            if (statistics.getStatistics().getLikeCount() != null) {
                dto.setLikeCount(statistics.getStatistics().getLikeCount().longValue());
            }
            if (statistics.getStatistics().getCommentCount() != null) {
                dto.setCommentCount(statistics.getStatistics().getCommentCount().longValue());
            }
        }
        return dto;
    }

    private LocalDateTime toLocalDateTime(DateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(dateTime.getValue());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

