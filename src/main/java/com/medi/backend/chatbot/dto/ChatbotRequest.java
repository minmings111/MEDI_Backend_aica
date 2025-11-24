package com.medi.backend.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 챗봇 요청 DTO
 * FastAPI와 통신 시 snake_case로 변환됨
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequest {
    
    @JsonProperty("channel_id")
    private String channelId;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("conversation_history")
    private List<Message> conversationHistory;
}

