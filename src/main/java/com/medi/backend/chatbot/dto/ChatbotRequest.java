package com.medi.backend.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 챗봇 요청 DTO
 * - channelId: YouTube 채널 ID (String, 예: "UCxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
 * - FastAPI와 통신 시 snake_case로 변환됨
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequest {
    
    /**
     * YouTube 채널 ID (String)
     * null이면 사용자의 첫 번째 채널의 YouTube channel_id로 자동 설정
     */
    @JsonProperty("channel_id")
    private String channelId;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("conversation_history")
    private List<Message> conversationHistory;
}

