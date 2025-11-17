package com.medi.backend.youtube.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.medi.backend.youtube.config.YoutubeDataApiProperties;
import com.medi.backend.youtube.dto.YoutubeOAuthTokenDto;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.exception.NoAvailableApiKeyException;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.mapper.YoutubeOAuthTokenMapper;
import com.medi.backend.youtube.mapper.YoutubeVideoMapper;
import com.medi.backend.youtube.model.VideoSyncMode;
import com.medi.backend.youtube.redis.service.YoutubeRedisSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
public class YoutubeService {

    @Autowired
    private YoutubeOAuthService youtubeOAuthService;

    @Autowired
    private YoutubeChannelMapper channelMapper;

    @Autowired
    private YoutubeVideoMapper videoMapper;

    @Autowired
    private YoutubeOAuthTokenMapper tokenMapper;

    @Autowired
    private YoutubeDataApiClient youtubeDataApiClient;

    @Autowired
    private YoutubeDataApiProperties youtubeDataApiProperties;

    @Autowired(required = false)
    private YoutubeRedisSyncService youtubeRedisSyncService;

    // 초기화 시점에 Redis 서비스 주입 여부 확인
    @PostConstruct
    public void init() {
        if (youtubeRedisSyncService == null) {
            log.warn("⚠️ YoutubeRedisSyncService가 주입되지 않았습니다. Redis 동기화 기능이 비활성화됩니다.");
        } else {
            log.info("✅ YoutubeRedisSyncService가 정상적으로 주입되었습니다.");
        }
    }

    public boolean validateToken(Integer userId) {
        String token = youtubeOAuthService.getValidAccessToken(userId);
        return token != null && !token.isBlank();
    }

    private YouTube buildClient(String accessToken) throws Exception {
        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("medi").build();
    }

    /**
     * 채널 동기화
     */
    @Transactional
    public List<YoutubeChannelDto> syncChannels(Integer userId) {
        return syncChannels(userId, false);
    }

