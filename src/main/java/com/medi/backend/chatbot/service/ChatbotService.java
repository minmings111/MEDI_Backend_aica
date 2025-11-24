package com.medi.backend.chatbot.service;

import com.medi.backend.chatbot.dto.ChatbotRequest;
import com.medi.backend.chatbot.dto.ChatbotResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 챗봇 서비스 인터페이스
 */
public interface ChatbotService {
    
    /**
     * 챗봇에 메시지 전송 및 응답 받기
     * 
     * @param request 챗봇 요청 (channelId, message, conversationHistory)
     * @return 챗봇 응답
     */
    ChatbotResponse chat(ChatbotRequest request);
    
    /**
     * 챗봇에 메시지 전송 및 스트리밍 응답 받기 (SSE)
     * 
     * @param request 챗봇 요청 (channelId, message, conversationHistory)
     * @return SSE Emitter
     */
    SseEmitter streamChat(ChatbotRequest request);
}

