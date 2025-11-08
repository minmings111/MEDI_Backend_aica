package com.medi.backend.youtube.dto;

import lombok.Data;

@Data
public class VideoSyncRequest {

    private Integer channelId;
    private Integer maxResults;
}