    /**
     * 채널 동기화
     * @param userId 사용자 ID
     * @param syncVideosEveryTime true면 매번 영상까지 즉시 동기화, false면 최초 동기화시에만 수행
     */
    @Transactional
    public List<YoutubeChannelDto> syncChannels(Integer userId, boolean syncVideosEveryTime) {
        try {
            YoutubeOAuthTokenDto tokenDto = tokenMapper.findByUserId(userId);
            if (tokenDto == null) {
                throw new IllegalStateException("YouTube OAuth 토큰이 존재하지 않습니다. 다시 연결해 주세요.");
            }

            // DB에서 사용자의 모든 채널 목록을 가져옴 (삭제된 채널 포함 - 동기화 시 체크용)
            List<YoutubeChannelDto> existingChannels = channelMapper.findByUserIdIncludingDeleted(userId);
            Map<String, YoutubeChannelDto> existingChannelMap = new HashMap<>();
            for (YoutubeChannelDto channel : existingChannels) {
                existingChannelMap.put(channel.getYoutubeChannelId(), channel);
            }

            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = buildClient(token);
            YouTube.Channels.List req = yt.channels().list(Arrays.asList("snippet","contentDetails","statistics"));
            req.setMine(true);
            ChannelListResponse resp = req.execute();
            
            for (Channel ch : resp.getItems()) {
                YoutubeChannelDto existing = existingChannelMap.get(ch.getId());
                boolean wasDeletedChannel = existing != null && existing.getDeletedAt() != null;
                
                // 삭제된 채널 처리:
                // - syncVideosEveryTime=true (OAuth 콜백): 복구 허용
                // - syncVideosEveryTime=false: 계속 삭제 상태 유지
                if (wasDeletedChannel && !syncVideosEveryTime) {
                    log.debug("채널({})은 삭제된 채널이므로 동기화를 건너뜁니다. userId={}", 
                            ch.getId(), userId);
                    continue;
                }
                if (wasDeletedChannel && syncVideosEveryTime) {
                    log.info("삭제된 채널 복구: {}. userId={}", ch.getId(), userId);
                    if (existing != null) {
                        existing.setDeletedAt(null);
                    }
                }
                
                // 새 채널 처리 로직:
                // - syncVideosEveryTime=true (OAuth 콜백): 새 채널 생성 ✅
                // - syncVideosEveryTime=false (수동 동기화): 새 채널 건너뜀
                if (existing == null && !syncVideosEveryTime) {
                    log.debug("채널({})은 DB에 존재하지 않으므로 동기화를 건너뜁니다 (새 채널, 수동 동기화 모드). userId={}", 
                            ch.getId(), userId);
                    continue;
                }
                
                // 새 채널 생성 또는 기존 채널 업데이트
                if (existing == null) {
                    log.info("새 채널 생성: {} (OAuth 콜백). userId={}", ch.getId(), userId);
                } else {
                    if (wasDeletedChannel) {
                        log.debug("삭제되었던 채널을 업데이트합니다: {}. userId={}", ch.getId(), userId);
                    } else {
                        log.debug("기존 채널 업데이트: {}. userId={}", ch.getId(), userId);
                    }
                }

                YoutubeChannelDto dto = mapChannelToDto(ch, userId, tokenDto.getId(), existing);

                // 1. MySQL에 저장 (트랜잭션 내)
                channelMapper.upsert(dto);

                // 영상 동기화 조건:
                // - syncVideosEveryTime=true: 항상 동기화 (OAuth 콜백 시)
                // - syncVideosEveryTime=false: 최초 등록된 채널만 동기화 (새로고침 시에는 새 영상만 가져오지 않음)
                //   → 새로고침은 채널 정보만 업데이트하고, 영상은 스케줄러에서 처리
                boolean shouldSyncVideos = syncVideosEveryTime
                        || wasDeletedChannel
                        || (existing != null && existing.getLastSyncedAt() == null);

                if (shouldSyncVideos) {
                    try {
                        VideoSyncMode mode = (existing == null || existing.getLastSyncedAt() == null)
                                ? VideoSyncMode.FIRST_SYNC
                                : VideoSyncMode.FOLLOW_UP;
                        syncVideos(userId, dto.getYoutubeChannelId(), 10, mode);
                    } catch (Exception videoSyncEx) {
                        log.warn("채널({}) 영상 동기화 실패 - userId={}, error={}",
                                ch.getId(), userId, videoSyncEx.getMessage(), videoSyncEx);
                    }
                } else {
                    log.debug("채널({}) 영상 동기화 스킵 - 이미 동기화된 채널 (새로고침은 채널 정보만 업데이트)", ch.getId());
                }
            }
            
            // 2. MySQL 저장 완료 후 Redis 초기 동기화
            // syncVideosEveryTime이 true일 때만 실행 (OAuth 콜백 직후 또는 수동 동기화 시)
            if (youtubeRedisSyncService == null) {
                log.warn("YoutubeRedisSyncService가 주입되지 않았습니다. Redis 동기화를 건너뜁니다. userId={}", userId);
            } else if (syncVideosEveryTime) {
                try {
                    log.info("Redis 초기 동기화 시작: userId={}", userId);
                    youtubeRedisSyncService.syncToRedis(userId);
                    log.info("Redis 초기 동기화 완료: userId={}", userId);
                } catch (Exception redisEx) {
                    log.error("Redis 초기 동기화 실패 - userId={}, error={}", userId, redisEx.getMessage(), redisEx);
                    // Redis 실패해도 MySQL은 이미 저장되었으므로 예외를 던지지 않음
                }
            } else {
                log.debug("Redis 동기화 스킵: syncVideosEveryTime=false, userId={}", userId);
            }
            
            // 동기화 후 DB에서 최신 채널 목록을 가져와서 반환 (삭제된 채널 제외)
            List<YoutubeChannelDto> latestChannels = channelMapper.findByUserId(userId);
            return latestChannels;
        } catch (Exception e) {
            log.error("YouTube 채널 동기화 실패: userId={}", userId, e);
            markUserChannelsFailed(userId, e.getMessage());
            throw new RuntimeException("syncChannels failed", e);
        }
    }

