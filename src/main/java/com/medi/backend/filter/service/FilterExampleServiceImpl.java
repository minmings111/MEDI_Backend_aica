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
        
        // 카테고리별 예시 조회
        log.debug("📝 [예시 댓글] 카테고리별 조회: categories={}, limit={}, mixDifficulty={}", 
            categories, limit, mixDifficulty);
        
        List<FilterExampleCommentDto> examples = filterMapper.findExamplesByCategories(
            categories, limit, mixDifficulty
        );
        
        // 난이도 믹스가 활성화된 경우, EASY/MEDIUM/HARD 균등 분배
        if (mixDifficulty && examples.size() >= 3) {
            examples = mixByDifficulty(examples);
        }
        
        log.info("✅ [예시 댓글] 조회 완료: {}개", examples.size());
        return examples;
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

