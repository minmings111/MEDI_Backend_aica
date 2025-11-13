package com.medi.backend.youtube.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.medi.backend.youtube.redis.dto.YoutubeCommentForProfile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeCommentForProfileServiceImpl implements YoutubeCommentForProfileService {
    
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    
    // Redis Key 형식: 채널 ID 그대로 사용
    // 예: "UC123456789"
    // Value: List<String> (댓글 내용만)
    
    @Override
    public long saveChannelComments(String channelId, List<String> videoIds) {
        if (channelId == null || channelId.isBlank()) {
            log.warn("채널 ID가 없습니다.");
            return 0;
        }
        
        if (videoIds == null || videoIds.isEmpty()) {
            log.warn("비디오 ID 리스트가 비어있습니다: channelId={}", channelId);
            return 0;
        }
        
        try {
            // 기존 데이터 삭제 (덮어쓰기)
            stringRedisTemplate.delete(channelId);
            
            List<String> allCommentTexts = new ArrayList<>();
            
            // 각 비디오의 댓글을 읽어서 내용만 추출
            for (String videoId : videoIds) {
                if (videoId == null || videoId.isBlank()) {
                    continue;
                }
                
                String videoRedisKey = "video:" + videoId + ":comments";
                
                // 해당 비디오의 댓글 리스트 조회
                List<String> commentJsonList = stringRedisTemplate.opsForList().range(videoRedisKey, 0, -1);
                
                if (commentJsonList != null && !commentJsonList.isEmpty()) {
                    // 각 댓글 JSON을 파싱해서 text 필드만 추출
                    for (String commentJson : commentJsonList) {
                        try {
                            JsonNode jsonNode = objectMapper.readTree(commentJson);
                            String text = jsonNode.get("text").asText();
                            
                            if (text != null && !text.isBlank()) {
                                allCommentTexts.add(text);
                            }
                        } catch (JsonProcessingException e) {
                            log.warn("댓글 JSON 파싱 실패: {}", commentJson, e);
                        }
                    }
                }
            }
            
            // 댓글 내용들을 채널 키에 저장
            if (!allCommentTexts.isEmpty()) {
                for (String commentText : allCommentTexts) {
                    stringRedisTemplate.opsForList().rightPush(channelId, commentText);
                }
                
                // TTL 설정 (예: 7일)
                stringRedisTemplate.expire(channelId, Duration.ofDays(7));
                
                log.info("채널 {}의 댓글 내용 저장 완료: {}개 ({}개 비디오)", 
                    channelId, allCommentTexts.size(), videoIds.size());
            } else {
                log.warn("채널 {}의 댓글이 없습니다.", channelId);
            }
            
            return allCommentTexts.size();
            
        } catch (Exception e) {
            log.error("채널 {}의 댓글 내용 저장 실패", channelId, e);
            throw new RuntimeException("saveChannelComments failed", e);
        }
    }
    
    @Override
    public YoutubeCommentForProfile getChannelComments(String channelId) {
        List<String> commentTexts = getChannelCommentTexts(channelId);
        
        return YoutubeCommentForProfile.builder()
            .channelId(channelId)
            .comments(commentTexts)
            .build();
    }
    
    @Override
    public List<String> getChannelCommentTexts(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return new ArrayList<>();
        }
        
        try {
            // Redis List에서 모든 댓글 내용 조회
            List<String> commentTexts = stringRedisTemplate.opsForList().range(channelId, 0, -1);
            
            return commentTexts != null ? commentTexts : new ArrayList<>();
            
        } catch (Exception e) {
            log.error("채널 {}의 댓글 내용 조회 실패", channelId, e);
            return new ArrayList<>();
        }
    }
}