    /**
     * 영상 동기화
     * - 처음 동기화(채널의 lastSyncedAt이 null): 영상 최대 N개(기본 10개)만 수집
     * - 이후 동기화(증분): 필요시 상위 N개만 또는 증분 로직으로 확장 가능
     *
     * 매핑 원칙:
     * - YouTube 응답(JSON)의 키 이름을 DB 컬럼명으로 맞출 필요는 없음
     * - YouTube 응답 → 자바 DTO 필드(setter)로 "의미 대응" 하여 매핑
     * - DB 컬럼과 DTO 필드 간 매핑은 MyBatis XML(ResultMap)에서 처리
     *   (즉, API 키를 바꾸는 것이 아니라, DTO에 옮겨 담는 코드가 정확하면 됨)
     */
    @Transactional
    public List<YoutubeVideoDto> syncVideos(Integer userId, String youtubeChannelId, Integer maxResults) {
        return syncVideos(userId, youtubeChannelId, maxResults, VideoSyncMode.FOLLOW_UP);
    }

    @Transactional
    public List<YoutubeVideoDto> syncVideos(Integer userId, String youtubeChannelId, Integer maxResults, VideoSyncMode syncMode) {
        try {
            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = buildClient(token);
            YoutubeChannelDto channel = channelMapper.findByYoutubeChannelId(youtubeChannelId);
            if (channel == null || channel.getUploadsPlaylistId() == null) {
                throw new RuntimeException("채널 또는 업로드 플레이리스트 정보를 찾을 수 없습니다");
            }

            boolean treatAsFirstSync = syncMode == VideoSyncMode.FIRST_SYNC || channel.getLastSyncedAt() == null;

            updateChannelSyncInfo(channel.getYoutubeChannelId(), channel.getLastSyncedAt(), channel.getLastVideoPublishedAt());

            int defaultCap = treatAsFirstSync ? 10 : 10;
            int cap = (maxResults != null ? maxResults : defaultCap);
            LocalDateTime publishedAfter = treatAsFirstSync ? null : channel.getLastVideoPublishedAt();

            List<PlaylistVideoSnapshot> snapshots;
            Map<String, Video> statistics;

            // 조회(playlistItems/videos.list)는 Data API 키를 우선 사용하고,
            // 민감 작업(삭제/수정)은 계속 OAuth 토큰을 사용한다.
            if (youtubeDataApiClient.hasApiKeys()) {
                try {
                    snapshots = fetchPlaylistSnapshotsWithApiKey(channel.getUploadsPlaylistId(), publishedAfter, cap);
                    statistics = fetchVideoStatisticsWithApiKey(snapshots);
                } catch (NoAvailableApiKeyException ex) {
                    if (!youtubeDataApiProperties.isEnableFallback()) {
                        throw ex;
                    }
                    log.warn("YouTube Data API 키 사용이 불가능하여 OAuth 토큰으로 폴백합니다: {}", ex.getMessage());
                    snapshots = fetchPlaylistSnapshotsWithOAuth(yt, channel.getUploadsPlaylistId(), publishedAfter, cap);
                    statistics = fetchVideoStatisticsWithOAuth(yt, snapshots);
                }
            } else {
                snapshots = fetchPlaylistSnapshotsWithOAuth(yt, channel.getUploadsPlaylistId(), publishedAfter, cap);
                statistics = fetchVideoStatisticsWithOAuth(yt, snapshots);
            }

            if (snapshots.isEmpty()) {
                updateChannelSyncInfo(channel.getYoutubeChannelId(), LocalDateTime.now(), channel.getLastVideoPublishedAt());
                return Collections.emptyList();
            }

            List<YoutubeVideoDto> persisted = persistSnapshots(channel, snapshots, statistics, syncMode);

            LocalDateTime newestPublishedAt = channel.getLastVideoPublishedAt();
            for (YoutubeVideoDto dto : persisted) {
                if (dto.getPublishedAt() != null && (newestPublishedAt == null || dto.getPublishedAt().isAfter(newestPublishedAt))) {
                    newestPublishedAt = dto.getPublishedAt();
                }
            }
            updateChannelSyncInfo(channel.getYoutubeChannelId(), LocalDateTime.now(), newestPublishedAt);
            
            // MySQL 저장 완료 후 Redis 증분 동기화
            if (youtubeRedisSyncService != null && !persisted.isEmpty()) {
                try {
                    List<String> videoIds = persisted.stream()
                            .map(YoutubeVideoDto::getYoutubeVideoId)
                            .filter(id -> id != null && !id.isBlank())
                            .collect(java.util.stream.Collectors.toList());
                    
                    if (!videoIds.isEmpty()) {
                        log.info("MySQL 영상 동기화 완료 - Redis 증분 동기화 시작: userId={}, channelId={}, videoCount={}", 
                                userId, youtubeChannelId, videoIds.size());
                        youtubeRedisSyncService.syncIncrementalToRedis(userId, videoIds);
                        log.info("Redis 증분 동기화 완료: userId={}, videoCount={}", userId, videoIds.size());
                    }
                } catch (Exception redisEx) {
                    log.warn("Redis 증분 동기화 실패 - userId={}, channelId={}, error={}", 
                            userId, youtubeChannelId, redisEx.getMessage(), redisEx);
                    // Redis 실패해도 MySQL은 이미 저장되었으므로 예외를 던지지 않음
                }
            }
            
            return persisted;
        } catch (Exception e) {
            log.error("YouTube 영상 동기화 실패: channelId={}", youtubeChannelId, e);
            YoutubeChannelDto existing = channelMapper.findByYoutubeChannelId(youtubeChannelId);
            LocalDateTime lastSynced = existing != null ? existing.getLastSyncedAt() : null;
            LocalDateTime lastPublished = existing != null ? existing.getLastVideoPublishedAt() : null;
            updateChannelSyncInfo(youtubeChannelId, lastSynced, lastPublished);
            throw new RuntimeException("syncVideos failed", e);
        }
    }

