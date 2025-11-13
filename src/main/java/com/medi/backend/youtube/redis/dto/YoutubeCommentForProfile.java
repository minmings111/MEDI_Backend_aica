package com.medi.backend.youtube.redis.dto;

import java.util.List;
import lombok.Getter;
import lombok.Builder;

@Getter
@Builder
public class YoutubeCommentForProfile {
    private final String channelId;        // 채널 ID
    private final List<String> comments;
}