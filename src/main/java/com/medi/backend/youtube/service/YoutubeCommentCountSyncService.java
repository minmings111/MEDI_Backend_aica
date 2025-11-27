package com.medi.backend.youtube.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.services.youtube.model.VideoListResponse;
import com.medi.backend.agent.mapper.AgentMapper;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.mapper.YoutubeVideoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * YouTube 실제 댓글 수를 daily_comment_stats 테이블에 저장하는 서비스
 * - 하루에 한 번 스케줄러에서 호출
 * - YouTube Data API에서 가져온 실제 댓글 수를 날짜별로 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeCommentCountSyncService {

    private final YoutubeVideoMapper videoMapper;
    private final YoutubeChannelMapper channelMapper;
    private final AgentMapper agentMapper;
    private final YoutubeDataApiClient youtubeDataApiClient;

    /**
     * 모든 비디오의 YouTube 실제 댓글 수를 저장
     * 스케줄러에서 하루에 한 번 호출 (오전 1시에 실행)
     * - 오전 1시에 실행되므로 전날 날짜로 저장 (하루가 끝난 후 그 날의 최종 댓글 수 저장)
     */
    @Transactional
    public void syncYoutubeCommentCounts() {
        log.info("📊 YouTube 실제 댓글 수 동기화 시작");
        // 오전 1시에 실행되므로 전날 날짜로 저장
        LocalDate targetDate = LocalDate.now().minusDays(1);
        int successCount = 0;
        int failCount = 0;

        try {
            // 모든 채널 조회
            List<YoutubeChannelDto> channels = channelMapper.findAllForSync();
            
            if (channels == null || channels.isEmpty()) {
                log.info("📊 동기화할 채널이 없습니다.");
                return;
            }

            log.info("📊 총 {}개 채널의 댓글 수 동기화 시작", channels.size());

            for (YoutubeChannelDto channel : channels) {
                try {
                    // 채널의 모든 비디오 조회
                    List<YoutubeVideoDto> videos = videoMapper.findByChannelId(channel.getId());
                    
                    if (videos == null || videos.isEmpty()) {
                        continue;
                    }

                    // 비디오 ID 목록 추출
                    List<String> videoIds = videos.stream()
                        .map(YoutubeVideoDto::getYoutubeVideoId)
                        .collect(Collectors.toList());

                    // YouTube API에서 일괄 조회 (최대 50개씩)
                    int batchSize = 50;
                    for (int i = 0; i < videoIds.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, videoIds.size());
                        List<String> batch = videoIds.subList(i, end);
                        
                        try {
                            VideoListResponse response = youtubeDataApiClient.fetchVideoStatistics(batch);
                            
                            if (response != null && response.getItems() != null) {
                                for (var videoItem : response.getItems()) {
                                    if (videoItem.getStatistics() != null && 
                                        videoItem.getStatistics().getCommentCount() != null) {
                                        
                                        // 해당 비디오 찾기
                                        String youtubeVideoId = videoItem.getId();
                                        YoutubeVideoDto video = videos.stream()
                                            .filter(v -> v.getYoutubeVideoId().equals(youtubeVideoId))
                                            .findFirst()
                                            .orElse(null);
                                        
                                        if (video != null) {
                                            Long commentCount = videoItem.getStatistics().getCommentCount().longValue();
                                            
                                            // daily_comment_stats 테이블에 저장
                                            agentMapper.updateYoutubeTotalCount(
                                                channel.getId(),
                                                video.getId(),
                                                targetDate,
                                                commentCount
                                            );
                                            successCount++;
                                            log.debug("✅ 비디오 {} 댓글 수 저장: {}개", youtubeVideoId, commentCount);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("❌ 비디오 배치 조회 실패: channelId={}, error={}", channel.getId(), e.getMessage());
                            failCount += batch.size();
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ 채널 {} 댓글 수 동기화 실패: {}", channel.getId(), e.getMessage(), e);
                    failCount++;
                }
            }

            log.info("📊 YouTube 실제 댓글 수 동기화 완료: 성공={}개, 실패={}개", successCount, failCount);
        } catch (Exception e) {
            log.error("❌ YouTube 댓글 수 동기화 중 오류 발생", e);
            throw e;
        }
    }

    /**
     * 특정 채널의 비디오들만 동기화
     */
    @Transactional
    public void syncYoutubeCommentCountsByChannel(Integer channelId) {
        log.info("📊 채널 {}의 YouTube 실제 댓글 수 동기화 시작", channelId);
        // 자정에 실행되므로 전날 날짜로 저장
        LocalDate targetDate = LocalDate.now().minusDays(1);
        int successCount = 0;
        int failCount = 0;

        try {
            List<YoutubeVideoDto> videos = videoMapper.findByChannelId(channelId);
            
            if (videos == null || videos.isEmpty()) {
                log.info("📊 채널 {}에 동기화할 비디오가 없습니다.", channelId);
                return;
            }

            // 비디오 ID 목록 추출
            List<String> videoIds = videos.stream()
                .map(YoutubeVideoDto::getYoutubeVideoId)
                .collect(Collectors.toList());

            // YouTube API에서 일괄 조회 (최대 50개씩)
            int batchSize = 50;
            for (int i = 0; i < videoIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, videoIds.size());
                List<String> batch = videoIds.subList(i, end);
                
                try {
                    VideoListResponse response = youtubeDataApiClient.fetchVideoStatistics(batch);
                    
                    if (response != null && response.getItems() != null) {
                        for (var videoItem : response.getItems()) {
                            if (videoItem.getStatistics() != null && 
                                videoItem.getStatistics().getCommentCount() != null) {
                                
                                String youtubeVideoId = videoItem.getId();
                                YoutubeVideoDto video = videos.stream()
                                    .filter(v -> v.getYoutubeVideoId().equals(youtubeVideoId))
                                    .findFirst()
                                    .orElse(null);
                                
                                if (video != null) {
                                    Long commentCount = videoItem.getStatistics().getCommentCount().longValue();
                                    
                                    agentMapper.updateYoutubeTotalCount(
                                        channelId,
                                        video.getId(),
                                        targetDate,
                                        commentCount
                                    );
                                    successCount++;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ 비디오 배치 조회 실패: channelId={}, error={}", channelId, e.getMessage());
                    failCount += batch.size();
                }
            }

            log.info("📊 채널 {} 댓글 수 동기화 완료: 성공={}개, 실패={}개", channelId, successCount, failCount);
        } catch (Exception e) {
            log.error("❌ 채널 {} 댓글 수 동기화 중 오류 발생", channelId, e);
            throw e;
        }
    }
}

