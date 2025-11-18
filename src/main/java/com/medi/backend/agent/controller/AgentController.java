package com.medi.backend.agent.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentChannelResult;
import com.medi.backend.agent.service.AgentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/v1/analysis")

public class AgentController {

    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public AgentController(AgentService agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }
    
    // @PostMapping("/filtered")
    // public ResponseEntity<?> receiveFilteredComments(@RequestBody List<AgentFilteredComment> filteredComments) { // JSON -> DTO
        
    //     Integer savedCount = agentService.insertFilteredComment(filteredComments);

    //     Map<String, Object> response = new HashMap<>();
    //     response.put("message", "Filtered comments saved successfully");
    //     response.put("savedCount", savedCount);
    //     response.put("totalReceived", filteredComments.size());
        
    //     return ResponseEntity.ok(response);
        
    // }


    /**
     * AI 서버에서 채널별 통합 분석 결과를 받는 엔드포인트
     * 
     * 채널별로 모든 비디오 결과와 채널 결과가 하나의 JSON으로 묶여서 전달됨
     * 
     * @param result 채널별 통합 분석 결과
     * @return 저장 성공 응답
     */
    @PostMapping("/profile-results")
    public ResponseEntity<?> receiveChannelResult(@RequestBody AgentChannelResult result) {
        
        try {
            agentService.saveChannelResult(result);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Channel analysis results saved successfully");
            response.put("channel_id", result.getChannelId());
            response.put("video_count", result.getVideoResults() != null ? result.getVideoResults().size() : 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to process channel result");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // TODO: AI 서버에서 필터링된 댓글 결과를 받는 엔드포인트 (구현 예정)
    @PostMapping("/filtered-results")
    public ResponseEntity<?> receiveFilteredResults() {

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Filtered results endpoint - not implemented yet");
        return ResponseEntity.ok(response);

    }

}


