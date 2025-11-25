package com.medi.backend.filter.service;

import com.medi.backend.filter.dto.FilterPreferenceRequest;
import com.medi.backend.filter.dto.FilterPreferenceResponse;
import com.medi.backend.filter.dto.UserFilterPreferenceDto;
import com.medi.backend.filter.mapper.FilterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 필터링 설정 관리 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilterPreferenceServiceImpl implements FilterPreferenceService {
    
    private final FilterMapper filterMapper;
    
    // 카테고리 ID → 한글명 매핑
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
        "profanity", "욕설·비속어",
        "hate_speech", "혐오·차별 발언",
        "personal_attack", "인신공격·모욕",
        "appearance", "외모·신체 비하",
        "sexual", "성적 발언·희롱",
        "spam", "스팸·광고·도배",
        "common", "공통"
    );
    
    @Override
    @Transactional
    @CacheEvict(value = "filterPrompt", key = "#userId + ':' + (#request.channelId != null ? #request.channelId : 'global')")
    public FilterPreferenceResponse savePreference(Integer userId, FilterPreferenceRequest request) {
        log.info("💾 [필터 설정] 저장 시작: userId={}, channelId={}", userId, request.getChannelId());
        
        // DTO 변환
        UserFilterPreferenceDto dto = new UserFilterPreferenceDto();
        dto.setUserId(userId);
        dto.setChannelId(request.getChannelId());
        dto.setSelectedCategories(request.getSelectedCategories());
        dto.setCustomRuleKeywords(request.getCustomRuleKeywords());
        dto.setDislikeExamples(request.getDislikeExamples());
        dto.setAllowExamples(request.getAllowExamples());
        dto.setIsActive(true);
        
        // DB 저장 (UPSERT)
        filterMapper.upsertPreference(dto);
        
        log.info("✅ [필터 설정] 저장 완료: id={}, userId={}, channelId={}", 
            dto.getId(), userId, request.getChannelId());
        
        // 응답 생성
        return toResponse(dto);
    }
    
    @Override
    public Optional<FilterPreferenceResponse> getPreference(Integer userId, Integer channelId) {
        log.debug("📖 [필터 설정] 조회: userId={}, channelId={}", userId, channelId);
        
        UserFilterPreferenceDto dto = filterMapper.findPreferenceByUserIdAndChannelId(userId, channelId);
        
        if (dto == null) {
            log.debug("⚠️ [필터 설정] 조회 결과 없음: userId={}, channelId={}", userId, channelId);
            return Optional.empty();
        }
        
        return Optional.of(toResponse(dto));
    }
    
    @Override
    @Cacheable(value = "filterPrompt", key = "#userId + ':' + (#channelId != null ? #channelId : 'global')", unless = "#result == null or #result.isEmpty()")
    public String buildPromptPolicyBlock(Integer userId, Integer channelId) {
        log.debug("📝 [프롬프트] 정책 블록 생성 시작: userId={}, channelId={}", userId, channelId);
        
        // 설정 조회
        UserFilterPreferenceDto preference = filterMapper.findPreferenceByUserIdAndChannelId(userId, channelId);
        
        if (preference == null || !Boolean.TRUE.equals(preference.getIsActive())) {
            log.debug("⚠️ [프롬프트] 설정 없음 또는 비활성화: userId={}, channelId={}", userId, channelId);
            return null;
        }
        
        // ✅ Redis에 저장할 Question-Answer 쌍 구조 생성
        Map<String, Object> policyMap = new HashMap<>();
        
        // Step 1: 카테고리 선택 (항상 값이 있음)
        List<String> categories = preference.getSelectedCategories();
        policyMap.put("Step1_카테고리선택", categories != null ? categories : new ArrayList<>());
        
        // Step 2: 카테고리별 키워드 입력 (null 가능)
        Map<String, List<String>> keywords = preference.getCustomRuleKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            policyMap.put("Step2_키워드입력", keywords);
        } else {
            policyMap.put("Step2_키워드입력", null);
        }
        
        // Step 3: Few-shot 예시 + 사용자 선택 예시
        Map<String, Object> step3Map = new HashMap<>();
        
        // ✅ 3-1. 카테고리별 Few-shot 예시 (DB에서 조회)
        Map<String, List<Map<String, String>>> fewShotExamples = new HashMap<>();
        if (categories != null && !categories.isEmpty()) {
            for (String categoryId : categories) {
                // 카테고리별로 10개씩 Few-shot 예시 조회
                List<com.medi.backend.filter.dto.FilterExampleCommentDto> examples = 
                    filterMapper.findFewShotExamplesByCategory(categoryId, 10);
                
                // JSON 형태로 변환
                List<Map<String, String>> categoryExamples = new ArrayList<>();
                for (com.medi.backend.filter.dto.FilterExampleCommentDto example : examples) {
                    Map<String, String> exampleMap = new HashMap<>();
                    exampleMap.put("comment", example.getCommentText());
                    exampleMap.put("label", example.getSuggestedLabel()); // "block" or "allow"
                    categoryExamples.add(exampleMap);
                }
                
                if (!categoryExamples.isEmpty()) {
                    fewShotExamples.put(categoryId, categoryExamples);
                }
            }
        }
        step3Map.put("few_shot_examples", fewShotExamples);
        
        // ✅ 3-2. 사용자가 직접 선택한 예시
        List<String> dislikeExamples = preference.getDislikeExamples();
        List<String> allowExamples = preference.getAllowExamples();
        
        Map<String, List<String>> userSelectedExamples = new HashMap<>();
        userSelectedExamples.put("dislike", dislikeExamples != null ? dislikeExamples : new ArrayList<>());
        userSelectedExamples.put("allow", allowExamples != null ? allowExamples : new ArrayList<>());
        step3Map.put("user_selected_examples", userSelectedExamples);
        
        policyMap.put("Step3_예시라벨링", step3Map);
        
        // JSON 문자열로 변환하여 반환 (Redis에 저장될 형태)
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String result = objectMapper.writeValueAsString(policyMap);
            log.debug("✅ [프롬프트] 정책 블록 생성 완료 (JSON): 길이={}자, 카테고리={}개, Few-shot={}개", 
                result.length(), 
                categories != null ? categories.size() : 0,
                fewShotExamples.size());
            return result;
        } catch (Exception e) {
            log.error("❌ [프롬프트] JSON 변환 실패: userId={}, channelId={}", userId, channelId, e);
            return null;
        }
    }
    
    private FilterPreferenceResponse toResponse(UserFilterPreferenceDto dto) {
        FilterPreferenceResponse response = new FilterPreferenceResponse();
        response.setId(dto.getId());
        response.setUserId(dto.getUserId());
        response.setChannelId(dto.getChannelId());
        response.setSelectedCategories(dto.getSelectedCategories());
        response.setCustomRuleKeywords(dto.getCustomRuleKeywords());
        response.setDislikeExamples(dto.getDislikeExamples());
        response.setAllowExamples(dto.getAllowExamples());
        response.setIsActive(dto.getIsActive());
        response.setCreatedAt(dto.getCreatedAt());
        response.setUpdatedAt(dto.getUpdatedAt());
        return response;
    }
}

