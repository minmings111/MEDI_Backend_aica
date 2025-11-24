package com.medi.backend.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 챗봇 응답 DTO
 * FastAPI에서 받은 응답을 그대로 프론트엔드로 전달
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotResponse {
    
    @JsonProperty("response")
    private String response;
    
    @JsonProperty("channel_id")
    private String channelId;
    
    @JsonProperty("tools_used")
    private List<String> toolsUsed;
    
    @JsonProperty("success")
    private Boolean success;
}

