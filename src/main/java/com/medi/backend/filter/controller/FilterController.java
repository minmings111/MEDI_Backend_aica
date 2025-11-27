package com.medi.backend.filter.controller;

import com.medi.backend.filter.dto.ExampleRequest;
import com.medi.backend.filter.dto.FilterExampleCommentDto;
import com.medi.backend.filter.dto.FilterPreferenceRequest;
import com.medi.backend.filter.dto.FilterPreferenceResponse;
import com.medi.backend.filter.service.FilterExampleService;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final YoutubeChannelMapper youtubeChannelMapper;
    private final StringRedisTemplate stringRedisTemplate;
    
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
    
    /**
     * 에이전트용 프롬프트 조회 API
     * GET /api/filter/prompt/{channelId}
     * - 에이전트가 Redis에서 프롬프트를 못 찾았을 때 호출
     * - DB에서 조회하여 Redis에 재저장 (TTL 30일) 후 반환
     * - 인증 없이 사용 가능 (에이전트는 내부 서비스)
     * 
     * @param channelId YouTube channel ID
     * @return 프롬프트 정책 블록 (JSON 문자열)
     */
    @GetMapping("/prompt/{channelId}")
    public ResponseEntity<Map<String, Object>> getPromptForAgent(
        @PathVariable("channelId") String channelId
    ) {
        log.info("🤖 [에이전트 API] 프롬프트 조회 요청: channelId={}", channelId);
        
        try {
            // 1. YouTube channel ID로 DB channel 정보 조회
            YoutubeChannelDto channel = youtubeChannelMapper.findByYoutubeChannelId(channelId);
            if (channel == null) {
                log.warn("⚠️ [에이전트 API] 채널을 찾을 수 없음: channelId={}", channelId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Channel not found");
                errorResponse.put("channelId", channelId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            Integer userId = channel.getUserId();
            Integer channelDbId = channel.getId();
            
            // 2. DB에서 프롬프트 조회
            String policyBlock = filterPreferenceService.buildPromptPolicyBlock(userId, channelDbId);
            
            // 3. 전역 설정 조회 (채널별 설정이 없을 경우)
            if (policyBlock == null || policyBlock.isEmpty()) {
                policyBlock = filterPreferenceService.buildPromptPolicyBlock(userId, null);
            }
            
            // 4. 프롬프트가 없으면 기본 프롬프트 사용
            if (policyBlock == null || policyBlock.isEmpty()) {
                policyBlock = getDefaultPolicyBlock();
                log.warn("⚠️ [에이전트 API] 사용자 설정 프롬프트 없음. 기본 프롬프트 사용: channelId={}", channelId);
            }
            
            // 5. Redis에 저장 (TTL 30일) - 다음번에는 Redis에서 바로 읽을 수 있도록
            String formRedisKey = "channel:" + channelId + ":form";
            stringRedisTemplate.opsForValue().set(formRedisKey, policyBlock, 
                java.time.Duration.ofDays(30));
            
            log.info("✅ [에이전트 API] 프롬프트 조회 및 Redis 저장 완료: channelId={}, length={}자", 
                channelId, policyBlock.length());
            
            // 6. 응답 반환
            Map<String, Object> response = new HashMap<>();
            response.put("channelId", channelId);
            response.put("policyBlock", policyBlock);
            response.put("source", "database");  // DB에서 조회했음을 표시
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [에이전트 API] 프롬프트 조회 실패: channelId={}", channelId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get prompt");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 기본 프롬프트 정책 블록 반환 (사용자 설정이 없을 때 사용)
     */
    private String getDefaultPolicyBlock() {
        try {
            Map<String, Object> defaultPolicy = new HashMap<>();
            defaultPolicy.put("Step1_카테고리선택", List.of(
                "profanity", "hate_speech", "personal_attack", 
                "appearance", "sexual", "spam"
            ));
            defaultPolicy.put("Step2_키워드입력", null);
            Map<String, Object> step3Map = new HashMap<>();
            step3Map.put("few_shot_examples", new HashMap<>());
            step3Map.put("user_selected_examples", Map.of(
                "dislike", List.of(),
                "allow", List.of()
            ));
            defaultPolicy.put("Step3_예시라벨링", step3Map);
            
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.writeValueAsString(defaultPolicy);
        } catch (Exception e) {
            log.error("❌ 기본 프롬프트 생성 실패", e);
            return "{\"Step1_카테고리선택\":[],\"Step2_키워드입력\":null,\"Step3_예시라벨링\":{\"few_shot_examples\":{},\"user_selected_examples\":{\"dislike\":[],\"allow\":[]}}}";
        }
    }
}

