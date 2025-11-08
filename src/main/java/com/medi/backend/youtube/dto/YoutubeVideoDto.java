package com.medi.backend.youtube.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class YoutubeVideoDto {

    private Integer id;
    private Integer channelId;
    private String youtubeVideoId;
    private String title;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private LocalDateTime publishedAt;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public void setCommentCount(Long commentCount) {
        this.commentCount = commentCount;
    }

}