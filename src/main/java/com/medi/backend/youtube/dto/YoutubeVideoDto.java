package com.medi.backend.youtube.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class YoutubeVideoDto {

    private Integer id;
    private Integer channelId;
    private String youtubeVideoId;
    private String title;
    private Integer viewCount;
    private LocalDateTime publishedAt;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}