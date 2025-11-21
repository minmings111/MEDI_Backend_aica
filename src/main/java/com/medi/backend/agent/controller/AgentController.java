package com.medi.backend.agent.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;

import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.service.AgentService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }
    
    /**
     * AI 서버에서 필터링된 댓글 결과를 받는 엔드포인트
     * 
     * @param request AI 분석 결과 (video_id, comments 배열 포함)
     * @return 저장 성공 응답
     */
    @PostMapping("filtered-results")
    public ResponseEntity<Map<String, Object>> receiveFilteredComments(
        @RequestBody AgentFilteredCommentsRequest request
    ) {
        Integer savedCount = agentService.insertFilteredComment(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Filtered comments saved successfully");
        response.put("savedCount", savedCount);
        response.put("totalReceived", request.getComments() != null ? request.getComments().size() : 0);
        response.put("video_id", request.getVideoId());
        
        return ResponseEntity.ok(response);
    }
}

