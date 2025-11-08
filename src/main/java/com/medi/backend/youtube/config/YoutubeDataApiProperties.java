package com.medi.backend.youtube.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "youtube.data-api")
public class YoutubeDataApiProperties {

    /**
     * 조회 전용 API 키 목록. 빈 값이면 OAuth 토큰으로 조회한다.
     */
    private List<String> apiKeys = new ArrayList<>();

    /**
     * API 키가 없거나 모두 소진되었을 때 OAuth 토큰으로 폴백할지 여부.
     */
    private boolean enableFallback = true;

}

