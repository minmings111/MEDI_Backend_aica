package com.medi.backend.youtube.controller;

import com.medi.backend.youtube.dto.ChannelAgentRequestDto;
import com.medi.backend.youtube.dto.ChannelAnalysisResponseDto;
import com.medi.backend.youtube.service.ChannelThreatAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/youtube/analysis/channel")
@RequiredArgsConstructor
public class ChannelAnalysisController {

    private final ChannelThreatAnalysisService analysisService;

    /**
     * FastAPI Agent → Spring Boot 저장 API (채널 기준)
     * 
     * channelId는 두 가지 방식으로 전달 가능:
     * 1. 쿼리 파라미터로 DB의 channel_id (Integer) 직접 전달
     * 2. JSON에 youtube_channel_id 포함 시 자동으로 DB ID로 변환
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveFromAgent(
            @RequestParam(required = false) Integer channelId,
            @RequestBody String jsonPayload
    ) {
        try {
            log.info("Agent 채널 분석 저장 요청: channelId={}", channelId);

            ChannelAgentRequestDto request = ChannelAgentRequestDto.builder()
                    .channelId(channelId)  // null일 수 있음 (JSON에서 찾을 예정)
                    .jsonPayload(jsonPayload)
                    .build();

            Integer savedChannelId = analysisService.saveFromAgent(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "채널 분석 결과 저장 완료",
                    "channel_id", savedChannelId
            ));

        } catch (Exception e) {
            log.error("Agent 채널 분석 저장 실패: channelId={}", channelId, e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "저장 실패: " + e.getMessage(),
                            "channel_id", channelId != null ? channelId : "unknown"
                    ));
        }
    }

    /**
     * 프론트엔드 API 1: 채널 최신 보고서 메타데이터 조회
     */
    @GetMapping("/{channelId}/metadata")
    public ResponseEntity<ChannelAnalysisResponseDto> getMetadata(
            @PathVariable Integer channelId
    ) {
        try {
            log.info("채널 메타데이터 조회: channelId={}", channelId);

            ChannelAnalysisResponseDto response = analysisService.getMetadata(channelId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("채널 메타데이터 조회 실패: channelId={}", channelId, e);
            throw new RuntimeException("채널 메타데이터 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 프론트엔드 API 2: 채널 최신 보고서 위협 인텔리전스 조회 (원본 JSON 그대로)
     */
    @GetMapping("/{channelId}/threat-intelligence")
    public ResponseEntity<Map<String, Object>> getThreatIntelligence(
            @PathVariable Integer channelId
    ) {
        try {
            log.info("채널 위협 인텔리전스 조회: channelId={}", channelId);

            Map<String, Object> response = analysisService.getThreatIntelligence(channelId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("채널 위협 인텔리전스 조회 실패: channelId={}", channelId, e);
            throw new RuntimeException("채널 위협 인텔리전스 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 프론트엔드 API 3: 채널 최신 보고서 방어 전략 조회 (원본 JSON 그대로)
     */
    @GetMapping("/{channelId}/defense-strategy")
    public ResponseEntity<Map<String, Object>> getDefenseStrategy(
            @PathVariable Integer channelId
    ) {
        try {
            log.info("채널 방어 전략 조회: channelId={}", channelId);

            Map<String, Object> response = analysisService.getDefenseStrategy(channelId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("채널 방어 전략 조회 실패: channelId={}", channelId, e);
            throw new RuntimeException("채널 방어 전략 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 프론트엔드 API 4: 채널 분석 히스토리 조회
     */
    @GetMapping("/{channelId}/history")
    public ResponseEntity<List<Map<String, Object>>> getAnalysisHistory(
            @PathVariable Integer channelId,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        try {
            log.info("채널 분석 히스토리 조회: channelId={}, limit={}", channelId, limit);

            List<Map<String, Object>> history = analysisService.getAnalysisHistory(channelId, limit);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("채널 히스토리 조회 실패: channelId={}", channelId, e);
            throw new RuntimeException("채널 히스토리 조회 실패: " + e.getMessage());
        }
    }
}

