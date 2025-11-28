package com.medi.backend.filter.service;

import com.medi.backend.filter.dto.ExampleRequest;
import com.medi.backend.filter.dto.FilterExampleCommentDto;
import com.medi.backend.filter.mapper.FilterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
            List<FilterExampleCommentDto> commonExamples = filterMapper.findCommonExamples(limit);
            // 공통 예시도 중복 제거
            commonExamples = removeDuplicates(commonExamples);
            // limit 확인
            if (commonExamples.size() > limit) {
                commonExamples = commonExamples.subList(0, limit);
            }
            return commonExamples;
        }
        
        // 카테고리별 예시 조회 (균등 분배)
        log.debug("📝 [예시 댓글] 카테고리별 조회: categories={}, limit={}, mixDifficulty={}", 
            categories, limit, mixDifficulty);
        
        List<FilterExampleCommentDto> examples;
        
        // ✅ 카테고리별 균등 분배 로직
        if (categories.size() == 1) {
            // 1개 카테고리: 해당 카테고리에서 limit개 조회
            // (단일 카테고리에서 조회하므로 중복 제거 불필요 - DB에서 이미 중복 없이 조회)
            if (mixDifficulty) {
                // 난이도별로 균등 분배하여 조회 (이미 섞여서 반환됨)
                examples = getExamplesByCategoryWithDifficultyMix(categories.get(0), limit);
            } else {
                // 기존 방식: 랜덤 조회
                examples = filterMapper.findExamplesByCategory(categories.get(0), limit, false, null);
            }
        } else {
            // 여러 카테고리: 총 limit개를 카테고리별로 균등 분배
            // (여러 카테고리에서 합칠 때 중복 가능하므로 중복 제거 필요)
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
        
        // 중복 제거 후 limit 적용 (요청한 개수만큼 반환)
        if (examples.size() > limit) {
            examples = examples.subList(0, limit);
            log.debug("📏 [예시 댓글] 중복 제거 후 limit 적용: {}개로 제한", limit);
        } else if (examples.size() < limit) {
            // 중복 제거 후 부족한 경우 추가 조회 (중복 제거를 고려하여 여유있게 조회)
            int shortage = limit - examples.size();
            log.debug("📝 [예시 댓글] 중복 제거 후 부족: 현재={}개, 필요={}개, 추가 조회 필요={}개", 
                examples.size(), limit, shortage);
            
            // 추가 조회를 위해 더 많이 가져오기 (중복 가능성 고려)
            int additionalLimit = shortage * 2; // 중복을 고려하여 2배로 조회
            
            List<FilterExampleCommentDto> additionalExamples = fetchAdditionalExamples(
                categories, additionalLimit, mixDifficulty, examples);
            
            // 기존 예시와 합치기
            examples.addAll(additionalExamples);
            
            // 합친 후 중복 제거 (기존 + 추가 조회 결과 합칠 때 중복 가능)
            examples = removeDuplicates(examples);
            
            // limit 적용
            if (examples.size() > limit) {
                examples = examples.subList(0, limit);
            }
            
            log.debug("✅ [예시 댓글] 추가 조회 완료: 최종={}개 (요청: {}개)", examples.size(), limit);
        }
        
        // 최종 limit 확인
        if (examples.size() > limit) {
            examples = examples.subList(0, limit);
        }
        
        log.info("✅ [예시 댓글] 조회 완료: {}개 (카테고리: {}개, 요청: {}개)", 
            examples.size(), categories.size(), limit);
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
        
        // 여러 카테고리에서 합친 후 중복 제거
        allExamples = removeDuplicates(allExamples);
        
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
        
        // (이미 중복 제거된 리스트를 섞는 것이므로 중복 제거 불필요)
        
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
        
        // (같은 카테고리에서 난이도별로 조회했으므로 중복 없음 - 중복 제거 불필요)
        
        log.debug("✅ [예시 댓글] 난이도 믹스 완료: 총 {}개 (요청: {}개)", mixed.size(), totalLimit);
        return mixed;
    }
    
    /**
     * 중복된 예시 댓글 제거 (id 기준)
     * 순서를 유지하기 위해 LinkedHashSet 사용
     * 
     * @param examples 예시 댓글 리스트
     * @return 중복 제거된 예시 댓글 리스트
     */
    private List<FilterExampleCommentDto> removeDuplicates(List<FilterExampleCommentDto> examples) {
        if (examples == null || examples.isEmpty()) {
            return examples;
        }
        
        int originalSize = examples.size();
        Set<Integer> seenIds = new LinkedHashSet<>();
        List<FilterExampleCommentDto> uniqueExamples = new ArrayList<>();
        
        for (FilterExampleCommentDto example : examples) {
            if (example != null && example.getId() != null) {
                if (!seenIds.contains(example.getId())) {
                    seenIds.add(example.getId());
                    uniqueExamples.add(example);
                } else {
                    log.debug("🔄 [예시 댓글] 중복 제거: id={}, commentText={}", 
                        example.getId(), 
                        example.getCommentText() != null && example.getCommentText().length() > 50 
                            ? example.getCommentText().substring(0, 50) + "..." 
                            : example.getCommentText());
                }
            } else {
                // id가 null인 경우도 포함 (혹시 모를 경우 대비)
                uniqueExamples.add(example);
            }
        }
        
        int removedCount = originalSize - uniqueExamples.size();
        if (removedCount > 0) {
            log.info("🔄 [예시 댓글] 중복 제거 완료: 원본={}개, 제거={}개, 결과={}개", 
                originalSize, removedCount, uniqueExamples.size());
        }
        
        return uniqueExamples;
    }
    
    /**
     * 중복 제거 후 부족한 경우 추가 예시 댓글 조회
     * 
     * @param categories 카테고리 리스트
     * @param additionalLimit 추가로 조회할 개수
     * @param mixDifficulty 난이도 믹스 여부
     * @param existingExamples 기존에 조회된 예시 리스트 (중복 체크용)
     * @return 추가 조회된 예시 댓글 리스트
     */
    private List<FilterExampleCommentDto> fetchAdditionalExamples(
            List<String> categories, Integer additionalLimit, Boolean mixDifficulty,
            List<FilterExampleCommentDto> existingExamples) {
        
        // 기존 예시의 ID를 Set으로 변환 (중복 체크용)
        Set<Integer> existingIds = existingExamples.stream()
            .filter(e -> e != null && e.getId() != null)
            .map(FilterExampleCommentDto::getId)
            .collect(Collectors.toSet());
        
        List<FilterExampleCommentDto> additionalExamples = new ArrayList<>();
        
        if (categories.size() == 1) {
            // 1개 카테고리: 추가 조회
            if (mixDifficulty) {
                additionalExamples = getExamplesByCategoryWithDifficultyMix(
                    categories.get(0), additionalLimit);
            } else {
                additionalExamples = filterMapper.findExamplesByCategory(
                    categories.get(0), additionalLimit, false, null);
            }
        } else {
            // 여러 카테고리: 균등 분배하여 추가 조회
            int categoryCount = categories.size();
            int baseCount = additionalLimit / categoryCount;
            int remainder = additionalLimit % categoryCount;
            
            for (int i = 0; i < categoryCount; i++) {
                String category = categories.get(i);
                int categoryLimit = baseCount + (i < remainder ? 1 : 0);
                
                List<FilterExampleCommentDto> categoryExamples;
                if (mixDifficulty) {
                    categoryExamples = getExamplesByCategoryWithDifficultyMix(category, categoryLimit);
                } else {
                    categoryExamples = filterMapper.findExamplesByCategory(category, categoryLimit, false, null);
                }
                
                additionalExamples.addAll(categoryExamples);
            }
            // 여러 카테고리에서 합칠 때 중복 가능하므로 중복 제거
            additionalExamples = removeDuplicates(additionalExamples);
        }
        
        // 기존에 있는 ID는 제외
        additionalExamples = additionalExamples.stream()
            .filter(e -> e == null || e.getId() == null || !existingIds.contains(e.getId()))
            .collect(Collectors.toList());
        
        // (기존 ID 제외 후에는 중복 제거 불필요 - 이미 제외했고, 여러 카테고리에서 합칠 때 이미 중복 제거함)
        
        log.debug("📝 [예시 댓글] 추가 조회 결과: {}개 (기존 제외 후)", additionalExamples.size());
        return additionalExamples;
    }
}

