package com.medi.backend.filter.controller;

import com.medi.backend.filter.dto.ExampleRequest;
import com.medi.backend.filter.dto.FilterExampleCommentDto;
import com.medi.backend.filter.dto.FilterPreferenceRequest;
import com.medi.backend.filter.dto.FilterPreferenceResponse;
import com.medi.backend.filter.service.FilterExampleService;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.global.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 필터링 설정 및 예시 댓글 API
 */
@Slf4j
@RestController
@RequestMapping("/api/filter")
@RequiredArgsConstructor
public class FilterController {
    
    private final FilterExampleService filterExampleService;
    private final FilterPreferenceService filterPreferenceService;
    private final AuthUtil authUtil;
    
    /**
     * Step 3: 예시 댓글 조회
     * POST /api/filter/examples
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/examples")
    public ResponseEntity<List<FilterExampleCommentDto>> getExamples(@RequestBody ExampleRequest request) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📝 [필터 API] 예시 댓글 조회: userId={}, categories={}", 
            userId, request.getCategories());
        
        List<FilterExampleCommentDto> examples = filterExampleService.getExamples(request);
        return ResponseEntity.ok(examples);
    }
    
    /**
     * 필터링 설정 저장 (Step 1, 2, 3 완료 후)
     * POST /api/filter/preferences
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/preferences")
    public ResponseEntity<FilterPreferenceResponse> savePreference(@RequestBody FilterPreferenceRequest request) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("💾 [필터 API] 설정 저장: userId={}, channelId={}", userId, request.getChannelId());
        
        FilterPreferenceResponse response = filterPreferenceService.savePreference(userId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 필터링 설정 조회 (전역 또는 채널별)
     * GET /api/filter/preferences?channelId={channelId}
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/preferences")
    public ResponseEntity<FilterPreferenceResponse> getPreference(
        @RequestParam(required = false) Integer channelId
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.debug("📖 [필터 API] 설정 조회: userId={}, channelId={}", userId, channelId);
        
        Optional<FilterPreferenceResponse> response = filterPreferenceService.getPreference(userId, channelId);
        
        if (response.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        return ResponseEntity.ok(response.get());
    }
}

