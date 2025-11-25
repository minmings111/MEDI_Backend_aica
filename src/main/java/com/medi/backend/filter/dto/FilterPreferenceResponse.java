package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 필터링 설정 조회 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterPreferenceResponse {
    
    private Integer id;
    private Integer userId;
    private Integer channelId;
    
    private List<String> selectedCategories;
    private Map<String, List<String>> customRuleKeywords;
    private List<String> dislikeExamples;
    private List<String> allowExamples;
    
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

