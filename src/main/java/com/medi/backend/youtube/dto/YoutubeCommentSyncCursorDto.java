package com.medi.backend.youtube.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeCommentSyncCursorDto {
    private String videoId;
    private LocalDateTime lastSyncTime;
    private LocalDateTime updatedAt;
}


