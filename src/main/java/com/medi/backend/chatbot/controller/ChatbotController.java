package com.medi.backend.chatbot.controller;

import com.medi.backend.chatbot.dto.ChatbotRequest;
import com.medi.backend.chatbot.dto.ChatbotResponse;
import com.medi.backend.chatbot.service.ChatbotService;
import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

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
    private final ChannelService channelService;
    
    /**
     * channelId(YouTube channel_id) 검증 및 자동 채우기
     * 
     * 처리 로직:
     * 1. channelId가 명시적으로 제공되면 그대로 사용 (사용자가 선택한 채널)
     * 2. channelId가 null이면 사용자의 첫 번째 채널의 YouTube channel_id로 자동 설정 (편의 기능)
     * 3. conversationHistory가 null이면 빈 리스트로 초기화
     * 
     * ⚠️ 주의: 여러 채널이 있는 경우, 프론트엔드에서 명시적으로 channel_id를 보내는 것을 권장합니다.
     * 프론트엔드에서 /api/youtube/channels/my로 채널 목록을 조회하고 사용자가 선택할 수 있습니다.
     */
    private void validateAndFillChannelId(ChatbotRequest request, Integer userId) {
        // 1. channelId가 명시적으로 제공된 경우 검증
        if (request.getChannelId() != null && !request.getChannelId().isBlank()) {
            // 사용자가 선택한 채널인지 검증 (보안: 다른 사용자의 채널 접근 방지)
            try {
                List<YoutubeChannelDto> userChannels = channelService.getChannelsByUserId(userId);
                boolean isValidChannel = userChannels != null && userChannels.stream()
                    .anyMatch(ch -> request.getChannelId().equals(ch.getYoutubeChannelId()));
                
                if (!isValidChannel) {
                    log.warn("⚠️ [챗봇] 사용자가 소유하지 않은 채널 ID: userId={}, channelId={}", 
                        userId, request.getChannelId());
                    // 잘못된 채널 ID는 빈 문자열로 설정 (FastAPI에서 에러 처리)
                    request.setChannelId("");
                } else {
                    log.info("✅ [챗봇] 사용자가 선택한 채널 사용: userId={}, YouTubeChannelId={}", 
                        userId, request.getChannelId());
                }
            } catch (Exception e) {
                log.error("❌ [챗봇] 채널 검증 실패: userId={}, channelId={}, error={}", 
                    userId, request.getChannelId(), e.getMessage(), e);
                request.setChannelId("");
            }
        }
        
        // 2. channelId가 null이거나 빈 문자열이면 첫 번째 채널로 자동 설정
        if (request.getChannelId() == null || request.getChannelId().isBlank()) {
            log.info("🔍 [챗봇] channelId(YouTube channel_id)가 null입니다. 사용자의 채널 목록 조회 중: userId={}", userId);
            
            try {
                List<YoutubeChannelDto> channels = channelService.getChannelsByUserId(userId);
                
                if (channels != null && !channels.isEmpty()) {
                    // 첫 번째 채널의 YouTube channel_id 사용
                    String firstYoutubeChannelId = channels.get(0).getYoutubeChannelId();
                    request.setChannelId(firstYoutubeChannelId);
                    
                    if (channels.size() > 1) {
                        log.warn("⚠️ [챗봇] 사용자가 여러 채널을 가지고 있습니다. 첫 번째 채널 자동 사용: userId={}, YouTubeChannelId={}, 채널수={}개", 
                            userId, firstYoutubeChannelId, channels.size());
                        log.info("💡 [챗봇] 권장: 프론트엔드에서 /api/youtube/channels/my로 채널 목록을 조회하고 사용자가 선택하도록 하세요.");
                    } else {
                        log.info("✅ [챗봇] channelId(YouTube channel_id) 자동 설정: userId={}, YouTubeChannelId={}", 
                            userId, firstYoutubeChannelId);
                    }
                } else {
                    log.warn("⚠️ [챗봇] 사용자의 채널이 없습니다: userId={}", userId);
                    // 채널이 없으면 null로 유지 (나중에 에러 처리)
                    // request.setChannelId("") 제거 - null로 유지하여 명확한 에러 처리
                }
            } catch (Exception e) {
                log.error("❌ [챗봇] 채널 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
                // 예외 발생 시 null로 유지 (나중에 명확한 에러 메시지 반환)
                // request.setChannelId("") 제거
            }
        }
        
        // 3. conversationHistory가 null이면 빈 리스트로 초기화
        if (request.getConversationHistory() == null) {
            request.setConversationHistory(List.of());
            log.debug("✅ [챗봇] conversationHistory를 빈 리스트로 초기화");
        }
    }
    
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
        
        // ✅ channelId(YouTube channel_id) 검증 및 자동 채우기
        validateAndFillChannelId(request, userId);
        
        // ✅ 채널이 없는 경우 에러 반환 (방어적 프로그래밍)
        // ⚠️ 참고: 프론트엔드에서 채널이 없으면 챗봇 UI를 비활성화하는 것을 권장합니다.
        if (request.getChannelId() == null || request.getChannelId().isBlank()) {
            log.warn("⚠️ [챗봇] 채널이 등록되지 않은 사용자: userId={}", userId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatbotResponse(
                    "챗봇을 사용하려면 YouTube 채널을 먼저 등록해주세요.",
                    null,
                    List.of(),
                    false
                ));
        }
        
        log.info("📡 [챗봇] 요청 수신: userId={}, YouTubeChannelId={}, messageLength={}", 
            userId, 
            request.getChannelId(),
            request.getMessage() != null ? request.getMessage().length() : 0);
        
        // Service 호출 (FastAPI로 전달)
        ChatbotResponse response = chatbotService.chat(request);
        
        // 응답 그대로 반환
        return ResponseEntity.ok(response);
    }
    
    /**
     * 챗봇 메시지 전송 및 스트리밍 응답 받기 (SSE)
     * 
     * @param request 챗봇 요청 (channelId, message, conversationHistory)
     * @return SSE Emitter
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatbotRequest request) {
        // 인증 확인
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            log.warn("⚠️ [챗봇 스트리밍] 인증되지 않은 사용자 요청");
            SseEmitter emitter = new SseEmitter(1000L);
            emitter.completeWithError(new RuntimeException("인증되지 않은 사용자입니다."));
            return emitter;
        }
        
        // ✅ channelId(YouTube channel_id) 검증 및 자동 채우기
        validateAndFillChannelId(request, userId);
        
        // ✅ 채널이 없는 경우 에러 반환 (방어적 프로그래밍)
        // ⚠️ 참고: 프론트엔드에서 채널이 없으면 챗봇 UI를 비활성화하는 것을 권장합니다.
        if (request.getChannelId() == null || request.getChannelId().isBlank()) {
            log.warn("⚠️ [챗봇 스트리밍] 채널이 등록되지 않은 사용자: userId={}", userId);
            SseEmitter emitter = new SseEmitter(1000L);
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"type\":\"error\",\"content\":\"챗봇을 사용하려면 YouTube 채널을 먼저 등록해주세요.\"}"));
            } catch (Exception e) {
                log.error("❌ [챗봇 스트리밍] 에러 전송 실패", e);
            }
            emitter.completeWithError(new RuntimeException("채널이 등록되지 않았습니다."));
            return emitter;
        }
        
        log.info("📡 [챗봇 스트리밍] 요청 수신: userId={}, YouTubeChannelId={}, messageLength={}", 
            userId, 
            request.getChannelId(),
            request.getMessage() != null ? request.getMessage().length() : 0);
        
        // Service 호출 (FastAPI 스트리밍으로 전달)
        return chatbotService.streamChat(request);
    }
}

