package com.medi.backend.youtube.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeChannelDto {
    private Integer id;
    private Integer userId;
    private Integer oauthTokenId;
    private String youtubeChannelId; // (API?먯꽌 諛쏆? ID)
    private String channelName;
    private String channelHandle;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastVideoPublishedAt;
    private String uploadsPlaylistId;
    private LocalDateTime deletedAt; // 소프트 삭제용
}
