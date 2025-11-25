package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 예시 댓글 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterExampleCommentDto {
    
    private Integer id;
    private String categoryId;
    private String commentText;
    private String suggestedLabel;  // "allow" or "block"
    private String difficultyLevel; // "EASY", "MEDIUM", "HARD"
    private Integer usageCount;
    private Boolean isActive;
}

