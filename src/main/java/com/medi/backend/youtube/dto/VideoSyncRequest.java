package com.medi.backend.youtube.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medi.backend.youtube.model.VideoSyncMode;
import lombok.Data;

@Data
public class VideoSyncRequest {

    private Integer channelId;
    private Integer maxResults;
    @JsonProperty("syncMode")
    private VideoSyncMode syncMode;
}

