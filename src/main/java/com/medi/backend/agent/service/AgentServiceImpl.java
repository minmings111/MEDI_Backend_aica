package com.medi.backend.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.dto.AgentProfilingRequest;
import com.medi.backend.agent.dto.FilteredCommentResponse;
import com.medi.backend.agent.dto.AnalysisSummaryResponse;
import com.medi.backend.agent.dto.FilteredCommentStatsResponse;
import com.medi.backend.agent.dto.DateStat;
import com.medi.backend.agent.mapper.AgentMapper;
import com.medi.backend.auth.service.EmailService;
import com.medi.backend.filter.dto.EmailNotificationSettings;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.user.mapper.UserMapper;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.ChannelMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final FilterPreferenceService filterPreferenceService;
    private final ChannelMapper channelMapper;
    private final UserMapper userMapper;
    
    public AgentServiceImpl(
            AgentMapper agentMapper, 
            ObjectMapper objectMapper,
            EmailService emailService,
            FilterPreferenceService filterPreferenceService,
            ChannelMapper channelMapper,
            UserMapper userMapper) {
        this.agentMapper = agentMapper;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.filterPreferenceService = filterPreferenceService;
        this.channelMapper = channelMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findVideoIdByYoutubeVideoId(String youtubeVideoId) {
        return agentMapper.findVideoIdByYoutubeVideoId(youtubeVideoId);
    }

    @Override
    @Transactional
    public Integer insertFilteredComment(AgentFilteredCommentsRequest request) {
        int savedCount = 0;
        
        // 1. 요청에서 videoId 추출
        String videoId = request.getVideoId();
        if (videoId == null || videoId.isBlank()) {
            log.warn("Video ID is missing in request");
            return 0;
        }
        
        // 2. YouTube video_id → 내부 video_id 변환
        Integer internalVideoId = findVideoIdByYoutubeVideoId(videoId);
        if (internalVideoId == null) {
            log.warn("Video not found: {}", videoId);
            return 0;
        }
        
        // 3. filteredComments 처리 (status = "filtered")
        if (request.getFilteredComments() != null) {
            for (AgentFilteredCommentsRequest.CommentData comment : request.getFilteredComments()) {
                savedCount += processComment(comment, internalVideoId, "filtered", request.getAnalysisTimestamp());
            }
        }
        
        // 4. contentSuggestions 처리 (status = "content_suggestion")
        if (request.getContentSuggestions() != null) {
            for (AgentFilteredCommentsRequest.CommentData comment : request.getContentSuggestions()) {
                savedCount += processComment(comment, internalVideoId, "content_suggestion", request.getAnalysisTimestamp());
            }
        }
        
        // 5. 분석 요약 데이터 저장
        if (request.getSentimentStats() != null) {
            try {
                agentMapper.insertAnalysisSummary(
                    internalVideoId,
                    request.getVideoId(),
                    request.getChannelId(),
                    request.getSentimentStats().getNeutral(),
                    request.getSentimentStats().getFiltered(),
                    request.getSentimentStats().getSuggestion(),
                    request.getRiskSummary(),
                    request.getAnalysisTimestamp()
                );
                log.debug("Analysis summary saved for video: {}", request.getVideoId());
            } catch (Exception e) {
                log.error("Failed to save analysis summary: videoId={}", request.getVideoId(), e);
            }
            
            try {
                int neutralCount = safeInt(request.getSentimentStats().getNeutral());
                int filteredCount = safeInt(request.getSentimentStats().getFiltered());
                int suggestionCount = safeInt(request.getSentimentStats().getSuggestion());
                int totalProcessed = neutralCount + filteredCount + suggestionCount;
                
                if (totalProcessed > 0) {
                    Integer internalChannelId = null;
                    if (request.getChannelId() != null && !request.getChannelId().isBlank()) {
                        internalChannelId = agentMapper.findChannelIdByYoutubeChannelId(request.getChannelId());
                    }
                    if (internalChannelId == null) {
                        internalChannelId = agentMapper.findChannelIdByVideoId(internalVideoId);
                    }
                    
                    if (internalChannelId != null) {
                        LocalDate statDate = resolveStatDate(request.getAnalysisTimestamp());
                        agentMapper.upsertDailyCommentStats(
                            internalChannelId,
                            internalVideoId,
                            statDate,
                            totalProcessed,
                            filteredCount
                        );
                        log.debug("Daily stats upserted: channelId={}, videoId={}, date={}, total={}, filtered={}",
                            internalChannelId, internalVideoId, statDate, totalProcessed, filteredCount);
                        
                        // ✅ 시간별 통계는 기존 테이블에서 직접 조회하므로 별도 저장 불필요
                    } else {
                        log.warn("Unable to resolve channelId for daily stats: videoId={}, youtubeChannelId={}",
                            internalVideoId, request.getChannelId());
                    }
                    
                    // ✅ 6. 이메일 알림 발송 (시간별 또는 일별 체크)
                    if (filteredCount > 0 && internalChannelId != null) {
                        try {
                            checkAndSendEmailNotification(internalChannelId, request.getAnalysisTimestamp());
                        } catch (Exception emailEx) {
                            log.error("이메일 알림 발송 실패 (필터링 결과 저장은 성공): channelId={}, filteredCount={}", 
                                internalChannelId, filteredCount, emailEx);
                            // 이메일 발송 실패해도 필터링 결과 저장은 성공했으므로 예외를 던지지 않음
                        }
                    }
                } else {
                    log.debug("Skip daily stats upsert due to zero total count: videoId={}", internalVideoId);
                }
            } catch (Exception e) {
                log.error("Failed to upsert daily stats: videoId={}, youtubeChannelId={}",
                    internalVideoId, request.getChannelId(), e);
            }
        }
        
        return savedCount;
    }
    
    /**
     * 시간 단위로 반올림 (예: 14:30:00 -> 14:00:00)
     */
    private java.time.LocalDateTime resolveStatDatetime(String analysisTimestamp) {
        if (analysisTimestamp == null || analysisTimestamp.isBlank()) {
            return java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        }
        try {
            java.time.OffsetDateTime odt = OffsetDateTime.parse(analysisTimestamp);
            return odt.toLocalDateTime().withMinute(0).withSecond(0).withNano(0);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse analysisTimestamp for hourly stats, fallback to now. timestamp={}, error={}",
                analysisTimestamp, e.getMessage());
            return java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        }
    }
    
    /**
     * 이메일 알림 발송 체크 및 발송
     * - timeUnit에 따라 시간별 또는 일별로 체크
     * - HOURLY: 시간당 필터링된 댓글 개수 체크
     * - DAILY: 일별 필터링된 댓글 개수 체크
     */
    private void checkAndSendEmailNotification(Integer internalChannelId, String analysisTimestamp) {
        try {
            // 1. 채널 정보 조회 (user_id, channel_name 필요)
            YoutubeChannelDto channel = channelMapper.getOneChannelById(internalChannelId);
            if (channel == null) {
                log.warn("⚠️ [이메일 알림] 채널을 찾을 수 없음: channelId={}", internalChannelId);
                return;
            }
            
            Integer userId = channel.getUserId();
            if (userId == null) {
                log.warn("⚠️ [이메일 알림] 채널에 userId가 없음: channelId={}", internalChannelId);
                return;
            }
            
            // 2. 이메일 알림 설정 조회 (채널별 설정 우선, 없으면 전역 설정)
            com.medi.backend.filter.dto.FilterPreferenceResponse preference = null;
            Optional<com.medi.backend.filter.dto.FilterPreferenceResponse> channelPreference = 
                filterPreferenceService.getPreference(userId, internalChannelId);
            
            if (channelPreference.isPresent() && channelPreference.get().getEmailNotificationSettings() != null) {
                preference = channelPreference.get();
            } else {
                // 전역 설정 조회
                Optional<com.medi.backend.filter.dto.FilterPreferenceResponse> globalPreference = 
                    filterPreferenceService.getPreference(userId, null);
                if (globalPreference.isPresent() && globalPreference.get().getEmailNotificationSettings() != null) {
                    preference = globalPreference.get();
                }
            }
            
            // 3. 이메일 알림 설정 확인
            if (preference == null || preference.getEmailNotificationSettings() == null) {
                log.debug("💡 [이메일 알림] 이메일 알림 설정이 없음: userId={}, channelId={}", userId, internalChannelId);
                return;
            }
            
            EmailNotificationSettings emailSettings = preference.getEmailNotificationSettings();
            
            // 이메일 알림이 비활성화되어 있으면 종료
            if (emailSettings.getEnabled() == null || !emailSettings.getEnabled()) {
                log.debug("💡 [이메일 알림] 이메일 알림이 비활성화됨: userId={}, channelId={}", userId, internalChannelId);
                return;
            }
            
            // threshold 확인
            Integer threshold = emailSettings.getThreshold();
            if (threshold == null || threshold <= 0) {
                log.warn("⚠️ [이메일 알림] threshold가 설정되지 않음: userId={}, channelId={}", userId, internalChannelId);
                return;
            }
            
            // timeUnit 확인 (기본값: HOURLY)
            String timeUnit = emailSettings.getTimeUnit();
            if (timeUnit == null || timeUnit.isBlank()) {
                timeUnit = "HOURLY"; // 기본값
            }
            
            // timeUnit에 따라 필터링된 댓글 개수 확인
            int actualFilteredCount = 0;
            String timeUnitDisplay = "";
            
            if ("HOURLY".equalsIgnoreCase(timeUnit)) {
                // 시간별 체크: 현재 시간대의 필터링된 댓글 개수 조회
                java.time.LocalDateTime statDatetime = resolveStatDatetime(analysisTimestamp);
                Integer hourlyCount = agentMapper.getHourlyFilteredCount(internalChannelId, statDatetime);
                actualFilteredCount = hourlyCount != null ? hourlyCount : 0;
                timeUnitDisplay = "시간당";
                
                log.debug("📊 [이메일 알림] 시간별 체크: channelId={}, datetime={}, filteredCount={}, threshold={}", 
                    internalChannelId, statDatetime, actualFilteredCount, threshold);
            } else if ("DAILY".equalsIgnoreCase(timeUnit)) {
                // 일별 체크: 오늘 날짜의 필터링된 댓글 개수 조회
                // TODO: daily_comment_stats에서 채널별 오늘 날짜의 filtered_count 합계 조회 구현 필요
                timeUnitDisplay = "일별";
                log.warn("⚠️ [이메일 알림] DAILY 모드는 아직 완전히 구현되지 않음: channelId={}", internalChannelId);
                return; // 일별 모드는 아직 구현하지 않음
            } else {
                log.warn("⚠️ [이메일 알림] 알 수 없는 timeUnit: timeUnit={}, channelId={}", timeUnit, internalChannelId);
                return;
            }
            
            // 필터링된 댓글 개수가 threshold 미만이면 이메일 발송 안 함
            if (actualFilteredCount < threshold) {
                log.debug("💡 [이메일 알림] 필터링된 댓글 개수가 threshold 미만: {}filteredCount={}, threshold={}, userId={}, channelId={}", 
                    timeUnitDisplay, actualFilteredCount, threshold, userId, internalChannelId);
                return;
            }
            
            // 4. 수신자 이메일 주소 결정
            String recipientEmail = emailSettings.getEmail();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                // 설정에 이메일이 없으면 users 테이블의 email 사용
                com.medi.backend.user.dto.UserDTO user = userMapper.findById(userId);
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                    log.warn("⚠️ [이메일 알림] 사용자 이메일을 찾을 수 없음: userId={}, channelId={}", userId, internalChannelId);
                    return;
                }
                recipientEmail = user.getEmail();
            }
            
            // 5. 이메일 발송
            String channelName = channel.getChannelName() != null ? channel.getChannelName() : "알 수 없음";
            emailService.sendFilteredCommentNotificationEmail(
                recipientEmail,
                channelName,
                actualFilteredCount,
                threshold,
                timeUnitDisplay
            );
            
            log.info("✅ [이메일 알림] 발송 완료: userId={}, channelId={}, channelName={}, recipientEmail={}, {}filteredCount={}, threshold={}, timeUnit={}", 
                userId, internalChannelId, channelName, recipientEmail, timeUnitDisplay, actualFilteredCount, threshold, timeUnit);
            
        } catch (Exception e) {
            log.error("❌ [이메일 알림] 체크 및 발송 실패: channelId={}", internalChannelId, e);
            throw e;
        }
    }
    
    private int processComment(AgentFilteredCommentsRequest.CommentData comment, Integer videoId, String status, String analyzedAt) {
        try {
            // 1. youtube_comments 테이블에 기본 댓글 정보 저장
            Integer insertResult = agentMapper.insertFilteredComment(
                videoId,
                comment.getCommentId(),
                comment.getTextOriginal(),
                comment.getAuthorName(),
                comment.getPublishedAt(),
                comment.getLikeCount()
            );
            
            log.debug("INSERT result: insertResult={}, youtubeCommentId={}", insertResult, comment.getCommentId());
            
            if (insertResult > 0) {
                // 2. 저장된 댓글의 id 조회
                Integer commentId = agentMapper.findCommentIdByYoutubeCommentId(comment.getCommentId());
                
                log.debug("SELECT result: commentId={}, youtubeCommentId={}", commentId, comment.getCommentId());
                
                if (commentId != null) {
                    // 3. ai_comment_analysis_result 테이블에 분석 결과 저장
                    Integer analysisResult = agentMapper.insertCommentAnalysisResult(
                        commentId,
                        status,
                        comment.getReason(),
                        analyzedAt
                    );
                    log.debug("Analysis result insert: result={}, commentId={}, status={}", 
                        analysisResult, commentId, status);
                    return 1;
                } else {
                    log.warn("Failed to find comment id after insert: videoId={}, youtubeCommentId={}", 
                        videoId, comment.getCommentId());
                }
            } else {
                log.warn("INSERT failed or no rows affected: insertResult={}, youtubeCommentId={}", 
                    insertResult, comment.getCommentId());
            }
        } catch (Exception e) {
            log.error("Failed to save comment: videoId={}, commentId={}, status={}", 
                videoId, comment.getCommentId(), status, e);
        }
        return 0;
    }
    
    @Override
    @Transactional
    public Integer insertChannelProfiling(AgentProfilingRequest request) {
        try {
            // 1. 요청에서 channelId 추출
            String youtubeChannelId = request.getChannelId();
            if (youtubeChannelId == null || youtubeChannelId.isBlank()) {
                log.warn("Channel ID is missing in request");
                return 0;
            }
            
            // 2. YouTube channel_id → 내부 channel_id 변환
            Integer internalChannelId = agentMapper.findChannelIdByYoutubeChannelId(youtubeChannelId);
            if (internalChannelId == null) {
                log.warn("Channel not found: {}", youtubeChannelId);
                return 0;
            }
            
            // 3. JSON 변환
            // profileData 전체를 JSON으로 변환
            String profileDataJson = objectMapper.writeValueAsString(request.getProfileData());
            
            // commentEcosystem만 추출하여 JSON으로 변환
            String commentEcosystemJson = "{}";  // 기본값: 빈 JSON 객체
            if (request.getProfileData() != null && request.getProfileData().getCommentEcosystem() != null) {
                commentEcosystemJson = objectMapper.writeValueAsString(request.getProfileData().getCommentEcosystem());
            }
            
            // channelCommunication만 추출하여 JSON으로 변환
            String channelCommunicationJson = "{}";  // 기본값: 빈 JSON 객체
            if (request.getProfileData() != null && request.getProfileData().getChannelCommunication() != null) {
                channelCommunicationJson = objectMapper.writeValueAsString(request.getProfileData().getChannelCommunication());
            }
            
            // metadata 전체를 JSON으로 변환
            String metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            
            // 5. ai_channel_profiling 테이블에 저장
            Integer result = agentMapper.insertChannelProfiling(
                internalChannelId,
                youtubeChannelId,
                profileDataJson,
                commentEcosystemJson,
                channelCommunicationJson,
                metadataJson
            );
            
            log.info("Channel profiling saved: channelId={}, youtubeChannelId={}, result={}", 
                internalChannelId, youtubeChannelId, result);
            
            return result != null && result > 0 ? 1 : 0;
            
        } catch (Exception e) {
            log.error("Failed to save channel profiling: channelId={}", request.getChannelId(), e);
            return 0;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FilteredCommentResponse> getFilteredCommentsByVideoId(Integer videoId, Integer userId, String status) {
        log.debug("비디오별 필터링된 댓글 조회: videoId={}, userId={}, status={}", videoId, userId, status);
        return agentMapper.findFilteredCommentsByVideoId(videoId, userId, status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public AnalysisSummaryResponse getAnalysisSummaryByVideoId(Integer videoId, Integer userId) {
        log.debug("비디오별 분석 요약 조회: videoId={}, userId={}", videoId, userId);
        return agentMapper.findAnalysisSummaryByVideoId(videoId, userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FilteredCommentResponse> getFilteredCommentsByChannelId(Integer channelId, Integer userId, String status) {
        log.debug("채널별 필터링된 댓글 조회: channelId={}, userId={}, status={}", channelId, userId, status);
        return agentMapper.findFilteredCommentsByChannelId(channelId, userId, status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FilteredCommentResponse> getFilteredCommentsByUserId(Integer userId, String status) {
        log.debug("사용자별 필터링된 댓글 조회: userId={}, status={}", userId, status);
        return agentMapper.findFilteredCommentsByUserId(userId, status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public FilteredCommentStatsResponse getFilteredCommentStatsByDate(
        Integer userId,
        Integer videoId,
        Integer channelId,
        String periodType,
        String startDate,
        String endDate
    ) {
        log.debug("날짜별 필터링된 댓글 통계 조회: userId={}, videoId={}, channelId={}, periodType={}, startDate={}, endDate={}", 
            userId, videoId, channelId, periodType, startDate, endDate);
        
        // periodType 기본값 설정
        if (periodType == null || periodType.isBlank()) {
            periodType = "daily";
        }
        
        // 날짜별 통계 조회
        List<DateStat> stats = agentMapper.findFilteredCommentStatsByDate(
            userId, videoId, channelId, periodType, startDate, endDate
        );
        
        // 전체 합계 계산
        int totalFiltered = 0;
        int totalSuggestions = 0;
        int totalNormal = 0;
        
        for (DateStat stat : stats) {
            totalFiltered += stat.getFilteredCount() != null ? stat.getFilteredCount() : 0;
            totalSuggestions += stat.getSuggestionCount() != null ? stat.getSuggestionCount() : 0;
            totalNormal += stat.getNormalCount() != null ? stat.getNormalCount() : 0;
        }
        
        return FilteredCommentStatsResponse.builder()
            .periodType(periodType)
            .stats(stats)
            .totalFiltered(totalFiltered)
            .totalSuggestions(totalSuggestions)
            .totalNormal(totalNormal)
            .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<com.medi.backend.agent.dto.DailyCommentStatDto> getDailyCommentStats(
        Integer userId,
        Integer videoId,
        Integer channelId,
        String periodType,
        String startDate,
        String endDate
    ) {
        log.debug("일별 전체 댓글 통계 조회: userId={}, videoId={}, channelId={}, periodType={}, startDate={}, endDate={}", 
            userId, videoId, channelId, periodType, startDate, endDate);
        
        // periodType 기본값 설정
        if (periodType == null || periodType.isBlank()) {
            periodType = "daily";
        }
        
        // daily_comment_stats 테이블에서 조회
        List<com.medi.backend.agent.dto.DailyCommentStatDto> stats = agentMapper.findDailyCommentStats(
            userId, videoId, channelId, periodType, startDate, endDate
        );
        
        log.info("✅ 일별 전체 댓글 통계 조회 완료: userId={}, 통계 항목수={}개", 
            userId, stats != null ? stats.size() : 0);
        
        return stats;
    }
    
    private LocalDate resolveStatDate(String analysisTimestamp) {
        if (analysisTimestamp == null || analysisTimestamp.isBlank()) {
            return LocalDate.now();
        }
        try {
            return OffsetDateTime.parse(analysisTimestamp).toLocalDate();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse analysisTimestamp for daily stats, fallback to today. timestamp={}, error={}",
                analysisTimestamp, e.getMessage());
            return LocalDate.now();
        }
    }
    
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}

