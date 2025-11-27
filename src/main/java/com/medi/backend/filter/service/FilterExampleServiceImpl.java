package com.medi.backend.filter.service;

import com.medi.backend.filter.dto.ExampleRequest;
import com.medi.backend.filter.dto.FilterExampleCommentDto;
import com.medi.backend.filter.mapper.FilterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 예시 댓글 조회 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilterExampleServiceImpl implements FilterExampleService {
    
    private final FilterMapper filterMapper;
    
    @Override
    public List<FilterExampleCommentDto> getExamples(ExampleRequest request) {
        Integer limit = request.getLimit() != null ? request.getLimit() : 10;
        Boolean mixDifficulty = request.getMixDifficulty() != null ? request.getMixDifficulty() : true;
        
        List<String> categories = request.getCategories();
        
        // 카테고리가 없으면 공통 예시만 반환
        if (categories == null || categories.isEmpty()) {
            log.debug("📝 [예시 댓글] 카테고리 없음 → 공통 예시만 조회: limit={}", limit);
            return filterMapper.findCommonExamples(limit);
        }
        
        // 카테고리별 예시 조회 (균등 분배)
        log.debug("📝 [예시 댓글] 카테고리별 조회: categories={}, limit={}, mixDifficulty={}", 
            categories, limit, mixDifficulty);
        
        List<FilterExampleCommentDto> examples;
        
        // ✅ 카테고리별 균등 분배 로직
        if (categories.size() == 1) {
            // 1개 카테고리: 해당 카테고리에서 limit개 조회
            examples = filterMapper.findExamplesByCategory(categories.get(0), limit, mixDifficulty);
        } else {
            // 여러 카테고리: 총 limit개를 카테고리별로 균등 분배
            examples = getExamplesByCategoriesDistributed(categories, limit, mixDifficulty);
        }
        
        // 난이도 믹스가 활성화된 경우, EASY/MEDIUM/HARD 균등 분배
        if (mixDifficulty && examples.size() >= 3) {
            examples = mixByDifficulty(examples);
        }
        
        log.info("✅ [예시 댓글] 조회 완료: {}개 (카테고리: {}개)", examples.size(), categories.size());
        return examples;
    }
    
    /**
     * 여러 카테고리에서 균등 분배하여 예시 댓글 조회
     * 예: 2개 카테고리, limit=10 → 각 5개씩
     * 예: 3개 카테고리, limit=10 → 4개, 3개, 3개
     */
    private List<FilterExampleCommentDto> getExamplesByCategoriesDistributed(
            List<String> categories, Integer totalLimit, Boolean mixDifficulty) {
        List<FilterExampleCommentDto> allExamples = new ArrayList<>();
        int categoryCount = categories.size();
        
        // 카테고리별 개수 계산 (균등 분배)
        int baseCount = totalLimit / categoryCount;  // 기본 개수
        int remainder = totalLimit % categoryCount;   // 나머지
        
        log.debug("📊 [예시 댓글] 카테고리별 분배: 총 {}개, 카테고리 {}개 → 기본 {}개, 나머지 {}개", 
            totalLimit, categoryCount, baseCount, remainder);
        
        // 각 카테고리별로 조회
        for (int i = 0; i < categoryCount; i++) {
            String category = categories.get(i);
            // 나머지가 있으면 앞쪽 카테고리부터 1개씩 추가
            int categoryLimit = baseCount + (i < remainder ? 1 : 0);
            
            log.debug("📝 [예시 댓글] 카테고리 '{}'에서 {}개 조회", category, categoryLimit);
            
            List<FilterExampleCommentDto> categoryExamples = 
                filterMapper.findExamplesByCategory(category, categoryLimit, mixDifficulty);
            
            allExamples.addAll(categoryExamples);
        }
        
        return allExamples;
    }
    
    /**
     * 난이도별로 균등 분배
     */
    private List<FilterExampleCommentDto> mixByDifficulty(List<FilterExampleCommentDto> examples) {
        // 난이도별로 그룹화
        List<FilterExampleCommentDto> easy = examples.stream()
            .filter(e -> "EASY".equals(e.getDifficultyLevel()))
            .collect(Collectors.toList());
        List<FilterExampleCommentDto> medium = examples.stream()
            .filter(e -> "MEDIUM".equals(e.getDifficultyLevel()))
            .collect(Collectors.toList());
        List<FilterExampleCommentDto> hard = examples.stream()
            .filter(e -> "HARD".equals(e.getDifficultyLevel()))
            .collect(Collectors.toList());
        
        // 균등 분배 (라운드 로빈)
        List<FilterExampleCommentDto> mixed = new ArrayList<>();
        int maxSize = Math.max(Math.max(easy.size(), medium.size()), hard.size());
        
        for (int i = 0; i < maxSize; i++) {
            if (i < easy.size()) mixed.add(easy.get(i));
            if (i < medium.size()) mixed.add(medium.get(i));
            if (i < hard.size()) mixed.add(hard.get(i));
        }
        
        return mixed;
    }
}

