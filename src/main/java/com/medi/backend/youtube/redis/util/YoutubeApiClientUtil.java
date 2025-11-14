package com.medi.backend.youtube.redis.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;

import com.medi.backend.youtube.service.YoutubeOAuthService;

/**
 * YouTube API 클라이언트 생성 유틸리티
 * 
 * 중복 코드 제거:
 * - 여러 서비스에서 OAuth 토큰 가져오기 + 클라이언트 생성 패턴이 반복됨
 * - 공통 유틸리티로 분리하여 코드 중복 제거
 */
public class YoutubeApiClientUtil {
    
    /**
     * YouTube API 클라이언트 생성
     * 
     * @param accessToken OAuth 액세스 토큰
     * @return YouTube API 클라이언트 객체
     * @throws Exception HTTP 전송 또는 JSON 팩토리 생성 실패 시
     */
    public static YouTube buildClient(String accessToken) throws Exception {
        return new YouTube.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("medi").build();
    }
    
    /**
     * 사용자 ID로부터 YouTube API 클라이언트 생성 (재사용성 향상)
     * 
     * OAuth 토큰 가져오기 + 클라이언트 생성을 한 번에 처리
     * 
     * @param youtubeOAuthService OAuth 서비스
     * @param userId 사용자 ID
     * @return YouTube API 클라이언트 객체
     * @throws Exception OAuth 토큰 조회 실패 또는 클라이언트 생성 실패 시
     */
    public static YouTube buildClientForUser(YoutubeOAuthService youtubeOAuthService, Integer userId) throws Exception {
        String token = youtubeOAuthService.getValidAccessToken(userId);
        return buildClient(token);
    }
    
    /**
     * YouTube API를 통해 사용자의 채널 목록 조회 (재사용성 향상)
     * 
     * 여러 서비스에서 동일한 채널 목록 조회 로직이 중복됨
     * 공통 유틸리티로 분리하여 코드 중복 제거
     * 
     * @param yt YouTube API 클라이언트
     * @return 채널 ID 리스트
     * @throws Exception YouTube API 호출 실패 시
     */
    public static List<String> fetchUserChannelIds(YouTube yt) throws Exception {
        YouTube.Channels.List req = yt.channels().list(Arrays.asList("snippet"));
        req.setMine(true);  // 사용자 본인의 채널만 조회
        ChannelListResponse resp = req.execute();
        
        if (resp.getItems() == null || resp.getItems().isEmpty()) {
            return List.of();
        }
        
        return resp.getItems().stream()
            .map(Channel::getId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toList());
    }
}

