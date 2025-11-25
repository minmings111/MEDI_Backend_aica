package com.medi.backend.filter.service;

import com.medi.backend.filter.dto.ExampleRequest;
import com.medi.backend.filter.dto.FilterExampleCommentDto;
import java.util.List;

/**
 * 예시 댓글 조회 서비스
 */
public interface FilterExampleService {
    
    /**
     * Step 3에서 사용할 예시 댓글 조회
     * - 선택한 카테고리 + 공통 예시
     * - 난이도 믹스 옵션 지원
     */
    List<FilterExampleCommentDto> getExamples(ExampleRequest request);
}

