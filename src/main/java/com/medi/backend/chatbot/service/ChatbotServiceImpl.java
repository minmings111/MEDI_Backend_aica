package com.medi.backend.chatbot.service;

import com.medi.backend.chatbot.dto.ChatbotRequest;
import com.medi.backend.chatbot.dto.ChatbotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 챗봇 서비스 구현체
 * FastAPI 챗봇 서버와 통신
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {
    
    private final RestClient restClient;
    
    @Value("${chatbot.api-url:http://localhost:8000}")
    private String fastApiBaseUrl;
    
    @Override
    public ChatbotResponse chat(ChatbotRequest request) {
        log.info("📡 [챗봇] FastAPI 호출 시작: channelId={}, messageLength={}", 
            request.getChannelId(), 
            request.getMessage() != null ? request.getMessage().length() : 0);
        
        try {
            // FastAPI 호출
            ChatbotResponse response = restClient.post()
                .uri(fastApiBaseUrl + "/api/chat")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    log.error("❌ [챗봇] FastAPI 클라이언트 에러: status={}, channelId={}", 
                        res.getStatusCode(), request.getChannelId());
                    throw new RuntimeException("챗봇 요청이 올바르지 않습니다.");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("❌ [챗봇] FastAPI 서버 에러: status={}, channelId={}", 
                        res.getStatusCode(), request.getChannelId());
                    throw new RuntimeException("챗봇 서버 오류가 발생했습니다.");
                })
                .body(ChatbotResponse.class);
            
            log.info("✅ [챗봇] FastAPI 응답 수신: success={}, toolsUsed={}, responseLength={}", 
                response.getSuccess(),
                response.getToolsUsed(),
                response.getResponse() != null ? response.getResponse().length() : 0);
            
            return response;
            
        } catch (RuntimeException e) {
            log.error("❌ [챗봇] FastAPI 호출 실패: channelId={}, error={}", 
                request.getChannelId(), e.getMessage(), e);
            return createErrorResponse(request.getChannelId(), e.getMessage());
            
        } catch (Exception e) {
            log.error("❌ [챗봇] 예상치 못한 에러: channelId={}, error={}", 
                request.getChannelId(), e.getMessage(), e);
            return createErrorResponse(request.getChannelId(), "서버 내부 오류가 발생했습니다.");
        }
    }
    
    /**
     * 에러 응답 생성
     */
    private ChatbotResponse createErrorResponse(String channelId, String errorMessage) {
        return new ChatbotResponse(
            "죄송합니다. 현재 챗봇 서비스를 이용할 수 없습니다.\n\n" + errorMessage,
            channelId,
            List.of(),
            false
        );
    }
}

