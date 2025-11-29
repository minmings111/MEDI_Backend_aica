package com.medi.backend.youtube.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 채널 위협 분석 결과 DTO (DB 매핑)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelThreatAnalysisDto {

    private Integer id;
    private Integer channelId;

    // FastAPI Agent JSON 원본 필드 (NULL 허용)
    private String channelName;
    private LocalDateTime generatedAt;
    private Integer totalComments;
    private Integer criticalCount;
    private Integer highCount;

    // JSON 컬럼 (TypeHandler로 자동 변환)
    private Map<String, Object> section1ThreatIntelligence;
    private Map<String, Object> section2DefenseStrategy;

    // 메타데이터 (DB 관리용)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