    private List<PlaylistVideoSnapshot> fetchPlaylistSnapshotsWithApiKey(String uploadsPlaylistId,
                                                                         LocalDateTime publishedAfter,
                                                                         int cap) throws IOException {
        List<PlaylistVideoSnapshot> snapshots = new ArrayList<>();
        String nextPageToken = null;
        do {
            PlaylistItemListResponse playlistResp = youtubeDataApiClient.fetchPlaylistItems(uploadsPlaylistId, nextPageToken);
            if (playlistResp.getItems() == null || playlistResp.getItems().isEmpty()) {
                break;
            }

            for (PlaylistItem item : playlistResp.getItems()) {
                PlaylistVideoSnapshot snapshot = PlaylistVideoSnapshot.from(item);
                if (snapshot == null) {
                    continue;
                }
                if (publishedAfter != null && snapshot.publishedAt() != null
                        && !snapshot.publishedAt().isAfter(publishedAfter)) {
                    return snapshots;
                }
                snapshots.add(snapshot);
                if (snapshots.size() >= cap) {
                    return snapshots;
                }
            }
            nextPageToken = playlistResp.getNextPageToken();
        } while (nextPageToken != null);
        return snapshots;
    }

    private Map<String, Video> fetchVideoStatisticsWithApiKey(List<PlaylistVideoSnapshot> snapshots) throws IOException {
        Map<String, Video> result = new HashMap<>();
        if (snapshots.isEmpty()) {
            return result;
        }

        List<String> videoIds = new ArrayList<>();
        for (PlaylistVideoSnapshot snapshot : snapshots) {
            videoIds.add(snapshot.videoId());
        }

        for (int i = 0; i < videoIds.size(); i += 50) {
            int end = Math.min(i + 50, videoIds.size());
            List<String> batch = videoIds.subList(i, end);
            VideoListResponse videosResponse = youtubeDataApiClient.fetchVideoStatistics(batch);
            if (videosResponse.getItems() == null) {
                continue;
            }
            for (Video video : videosResponse.getItems()) {
                result.put(video.getId(), video);
            }
        }
        return result;
    }

