package com.medi.backend.youtube.dto;

import java.time.LocalDate;
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
    private Long subscriberCount;
    private Long totalViewCount; // 시연용: 총 조회수
    private Long totalCommentCount; // 시연용: 총 댓글 수 (필터링 비율 계산용)
    private Integer totalVideoCount; // 시연용: 총 동영상 수
    private LocalDate channelCreatedAt; // 시연용: 채널 생성일
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastVideoPublishedAt;
    private String uploadsPlaylistId;
    private LocalDateTime deletedAt; // 소프트 삭제용
}
