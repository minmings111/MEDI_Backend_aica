package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

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
    private String userFilteringDescription;
    private List<String> dislikeExamples;
    private List<String> allowExamples;
    
    private EmailNotificationSettings emailNotificationSettings;
    
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