    private List<PlaylistVideoSnapshot> fetchPlaylistSnapshotsWithOAuth(YouTube yt,
                                                                        String uploadsPlaylistId,
                                                                        LocalDateTime publishedAfter,
                                                                        int cap) throws Exception {
        List<PlaylistVideoSnapshot> snapshots = new ArrayList<>();
        String nextPageToken = null;
        do {
            YouTube.PlaylistItems.List playlistReq = yt.playlistItems().list(Arrays.asList("snippet", "contentDetails"));
            playlistReq.setPlaylistId(uploadsPlaylistId);
            playlistReq.setMaxResults(50L);
            if (nextPageToken != null) playlistReq.setPageToken(nextPageToken);

            PlaylistItemListResponse playlistResp = playlistReq.execute();
            if (playlistResp.getItems() == null || playlistResp.getItems().isEmpty()) {
                break;
            }

            for (PlaylistItem item : playlistResp.getItems()) {
                PlaylistVideoSnapshot snapshot = PlaylistVideoSnapshot.from(item);
                if (snapshot == null) continue;
                if (publishedAfter != null && snapshot.publishedAt() != null
                        && !snapshot.publishedAt().isAfter(publishedAfter)) {
                    return snapshots;
                }
                snapshots.add(snapshot);
                if (snapshots.size() >= cap) {
                    return snapshots;
                }
            }
            nextPageToken = playlistResp.getNextPageToken();
        } while (nextPageToken != null);
        return snapshots;
    }

    private Map<String, Video> fetchVideoStatisticsWithOAuth(YouTube yt, List<PlaylistVideoSnapshot> snapshots) throws Exception {
        Map<String, Video> result = new HashMap<>();
        if (snapshots.isEmpty()) return result;
        List<String> videoIds = new ArrayList<>();
        for (PlaylistVideoSnapshot snapshot : snapshots) {
            videoIds.add(snapshot.videoId());
        }
        for (int i = 0; i < videoIds.size(); i += 50) {
            int end = Math.min(i + 50, videoIds.size());
            List<String> batch = videoIds.subList(i, end);
            YouTube.Videos.List videosRequest = yt.videos().list(Collections.singletonList("statistics"));
            videosRequest.setId(batch);
            VideoListResponse videosResponse = videosRequest.execute();
            if (videosResponse.getItems() == null) continue;
            for (Video video : videosResponse.getItems()) {
                result.put(video.getId(), video);
            }
        }
        return result;
    }

    private List<YoutubeVideoDto> persistSnapshots(YoutubeChannelDto channel,
                                                   List<PlaylistVideoSnapshot> snapshots,
                                                   Map<String, Video> statistics,
                                                   VideoSyncMode syncMode) {
        List<YoutubeVideoDto> persisted = new ArrayList<>();
        for (PlaylistVideoSnapshot snapshot : snapshots) {
            Video stat = statistics.get(snapshot.videoId());
            YoutubeVideoDto dto = mapVideoSnapshotToDto(channel.getId(), snapshot, stat);
            videoMapper.upsert(dto);
            persisted.add(dto);
        }
        return persisted;
    }

    private YoutubeChannelDto mapChannelToDto(Channel ch, Integer userId, Integer oauthTokenId, YoutubeChannelDto existing) {
        LocalDateTime now = LocalDateTime.now();
        YoutubeChannelDto dto = new YoutubeChannelDto();
        if (existing != null) {
            dto.setId(existing.getId());
            dto.setCreatedAt(existing.getCreatedAt());
            dto.setUpdatedAt(existing.getUpdatedAt());
            dto.setLastVideoPublishedAt(existing.getLastVideoPublishedAt());
            if (existing.getUploadsPlaylistId() != null) {
                dto.setUploadsPlaylistId(existing.getUploadsPlaylistId());
            }
        }
        dto.setUserId(userId);
        dto.setOauthTokenId(oauthTokenId);
        dto.setYoutubeChannelId(ch.getId());
        dto.setChannelName(ch.getSnippet() != null ? ch.getSnippet().getTitle() : null);
        dto.setChannelHandle(ch.getSnippet() != null ? ch.getSnippet().getCustomUrl() : null);
        dto.setThumbnailUrl(extractThumbnail(ch));
        dto.setLastSyncedAt(now);
        if (ch.getContentDetails() != null && ch.getContentDetails().getRelatedPlaylists() != null) {
            dto.setUploadsPlaylistId(ch.getContentDetails().getRelatedPlaylists().getUploads());
        }
        return dto;
    }

