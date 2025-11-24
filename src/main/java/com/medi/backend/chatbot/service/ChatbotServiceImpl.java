package com.medi.backend.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.chatbot.dto.ChatbotRequest;
import com.medi.backend.chatbot.dto.ChatbotResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 챗봇 서비스 구현체
 * FastAPI 챗봇 서버와 통신
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {
    
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    
    // ✅ FixedThreadPool로 변경해 무제한 스레드 생성 방지
    private final ExecutorService executorService = Executors.newFixedThreadPool(100);
    /**
     * 애플리케이션 종료 시 ExecutorService 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("🔄 [챗봇] ExecutorService 종료 중...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("❌ [챗봇] ExecutorService가 정상적으로 종료되지 않았습니다.");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("✅ [챗봇] ExecutorService 종료 완료");
    }
    
    
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
    
    @Override
    public SseEmitter streamChat(ChatbotRequest request) {
        SseEmitter emitter = new SseEmitter(600000L); // 10분 타임아웃
        
        log.info("📡 [챗봇 스트리밍] FastAPI 호출 시작: channelId={}, messageLength={}", 
            request.getChannelId(), 
            request.getMessage() != null ? request.getMessage().length() : 0);
        
        CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            try {
                // FastAPI 스트리밍 엔드포인트 호출
                URL url = new URL(fastApiBaseUrl + "/api/chat/stream");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(600000); // 10분
                
                // 요청 본문 전송
                String requestBody = objectMapper.writeValueAsString(request);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
                
                // 응답 코드 확인
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    log.error("❌ [챗봇 스트리밍] FastAPI 에러: status={}, channelId={}", 
                        responseCode, request.getChannelId());
                    try {
                        emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"type\":\"error\",\"content\":\"챗봇 서버 오류가 발생했습니다.\"}"));
                    } catch (Exception sendError) {
                        log.error("❌ [챗봇 스트리밍] 에러 전송 실패", sendError);
                    }
                    emitter.completeWithError(new RuntimeException("FastAPI 응답 오류: " + responseCode));
                    return;
                }
                
                // 스트리밍 응답 읽기
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 빈 줄 건너뛰기
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        
                        // SSE 형식: "data: {json}"
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6); // "data: " 제거
                            
                            // [DONE] 신호 처리
                            if ("[DONE]".equals(data.trim())) {
                                emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("{\"type\":\"done\"}"));
                                break;
                            }
                            
                            // JSON 데이터를 그대로 전달
                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(data));
                        }
                    }
                }
                
                emitter.complete();
                log.info("✅ [챗봇 스트리밍] 완료: channelId={}", request.getChannelId());
                
            } catch (Exception e) {
                log.error("❌ [챗봇 스트리밍] 오류: channelId={}, error={}", 
                    request.getChannelId(), e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"type\":\"error\",\"content\":\"서버 내부 오류가 발생했습니다.\"}"));
                } catch (Exception sendError) {
                    log.error("❌ [챗봇 스트리밍] 에러 전송 실패", sendError);
                }
                emitter.completeWithError(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, executorService);
        
        // 타임아웃 및 에러 처리
        emitter.onTimeout(() -> {
            log.warn("⏱️ [챗봇 스트리밍] 타임아웃: channelId={}", request.getChannelId());
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"type\":\"error\",\"content\":\"응답 시간이 초과되었습니다. 다시 시도해주세요.\"}"));
            } catch (Exception e) {
                log.error("타임아웃 에러 전송 실패", e);
            }
            emitter.complete();
        });
        
        emitter.onError((ex) -> {
            log.error("❌ [챗봇 스트리밍] 에러: channelId={}, error={}", 
                request.getChannelId(), ex.getMessage(), ex);
            emitter.completeWithError(ex);
        });
        
        return emitter;
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

