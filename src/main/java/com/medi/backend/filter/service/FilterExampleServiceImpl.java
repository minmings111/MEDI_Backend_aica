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
            if (mixDifficulty) {
                // 난이도별로 균등 분배하여 조회 (이미 섞여서 반환됨)
                examples = getExamplesByCategoryWithDifficultyMix(categories.get(0), limit);
            } else {
                // 기존 방식: 랜덤 조회
                examples = filterMapper.findExamplesByCategory(categories.get(0), limit, false, null);
            }
        } else {
            // 여러 카테고리: 총 limit개를 카테고리별로 균등 분배
            examples = getExamplesByCategoriesDistributed(categories, limit, mixDifficulty);
            
            // 여러 카테고리일 때는 각 카테고리별로 이미 섞었지만, 전체적으로 다시 한 번 섞기
            if (mixDifficulty && examples.size() >= 3) {
                examples = mixByDifficulty(examples, limit);
            }
            
            // limit 초과 방지 (혹시 모를 경우 대비)
            if (examples.size() > limit) {
                examples = examples.subList(0, limit);
                log.debug("⚠️ [예시 댓글] limit 초과로 {}개로 제한: 요청={}개, 실제={}개", 
                    limit, examples.size() + (examples.size() - limit), examples.size());
            }
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
            
            List<FilterExampleCommentDto> categoryExamples;
            if (mixDifficulty) {
                // 난이도별로 균등 분배하여 조회
                categoryExamples = getExamplesByCategoryWithDifficultyMix(category, categoryLimit);
            } else {
                // 기존 방식: 랜덤 조회
                categoryExamples = filterMapper.findExamplesByCategory(category, categoryLimit, false, null);
            }
            
            allExamples.addAll(categoryExamples);
        }
        
        return allExamples;
    }
    
    /**
     * 난이도별로 균등 분배
     * @param examples 섞을 예시 댓글 리스트
     * @param limit 최대 반환 개수 (null이면 제한 없음)
     */
    private List<FilterExampleCommentDto> mixByDifficulty(List<FilterExampleCommentDto> examples, Integer limit) {
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
        
        log.debug("📊 [예시 댓글] 난이도별 분포: EASY={}개, MEDIUM={}개, HARD={}개", 
            easy.size(), medium.size(), hard.size());
        
        // 균등 분배 (라운드 로빈)
        List<FilterExampleCommentDto> mixed = new ArrayList<>();
        int maxSize = Math.max(Math.max(easy.size(), medium.size()), hard.size());
        
        for (int i = 0; i < maxSize; i++) {
            if (limit != null && mixed.size() >= limit) break;
            if (i < easy.size()) mixed.add(easy.get(i));
            if (limit != null && mixed.size() >= limit) break;
            if (i < medium.size()) mixed.add(medium.get(i));
            if (limit != null && mixed.size() >= limit) break;
            if (i < hard.size()) mixed.add(hard.get(i));
        }
        
        log.debug("✅ [예시 댓글] 난이도 믹스 완료: 총 {}개 (limit: {})", 
            mixed.size(), limit != null ? limit : "제한없음");
        return mixed;
    }
    
    /**
     * 난이도별로 균등 분배하여 카테고리에서 예시 댓글 조회
     * 각 난이도에서 균등하게 가져와서 Java에서 섞기
     */
    private List<FilterExampleCommentDto> getExamplesByCategoryWithDifficultyMix(
            String categoryId, Integer totalLimit) {
        // 난이도별로 균등 분배 (각 난이도에서 총 limit의 1/3 + 1개씩 가져와서 부족한 경우 대비)
        int perDifficultyLimit = (int) Math.ceil(totalLimit / 3.0) + 1;
        
        log.debug("📝 [예시 댓글] 난이도별 균등 조회: category={}, totalLimit={}, perDifficulty={}", 
            categoryId, totalLimit, perDifficultyLimit);
        
        // 각 난이도별로 직접 조회
        List<FilterExampleCommentDto> easy = filterMapper.findExamplesByCategory(
            categoryId, perDifficultyLimit, false, "EASY");
            
        List<FilterExampleCommentDto> medium = filterMapper.findExamplesByCategory(
            categoryId, perDifficultyLimit, false, "MEDIUM");
            
        List<FilterExampleCommentDto> hard = filterMapper.findExamplesByCategory(
            categoryId, perDifficultyLimit, false, "HARD");
        
        log.debug("📊 [예시 댓글] 난이도별 조회 결과: EASY={}개, MEDIUM={}개, HARD={}개", 
            easy.size(), medium.size(), hard.size());
        
        // 라운드 로빈으로 섞기
        List<FilterExampleCommentDto> mixed = new ArrayList<>();
        int maxSize = Math.max(Math.max(easy.size(), medium.size()), hard.size());
        
        for (int i = 0; i < maxSize && mixed.size() < totalLimit; i++) {
            if (i < easy.size() && mixed.size() < totalLimit) mixed.add(easy.get(i));
            if (i < medium.size() && mixed.size() < totalLimit) mixed.add(medium.get(i));
            if (i < hard.size() && mixed.size() < totalLimit) mixed.add(hard.get(i));
        }
        
        log.debug("✅ [예시 댓글] 난이도 믹스 완료: 총 {}개 (요청: {}개)", mixed.size(), totalLimit);
        return mixed;
    }
}

