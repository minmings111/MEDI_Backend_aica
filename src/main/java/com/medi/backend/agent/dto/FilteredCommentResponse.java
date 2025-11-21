package com.medi.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 필터링된 댓글 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilteredCommentResponse {
    
    // 댓글 기본 정보
    private Integer commentId;
    private String youtubeCommentId;
    private String commentText;
    private String commenterName;
    private LocalDateTime publishedAt;
    private Long likeCount;
    
    // 비디오 정보
    private Integer videoId;
    private String youtubeVideoId;
    private String videoTitle;
    
    // 채널 정보
    private Integer channelId;
    private String youtubeChannelId;
    private String channelName;
    
    // AI 분석 결과
    private String status;  // filtered, content_suggestion, normal
    private String reason;
    private LocalDateTime analyzedAt;
    private String harmfulnessLevel;  // LOW, MEDIUM, HIGH
    private String detectionSource;  // AI_MODEL, USER_KEYWORD, USER_CONTEXT
}

