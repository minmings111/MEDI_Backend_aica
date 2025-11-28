package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 필터링 설정 DTO (DB 매핑용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterPreferenceDto {
    
    private Integer id;
    private Integer userId;
    private Integer channelId;
    
    /**
     * JSON 컬럼 → List<String> 변환
     */
    private List<String> selectedCategories;
    
    /**
     * TEXT 컬럼 → String 변환
     */
    private String userFilteringDescription;
    
    /**
     * JSON 컬럼 → List<String> 변환
     */
    private List<String> dislikeExamples;
    
    /**
     * JSON 컬럼 → List<String> 변환
     */
    private List<String> allowExamples;
    
    /**
     * JSON 컬럼 → EmailNotificationSettings 변환
     * 예: {"enabled": true, "threshold": 10, "email": "user@example.com"}
     */
    private EmailNotificationSettings emailNotificationSettings;
    
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

