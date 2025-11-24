package com.medi.backend.chatbot.controller;

import com.medi.backend.chatbot.dto.ChatbotRequest;
import com.medi.backend.chatbot.dto.ChatbotResponse;
import com.medi.backend.chatbot.service.ChatbotService;
import com.medi.backend.global.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챗봇 컨트롤러
 * 프론트엔드와 FastAPI 챗봇 사이의 프록시 역할
 */
@Slf4j
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {
    
    private final ChatbotService chatbotService;
    private final AuthUtil authUtil;
    
    /**
     * 챗봇 메시지 전송 및 응답 받기
     * 
     * @param request 챗봇 요청 (channelId, message, conversationHistory)
     * @return 챗봇 응답
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/chat")
    public ResponseEntity<ChatbotResponse> chat(@RequestBody ChatbotRequest request) {
        // 인증 확인
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            log.warn("⚠️ [챗봇] 인증되지 않은 사용자 요청");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📡 [챗봇] 요청 수신: userId={}, channelId={}, messageLength={}", 
            userId, 
            request.getChannelId(),
            request.getMessage() != null ? request.getMessage().length() : 0);
        
        // Service 호출 (FastAPI로 전달)
        ChatbotResponse response = chatbotService.chat(request);
        
        // 응답 그대로 반환
        return ResponseEntity.ok(response);
    }
}

