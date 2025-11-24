package com.medi.backend.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 챗봇 대화 메시지 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String role;  // "user" or "assistant"
    private String content;
}

