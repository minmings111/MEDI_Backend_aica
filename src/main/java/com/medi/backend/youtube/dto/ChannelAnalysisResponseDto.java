package com.medi.backend.youtube.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 채널 메타데이터 API 응답 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAnalysisResponseDto {

    // 기본 정보
    private Integer id;
    private Integer channelId;
    private String channelName;
    private LocalDateTime generatedAt;
    private Integer totalComments;
    private Integer criticalCount;
    private Integer highCount;

    // 계산된 값
    private String riskLevel;
    private Boolean hasSectionThreatIntelligence;
    private Boolean hasSectionDefenseStrategy;
}

