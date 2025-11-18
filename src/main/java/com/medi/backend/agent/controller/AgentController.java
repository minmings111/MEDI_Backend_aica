package com.medi.backend.agent.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medi.backend.agent.dto.AgentFilteredComment;
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

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }
    
    @PostMapping("/filtered")
    public ResponseEntity<?> receiveFilteredComments(@RequestBody List<AgentFilteredComment> filteredComments) { // JSON -> DTO
        
        Integer savedCount = agentService.insertFilteredComment(filteredComments);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Filtered comments saved successfully");
        response.put("savedCount", savedCount);
        response.put("totalReceived", filteredComments.size());
        
        return ResponseEntity.ok(response);
        
    }

    // TODO: AI 서버에서 분석 결과를 받는 엔드포인트 (구현 예정)
    @PostMapping("/results")
    public ResponseEntity<?> receiveResult() {

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Result endpoint - not implemented yet");
        return ResponseEntity.ok(response);

    }

    // TODO: AI 서버에서 상태 정보를 받는 엔드포인트 (구현 예정)
    @PostMapping("/status")
    public ResponseEntity<?> receiveStatus() {

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Status endpoint - not implemented yet");
        return ResponseEntity.ok(response);

    }

}


