package com.medi.backend.filter.service;

import com.medi.backend.filter.dto.FilterPreferenceRequest;
import com.medi.backend.filter.dto.FilterPreferenceResponse;
import java.util.Optional;

/**
 * 필터링 설정 관리 서비스
 */
public interface FilterPreferenceService {
    
    /**
     * 필터링 설정 저장 (Step 1, 2, 3 완료 후)
     */
    FilterPreferenceResponse savePreference(Integer userId, FilterPreferenceRequest request);
    
    /**
     * 필터링 설정 조회 (전역 또는 채널별)
     */
    Optional<FilterPreferenceResponse> getPreference(Integer userId, Integer channelId);
    
    /**
     * 프롬프트 정책 블록 생성 (LLM용)
     * - Redis 캐싱 적용
     */
    String buildPromptPolicyBlock(Integer userId, Integer channelId);
}

