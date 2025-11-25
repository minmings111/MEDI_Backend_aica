package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Step 3 예시 댓글 조회 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExampleRequest {
    
    /**
     * 선택한 카테고리 배열 (Step 1에서 선택한 것)
     * 예: ["profanity", "appearance"]
     */
    private List<String> categories;
    
    /**
     * 가져올 예시 개수 (기본값: 10)
     */
    private Integer limit;
    
    /**
     * 난이도 믹스 여부 (기본값: true)
     * - true: EASY/MEDIUM/HARD 균등 분배
     * - false: 모든 난이도 포함
     */
    private Boolean mixDifficulty;
}

