package com.medi.backend.youtube.dto;

import lombok.*;

/**
 * FastAPI Agent 요청 DTO (채널 기준)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAgentRequestDto {

    private Integer channelId;
    private String jsonPayload; // FastAPI Agent 응답 JSON (String)
}