    private YoutubeVideoDto mapVideoSnapshotToDto(Integer channelId, PlaylistVideoSnapshot snapshot, Video stat) {
        YoutubeVideoDto dto = new YoutubeVideoDto();
        dto.setChannelId(channelId);
        dto.setYoutubeVideoId(snapshot.videoId());
        dto.setTitle(snapshot.title());
        dto.setPublishedAt(snapshot.publishedAt());
        dto.setThumbnailUrl(snapshot.thumbnailUrl());
        if (stat != null && stat.getStatistics() != null) {
            dto.setViewCount(toLong(stat.getStatistics().getViewCount()));
            dto.setLikeCount(toLong(stat.getStatistics().getLikeCount()));
            dto.setCommentCount(toLong(stat.getStatistics().getCommentCount()));
        }
        return dto;
    }

    private void updateChannelSyncInfo(String youtubeChannelId,
                                       LocalDateTime lastSyncedAt,
                                       LocalDateTime lastVideoPublishedAt) {
        channelMapper.updateSyncState(
                youtubeChannelId,
                lastSyncedAt,
                lastVideoPublishedAt
        );
    }

    private void markUserChannelsFailed(Integer userId, String error) {
        List<YoutubeChannelDto> channels = channelMapper.findByUserId(userId);
        if (channels == null) return;
        if (error != null) {
            log.warn("채널 동기화 실패: userId={}, reason={}", userId, error);
        }
        for (YoutubeChannelDto channel : channels) {
            updateChannelSyncInfo(channel.getYoutubeChannelId(), channel.getLastSyncedAt(), channel.getLastVideoPublishedAt());
        }
    }

    private String extractThumbnail(Channel ch) {
        if (ch.getSnippet() == null || ch.getSnippet().getThumbnails() == null) return null;
        Thumbnail defaultThumb = ch.getSnippet().getThumbnails().getDefault();
        return defaultThumb != null ? defaultThumb.getUrl() : null;
    }

    private Long toLong(BigInteger value) {
        return value != null ? value.longValue() : null;
    }

    private static class PlaylistVideoSnapshot {
        private final String videoId;
        private final String title;
        private final String thumbnailUrl;
        private final LocalDateTime publishedAt;

        private PlaylistVideoSnapshot(String videoId, String title, String thumbnailUrl, LocalDateTime publishedAt) {
            this.videoId = videoId;
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
            this.publishedAt = publishedAt;
        }

        static PlaylistVideoSnapshot from(PlaylistItem item) {
            if (item.getSnippet() == null) return null;
            String videoId = null;
            if (item.getContentDetails() != null && item.getContentDetails().getVideoId() != null) {
                videoId = item.getContentDetails().getVideoId();
            } else if (item.getSnippet().getResourceId() != null) {
                videoId = item.getSnippet().getResourceId().getVideoId();
            }
            if (videoId == null) return null;

            String title = item.getSnippet().getTitle();
            String thumbnail = null;
            if (item.getSnippet().getThumbnails() != null && item.getSnippet().getThumbnails().getDefault() != null) {
                thumbnail = item.getSnippet().getThumbnails().getDefault().getUrl();
            }

            LocalDateTime publishedAt = null;
            if (item.getContentDetails() != null && item.getContentDetails().getVideoPublishedAt() != null) {
                publishedAt = ZonedDateTime.parse(item.getContentDetails().getVideoPublishedAt().toStringRfc3339()).toLocalDateTime();
            } else if (item.getSnippet().getPublishedAt() != null) {
                publishedAt = ZonedDateTime.parse(item.getSnippet().getPublishedAt().toStringRfc3339()).toLocalDateTime();
            }
            return new PlaylistVideoSnapshot(videoId, title, thumbnail, publishedAt);
        }

        String videoId() {
            return videoId;
        }

        String title() {
            return title;
        }

        String thumbnailUrl() {
            return thumbnailUrl;
        }

        LocalDateTime publishedAt() {
            return publishedAt;
        }
    }
}

