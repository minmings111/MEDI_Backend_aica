package com.medi.backend.youtube.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.medi.backend.youtube.config.YoutubeDataApiProperties;
import com.medi.backend.youtube.config.YoutubeSyncConfigProperties;
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
import java.util.concurrent.CompletableFuture;

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

    @Autowired
    private YoutubeSyncConfigProperties syncConfig;

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
        log.info("🔄 [트랜잭션 시작] 채널 동기화 시작: userId={}, syncVideosEveryTime={}", userId, syncVideosEveryTime);
        try {
            YoutubeOAuthTokenDto tokenDto = tokenMapper.findByUserId(userId);
            if (tokenDto == null) {
                log.error("❌ YouTube OAuth 토큰이 존재하지 않습니다: userId={}", userId);
                throw new IllegalStateException("YouTube OAuth 토큰이 존재하지 않습니다. 다시 연결해 주세요.");
            }
            log.debug("✅ OAuth 토큰 조회 성공: userId={}, tokenId={}", userId, tokenDto.getId());

            // DB에서 사용자의 모든 채널 목록을 가져옴 (삭제된 채널 포함 - 동기화 시 체크용)
            List<YoutubeChannelDto> existingChannels = channelMapper.findByUserIdIncludingDeleted(userId);
            log.info("📋 기존 채널 조회 (삭제된 것 포함): userId={}, 기존채널수={}개", userId, existingChannels.size());
            
            Map<String, YoutubeChannelDto> existingChannelMap = new HashMap<>(
                Math.max(16, existingChannels.size()), 0.75f);
            for (YoutubeChannelDto channel : existingChannels) {
                existingChannelMap.put(channel.getYoutubeChannelId(), channel);
                log.debug("📋 기존 채널 매핑: channelId={}, name={}, deletedAt={}", 
                    channel.getYoutubeChannelId(), channel.getChannelName(), channel.getDeletedAt());
            }

            // ⭐ OAuth 토큰 가져오기 (실패 시 기존 DB 채널 반환)
            String token;
            YouTube yt;
            try {
                token = youtubeOAuthService.getValidAccessToken(userId);
                yt = buildClient(token);
                log.debug("✅ OAuth 토큰 검증 성공: userId={}", userId);
            } catch (RuntimeException tokenEx) {
                // OAuth 토큰 만료 또는 refresh token 만료 시 기존 DB 채널 반환
                String errorMsg = tokenEx.getMessage();
                if (errorMsg != null && (errorMsg.contains("Refresh token") || errorMsg.contains("reconnect required") 
                        || errorMsg.contains("not found") || errorMsg.contains("YouTube token not found"))) {
                    log.warn("⚠️ OAuth 토큰 만료/없음 - 기존 DB 채널 정보 반환: userId={}, error={}", userId, errorMsg);
                    List<YoutubeChannelDto> existingChannelsList = channelMapper.findByUserId(userId);
                    if (!existingChannelsList.isEmpty()) {
                        log.info("✅ 기존 DB 채널 정보 반환: userId={}, 채널={}개", userId, existingChannelsList.size());
                        return existingChannelsList;
                    } else {
                        log.error("❌ OAuth 토큰 만료 및 DB에 기존 채널 정보가 없습니다: userId={}", userId);
                        throw new RuntimeException("OAuth 토큰이 만료되었습니다. 다시 연결해주세요.", tokenEx);
                    }
                }
                // 다른 예외는 그대로 던지기
                log.error("❌ OAuth 토큰 가져오기 실패 (예상치 못한 에러): userId={}, error={}", userId, errorMsg);
                throw tokenEx;
            }
            
            // ⭐ 채널 목록 조회 (setMine(true)는 OAuth 토큰 필수, API 키로는 불가능)
            ChannelListResponse resp;
            try {
            YouTube.Channels.List req = yt.channels().list(Arrays.asList("snippet","contentDetails","statistics"));
            req.setMine(true);
                resp = req.execute();
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                // ⚠️ catch 블록 진입 확인 로그
                log.info("🔍 YouTube 채널 조회 예외 발생: userId={}, statusCode={}, exceptionType={}", 
                    userId, e.getStatusCode(), e.getClass().getSimpleName());
                
                // 401 Unauthorized: OAuth 토큰 만료 (API 호출 시점에 만료된 경우)
                if (e.getStatusCode() == 401) {
                    log.warn("⚠️ YouTube 채널 조회 401 에러 (OAuth 토큰 만료) - userId={}, 기존 DB 채널 정보 반환", userId);
                    List<YoutubeChannelDto> existingChannelsList = channelMapper.findByUserId(userId);
                    if (!existingChannelsList.isEmpty()) {
                        log.info("✅ 기존 DB 채널 정보 반환: userId={}, 채널={}개", userId, existingChannelsList.size());
                        return existingChannelsList;
                    } else {
                        log.error("❌ OAuth 토큰 만료 및 DB에 기존 채널 정보가 없습니다: userId={}", userId);
                        throw new RuntimeException("OAuth 토큰이 만료되었습니다. 다시 연결해주세요.", e);
                    }
                }
                
                // 쿼터 초과 등 403 에러 처리
                if (e.getStatusCode() == 403) {
                    String errorReason = com.medi.backend.youtube.redis.util.YoutubeErrorUtil.extractErrorReason(e);
                    log.info("🔍 YouTube 채널 조회 403 에러 처리 시작: userId={}, statusCode={}, errorReason={}", 
                        userId, e.getStatusCode(), errorReason);
                    
                    if ("quotaExceeded".equals(errorReason) || "dailyLimitExceeded".equals(errorReason) 
                            || "userRateLimitExceeded".equals(errorReason)) {
                        log.warn("⚠️ YouTube 채널 조회 쿼터 초과 - userId={}, errorReason={}, 기존 DB 채널 정보 반환", 
                            userId, errorReason);
                        // ⚠️ 쿼터 초과 시 기존 DB의 채널 정보를 반환 (사용자 경험 개선)
                        List<YoutubeChannelDto> existingChannelsList = channelMapper.findByUserId(userId);
                        if (existingChannelsList.isEmpty()) {
                            // DB에도 없으면 예외 던지기 (사용자가 알 수 있도록)
                            // ⚠️ 프로젝트 전체 쿼터가 소진된 경우이므로, 다른 계정으로 로그인해도 같은 에러 발생
                            log.error("❌ DB에 기존 채널 정보가 없습니다 (프로젝트 전체 쿼터 소진): userId={}, errorReason={}", 
                                userId, errorReason);
                            throw new RuntimeException(
                                "YouTube API 일일 할당량이 모두 소진되었습니다. " +
                                "프로젝트 전체의 쿼터가 소진된 상태이므로, 다른 계정으로 로그인해도 같은 오류가 발생합니다. " +
                                "24시간 후 자동으로 복구되거나, Google Cloud Console에서 할당량을 늘릴 수 있습니다. " +
                                "잠시 후 다시 시도해주세요.", e);
                        } else {
                            log.info("✅ 기존 DB 채널 정보 반환: userId={}, 채널={}개", userId, existingChannelsList.size());
                            return existingChannelsList;
                        }
                    } else {
                        log.warn("⚠️ YouTube 채널 조회 403 에러 (quota 이외): userId={}, errorReason={}", userId, errorReason);
                    }
                } else {
                    log.info("🔍 YouTube 채널 조회 에러 (401/403 아님): userId={}, statusCode={}", userId, e.getStatusCode());
                }
                // 다른 종류의 403 에러나 다른 예외는 그대로 던지기
                log.info("🔍 예외를 다시 던집니다: userId={}, statusCode={}", userId, e.getStatusCode());
                throw e;
            }
            
            if (resp.getItems() == null || resp.getItems().isEmpty()) {
                log.warn("⚠️ YouTube API를 통해 조회된 채널이 없습니다: userId={}", userId);
                // API에서 채널이 없으면 기존 DB 채널 정보 반환
                List<YoutubeChannelDto> existingChannelsList = channelMapper.findByUserId(userId);
                log.info("📋 기존 DB 채널 정보 반환: userId={}, 채널수={}개", userId, existingChannelsList.size());
                return existingChannelsList;
            }
            
            log.info("✅ YouTube API 채널 조회 성공: userId={}, API채널수={}개", userId, resp.getItems().size());
            
            int upsertCount = 0;
            int skipCount = 0;
            for (Channel ch : resp.getItems()) {
                log.debug("🔄 채널 처리 시작: channelId={}, userId={}", ch.getId(), userId);
                YoutubeChannelDto existing = existingChannelMap.get(ch.getId());
                boolean wasDeletedChannel = existing != null && existing.getDeletedAt() != null;
                
                // 삭제된 채널 처리:
                // - syncVideosEveryTime=true (OAuth 콜백): 복구 허용
                // - syncVideosEveryTime=false: 계속 삭제 상태 유지
                if (wasDeletedChannel && !syncVideosEveryTime) {
                    log.debug("채널({})은 삭제된 채널이므로 동기화를 건너뜁니다. userId={}", 
                            ch.getId(), userId);
                    skipCount++;
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
                    skipCount++;
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
                log.info("💾 채널 저장 준비: channelId={}, channelName={}, isNew={}, wasDeleted={}, deletedAt={}", 
                    dto.getYoutubeChannelId(), dto.getChannelName(), 
                    existing == null, wasDeletedChannel, dto.getDeletedAt());

                // 1. MySQL에 저장 (트랜잭션 내)
                try {
                channelMapper.upsert(dto);
                    upsertCount++;
                    log.info("✅ 채널 DB 저장 성공: channelId={}, channelName={}, userId={}", 
                        dto.getYoutubeChannelId(), dto.getChannelName(), userId);
                } catch (Exception upsertEx) {
                    log.error("❌ 채널 DB 저장 실패: channelId={}, channelName={}, userId={}, error={}", 
                        dto.getYoutubeChannelId(), dto.getChannelName(), userId, upsertEx.getMessage(), upsertEx);
                    throw upsertEx; // 트랜잭션 롤백을 위해 예외 다시 던지기
                }

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
                        // 초기 동기화 시 설정값 사용 (기본값: 5개)
                        syncVideos(userId, dto.getYoutubeChannelId(), syncConfig.getMaxVideosInitial(), mode);
                    } catch (Exception videoSyncEx) {
                        log.warn("채널({}) 영상 동기화 실패 - userId={}, error={}",
                                ch.getId(), userId, videoSyncEx.getMessage(), videoSyncEx);
                    }
                } else {
                    log.debug("채널({}) 영상 동기화 스킵 - 이미 동기화된 채널 (새로고침은 채널 정보만 업데이트)", ch.getId());
                }
            }
            
            log.info("📊 채널 처리 완료: userId={}, 처리된채널={}개, 저장성공={}개, 스킵={}개", 
                userId, resp.getItems().size(), upsertCount, skipCount);
            
            // 2. MySQL 저장 완료 후 Redis 초기 동기화 (비동기로 실행)
            // syncVideosEveryTime이 true일 때만 실행 (OAuth 콜백 직후 또는 수동 동기화 시)
            // ⚡ 비동기 처리: 사용자는 즉시 응답을 받고, Redis 동기화는 백그라운드에서 실행됩니다.
            if (youtubeRedisSyncService == null) {
                log.warn("YoutubeRedisSyncService가 주입되지 않았습니다. Redis 동기화를 건너뜁니다. userId={}", userId);
            } else if (syncVideosEveryTime) {
                // 비동기로 Redis 동기화 시작 (사용자는 기다리지 않음)
                try {
                log.info("🔄 [비동기] Redis 초기 동기화 시작: userId={} (백그라운드 실행)", userId);
                
                // ⚡ 안전한 CompletableFuture 처리: whenComplete로 완료 보장
                youtubeRedisSyncService.syncToRedisAsync(userId)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // 예외 발생 시
                            log.error("❌ [비동기] Redis 초기 동기화 예외 발생: userId={}", userId, ex);
                        } else if (result != null) {
                            // 정상 완료 시
                            if (result.isSuccess()) {
                                log.info("✅ [비동기] Redis 초기 동기화 완료: userId={}, 채널={}개, 비디오={}개, 댓글={}개", 
                                    userId, result.getChannelCount(), result.getVideoCount(), result.getCommentCount());
                            } else {
                                log.error("❌ [비동기] Redis 초기 동기화 실패: userId={}, error={}", 
                                    userId, result.getErrorMessage());
                            }
                        }
                        // 완료되면 GC 대상이 되어 메모리 누수 방지
                    });
                } catch (Exception redisStartEx) {
                    // syncToRedisAsync 호출 자체에서 발생하는 예외는 DB 트랜잭션을 롤백시키지 않도록 방어
                    log.error("⚠️ [비동기] Redis 초기 동기화 시작 실패 (DB 저장은 유지됨): userId={}, error={}",
                        userId, redisStartEx.getMessage(), redisStartEx);
                }
            } else {
                log.debug("Redis 동기화 스킵: syncVideosEveryTime=false, userId={}", userId);
            }
            
            // ⚡ 즉시 DB에서 최신 채널 목록을 가져와서 반환 (Redis 동기화 완료를 기다리지 않음)
            log.info("📋 최종 채널 목록 조회 시작: userId={}", userId);
            List<YoutubeChannelDto> latestChannels = channelMapper.findByUserId(userId);
            log.info("✅ [트랜잭션 성공] 채널 동기화 완료: userId={}, 반환채널수={}개, 저장성공={}개", 
                userId, latestChannels != null ? latestChannels.size() : 0, upsertCount);
            
            if (latestChannels != null && !latestChannels.isEmpty()) {
                for (YoutubeChannelDto channel : latestChannels) {
                    log.debug("✅ 반환 채널: channelId={}, name={}, deletedAt={}", 
                        channel.getYoutubeChannelId(), channel.getChannelName(), channel.getDeletedAt());
                }
            } else {
                log.warn("⚠️ 최종 채널 목록이 비어있습니다: userId={}, 저장성공={}개", userId, upsertCount);
            }
            
            return latestChannels;
        } catch (Exception e) {
            log.error("❌ [트랜잭션 롤백] YouTube 채널 동기화 실패: userId={}, errorType={}, errorMessage={}", 
                userId, e.getClass().getSimpleName(), e.getMessage(), e);
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
        return syncVideos(userId, youtubeChannelId, maxResults, syncMode, false);
    }

    @Transactional
    public List<YoutubeVideoDto> syncVideos(Integer userId, String youtubeChannelId, Integer maxResults,
                                           VideoSyncMode syncMode, boolean skipCommentSync) {
        try {
            String token = youtubeOAuthService.getValidAccessToken(userId);
            YouTube yt = buildClient(token);
            YoutubeChannelDto channel = channelMapper.findByYoutubeChannelId(youtubeChannelId);
            if (channel == null || channel.getUploadsPlaylistId() == null) {
                throw new RuntimeException("채널 또는 업로드 플레이리스트 정보를 찾을 수 없습니다");
            }

            boolean treatAsFirstSync = syncMode == VideoSyncMode.FIRST_SYNC || channel.getLastSyncedAt() == null;

            updateChannelSyncInfo(channel.getYoutubeChannelId(), channel.getLastSyncedAt(), channel.getLastVideoPublishedAt());

            // 설정값 사용: 초기 동기화는 maxVideosInitial, 증분 동기화는 maxVideosPerHour
            int defaultCap = treatAsFirstSync ? syncConfig.getMaxVideosInitial() : syncConfig.getMaxVideosPerHour();
            int cap = (maxResults != null ? maxResults : defaultCap);
            LocalDateTime publishedAfter = treatAsFirstSync ? null : channel.getLastVideoPublishedAt();

            log.debug("[YouTube] 영상 동기화 시작: userId={}, channelId={}, mode={}, skipComment={}, maxResults={}, cap={}",
                    userId, youtubeChannelId, syncMode, skipCommentSync, maxResults, cap);

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

            // 영상 개수 제한 도달 시 경고 로그
            if (snapshots.size() >= cap && cap < Integer.MAX_VALUE) {
                log.warn("[YouTube] 영상 개수 제한 도달: userId={}, channelId={}, 조회={}, 제한={}, " +
                        "다음 동기화 시 처리될 영상이 있을 수 있습니다.",
                        userId, youtubeChannelId, snapshots.size(), cap);
            }

            // skipCommentSync가 false일 때만 Redis 댓글 동기화 수행
            // (스케줄러에서는 skipCommentSync=true로 호출하여 중복 호출 방지)
            if (!skipCommentSync && youtubeRedisSyncService != null && !persisted.isEmpty()) {
                try {
                    List<String> videoIds = persisted.stream()
                            .map(YoutubeVideoDto::getYoutubeVideoId)
                            .filter(id -> id != null && !id.isBlank())
                            .collect(java.util.stream.Collectors.toList());

                    if (!videoIds.isEmpty()) {
                        log.info("[YouTube] MySQL 영상 동기화 완료 - Redis 증분 동기화 시작: userId={}, channelId={}, videoCount={}",
                                userId, youtubeChannelId, videoIds.size());
                        youtubeRedisSyncService.syncIncrementalToRedis(userId, videoIds);
                        log.info("[YouTube] Redis 증분 동기화 완료: userId={}, videoCount={}", userId, videoIds.size());
                    }
                } catch (Exception redisEx) {
                    log.warn("[YouTube] Redis 증분 동기화 실패 - userId={}, channelId={}, error={}",
                            userId, youtubeChannelId, redisEx.getMessage(), redisEx);
                    // Redis 실패해도 MySQL은 이미 저장되었으므로 예외를 던지지 않음
                }
            } else if (skipCommentSync) {
                log.debug("[YouTube] 댓글 동기화 건너뜀: userId={}, channelId={}", userId, youtubeChannelId);
            }
            
            return persisted;
        } catch (Exception e) {
            // 예상 가능한 API 오류(예: playlistNotFound 등)는 채널 동기화를 망치지 않도록 soft-fail 처리한다.
            // 어차피 상위 syncChannels()에서 한 번 더 try-catch 하고 있으므로,
            // 여기서는 예외를 다시 던지지 않고 빈 리스트를 반환하여 "영상 0개" 상태로 취급한다.
            log.error("YouTube 영상 동기화 실패(soft-fail): channelId={}", youtubeChannelId, e);
            YoutubeChannelDto existing = channelMapper.findByYoutubeChannelId(youtubeChannelId);
            LocalDateTime lastSynced = existing != null ? existing.getLastSyncedAt() : null;
            LocalDateTime lastPublished = existing != null ? existing.getLastVideoPublishedAt() : null;
            updateChannelSyncInfo(youtubeChannelId, lastSynced, lastPublished);
            return Collections.emptyList();
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
                // cap이 Integer.MAX_VALUE가 아닐 때만 제한 체크
                if (cap != Integer.MAX_VALUE && snapshots.size() >= cap) {
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
                // cap이 Integer.MAX_VALUE가 아닐 때만 제한 체크
                if (cap != Integer.MAX_VALUE && snapshots.size() >= cap) {
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
        if (ch.getStatistics() != null) {
            dto.setSubscriberCount(toLong(ch.getStatistics().getSubscriberCount()));
        }
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

