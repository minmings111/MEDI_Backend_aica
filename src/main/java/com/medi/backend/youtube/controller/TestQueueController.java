package com.medi.backend.youtube.controller;

import com.medi.backend.youtube.redis.service.RedisQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Queue 디버깅용 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/test/queue")
@RequiredArgsConstructor
public class TestQueueController {

    private final RedisQueueService redisQueueService;

    /**
     * Queue 상태 조회
     * 
     * GET /api/test/queue/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getQueueStats() {
        try {
            Map<String, Long> stats = redisQueueService.getQueueStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stats", stats);
            
            log.info("[Queue] 통계 조회: {}", stats);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Queue] 통계 조회 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Queue 통계 조회 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * Queue 비우기
     * 
     * DELETE /api/test/queue/clear?type=profiling|filtering|all
     */
    @DeleteMapping("/clear")
    public ResponseEntity<?> clearQueue(@RequestParam(defaultValue = "all") String type) {
        try {
            redisQueueService.clearQueue(type);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", type + " Queue 비움");
            
            log.info("[Queue] Queue 비움: type={}", type);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Queue] Queue 삭제 실패: type={}", type, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Queue 삭제 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * Profiling Task 수동 추가 (테스트용)
     * 
     * POST /api/test/queue/profiling
     * Body: { "channelId": "UC..." }
     */
    @PostMapping("/profiling")
    public ResponseEntity<?> addProfilingTask(@RequestBody Map<String, Object> request) {
        try {
            String channelId = (String) request.get("channelId");
            
            if (channelId == null || channelId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "channelId 필수"
                ));
            }
            
            @SuppressWarnings("unchecked")
            java.util.List<String> videoIds = (java.util.List<String>) request.get("videoIds");
            
            redisQueueService.enqueueProfiling(channelId, videoIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profiling task 추가됨");
            response.put("channelId", channelId);
            
            log.info("[Queue] Profiling task 수동 추가: channelId={}", channelId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Queue] Profiling task 추가 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Task 추가 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * Filtering Task 수동 추가 (테스트용)
     * 
     * POST /api/test/queue/filtering
     * Body: { "channelId": "UC...", "videoIds": ["vid1", "vid2"] }
     */
    @PostMapping("/filtering")
    public ResponseEntity<?> addFilteringTask(@RequestBody Map<String, Object> request) {
        try {
            String channelId = (String) request.get("channelId");
            @SuppressWarnings("unchecked")
            java.util.List<String> videoIds = (java.util.List<String>) request.get("videoIds");
            
            if (channelId == null || channelId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "channelId 필수"
                ));
            }
            
            if (videoIds == null || videoIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "videoIds 필수"
                ));
            }
            
            redisQueueService.enqueueFiltering(channelId, videoIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Filtering task 추가됨");
            response.put("channelId", channelId);
            response.put("videoCount", videoIds.size());
            
            log.info("[Queue] Filtering task 수동 추가: channelId={}, videos={}", 
                channelId, videoIds.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Queue] Filtering task 추가 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Task 추가 실패: " + e.getMessage()
            ));
        }
    }
}

