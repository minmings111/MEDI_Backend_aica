package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * 필터링 설정 저장 요청 DTO
 * - Step 1, 2, 3의 모든 데이터를 포함
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterPreferenceRequest {
    
    /**
     * 채널 ID (선택)
     * - null이면 전역 설정
     * - 값이 있으면 해당 채널별 설정
     */
    private Integer channelId;
    
    /**
     * Step 1: 선택한 카테고리 배열
     * 예: ["profanity", "appearance", "personal_attack"]
     */
    private List<String> selectedCategories;
    
    /**
     * Step 2: 카테고리별 키워드 맵
     * 예: {"profanity": ["ㅅㅂ", "병X"], "appearance": ["못생겼다"]}
     */
    private Map<String, List<String>> customRuleKeywords;
    
    /**
     * Step 3: 숨기고 싶은 댓글 예시
     * 예: ["왜 이렇게 못하냐", "와 못생겼다"]
     */
    private List<String> dislikeExamples;
    
    /**
     * Step 3: 괜찮은 댓글 예시
     * 예: ["컨디션 안 좋아보이네"]
     */
    private List<String> allowExamples;
}

