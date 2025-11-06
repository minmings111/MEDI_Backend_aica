package com.medi.backend.youtube.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class YoutubeChannelDto {
    private Integer id;
    private Integer userId;
    private Integer oauthTokenId;
    private String youtubeChannelId; // (API에서 받은 ID)
    private String channelName;
    private String channelHandle;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
