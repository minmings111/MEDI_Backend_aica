package com.medi.backend.agent.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentChannelResult;
import com.medi.backend.agent.dto.AgentSummaryResult;
import com.medi.backend.agent.dto.AgentCommunicationResult;
import com.medi.backend.agent.dto.AgentProfileResult;
import com.medi.backend.agent.dto.AgentEcosystemResult;
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
     * AI 서버에서 개별 분석 결과를 받는 엔드포인트
     * 
     * 개별 JSON을 받아서 타입을 판단하고 각각의 DTO로 변환한 다음 Service의 개별 메서드를 호출
     */
    @PostMapping("/profile-results")
    public ResponseEntity<?> receiveResult(@RequestBody Map<String, Object> jsonBody) {
        
        try {
            // 1. JSON type check and collect result
            if (jsonBody.containsKey("summary")) {
                // summary 결과
                AgentSummaryResult result = objectMapper.convertValue(jsonBody, AgentSummaryResult.class);
                agentService.collectSummaryResult(result);
                
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Summary result received and processed successfully");
                response.put("video_id", result.getVideoId());
                return ResponseEntity.ok(response);
                
            } else if (jsonBody.containsKey("communication_report")) {
                // communication_report 결과
                AgentCommunicationResult result = objectMapper.convertValue(jsonBody, AgentCommunicationResult.class);
                agentService.collectCommunicationResult(result);
                
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Communication result received and processed successfully");
                response.put("video_id", result.getVideoId());
                return ResponseEntity.ok(response);
                
            } else if (jsonBody.containsKey("profile_report")) {
                // profile_report 결과
                AgentProfileResult result = objectMapper.convertValue(jsonBody, AgentProfileResult.class);
                agentService.collectProfileResult(result);
                
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Profile result received and processed successfully");
                response.put("channel_id", result.getChannelId());
                return ResponseEntity.ok(response);
                
            } else if (jsonBody.containsKey("ecosystem_report")) {
                // ecosystem_report 결과
                AgentEcosystemResult result = objectMapper.convertValue(jsonBody, AgentEcosystemResult.class);
                agentService.collectEcosystemResult(result);
                
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Ecosystem result received and processed successfully");
                response.put("channel_id", result.getChannelId());
                return ResponseEntity.ok(response);
                
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "Unknown result type. Must contain one of: summary, communication_report, profile_report, ecosystem_report");
                errorResponse.put("error", "INVALID_REQUEST");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to process result");
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


