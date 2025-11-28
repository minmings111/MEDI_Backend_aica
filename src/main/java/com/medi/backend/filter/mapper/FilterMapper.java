package com.medi.backend.filter.mapper;

import com.medi.backend.filter.dto.FilterExampleCommentDto;
import com.medi.backend.filter.dto.UserFilterPreferenceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 필터링 설정 및 예시 댓글 Mapper
 */
@Mapper
public interface FilterMapper {
    
    // ========== UserFilterPreference 관련 ==========
    
    /**
     * 사용자 필터링 설정 조회 (전역 또는 채널별)
     * - channelId가 null이면 전역 설정 조회
     * - channelId가 있으면 해당 채널 설정 조회
     */
    UserFilterPreferenceDto findPreferenceByUserIdAndChannelId(
        @Param("userId") Integer userId,
        @Param("channelId") Integer channelId
    );
    
    /**
     * 필터링 설정 저장 (INSERT 또는 UPDATE)
     */
    int upsertPreference(UserFilterPreferenceDto preference);
    
    /**
     * 필터링 설정 삭제 (소프트 삭제: is_active = false)
     */
    int deactivatePreference(
        @Param("userId") Integer userId,
        @Param("channelId") Integer channelId
    );
    
    // ========== FilterExampleComment 관련 ==========
    
    /**
     * 카테고리별 예시 댓글 조회
     * - categories: 조회할 카테고리 배열
     * - limit: 최대 개수
     * - mixDifficulty: 난이도 믹스 여부
     */
    List<FilterExampleCommentDto> findExamplesByCategories(
        @Param("categories") List<String> categories,
        @Param("limit") Integer limit,
        @Param("mixDifficulty") Boolean mixDifficulty
    );
    
    /**
     * 단일 카테고리 예시 댓글 조회
     * - categoryId: 조회할 카테고리 ID
     * - limit: 최대 개수
     * - mixDifficulty: 난이도 믹스 여부
     * - difficultyLevel: 특정 난이도만 조회 (선택적, null이면 모든 난이도)
     */
    List<FilterExampleCommentDto> findExamplesByCategory(
        @Param("categoryId") String categoryId,
        @Param("limit") Integer limit,
        @Param("mixDifficulty") Boolean mixDifficulty,
        @Param("difficultyLevel") String difficultyLevel
    );
    
    /**
     * 공통 예시 댓글 조회 (category_id = 'common')
     */
    List<FilterExampleCommentDto> findCommonExamples(
        @Param("limit") Integer limit
    );
    
    /**
     * 카테고리별 Few-shot 예시 조회 (프롬프트용)
     * - 각 카테고리별로 block/allow 균형있게 조회
     * - limit: 카테고리당 최대 개수
     */
    List<FilterExampleCommentDto> findFewShotExamplesByCategory(
        @Param("categoryId") String categoryId,
        @Param("limit") Integer limit
    );
    
    /**
     * 예시 댓글 사용 횟수 증가
     */
    int incrementUsageCount(@Param("id") Integer id);
}

