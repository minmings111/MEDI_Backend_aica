package com.medi.backend.youtube.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.redis.dto.RedisSyncResult;
import com.medi.backend.youtube.redis.service.YoutubeRedisSyncService;
import com.medi.backend.youtube.service.ChannelService;
import com.medi.backend.youtube.service.YoutubeService;

@Slf4j
@RestController
@RequestMapping("/api/youtube/channels")
@Tag(name = "YouTube Channels", description = "채널 목록 조회 및 삭제 API")
public class ChannelController {
    private final ChannelService channelService;
    private final YoutubeService youtubeService;
    private final AuthUtil authUtil;
    private final YoutubeRedisSyncService youtubeRedisSyncService;

    public ChannelController(
            ChannelService channelService, 
            YoutubeService youtubeService, 
            AuthUtil authUtil,
            YoutubeRedisSyncService youtubeRedisSyncService) {
        this.channelService = channelService;
        this.youtubeService = youtubeService;
        this.authUtil = authUtil;
        this.youtubeRedisSyncService = youtubeRedisSyncService;
    }

    // View my channels
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<?> getChannelsByUserId() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            log.warn("❌ 채널 목록 조회 요청 - 비로그인 사용자");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("📡 [API 요청] 채널 목록 조회: userId={}", userId);
        List<YoutubeChannelDto> youtubeChannelDtos = channelService.getChannelsByUserId(userId);
        log.info("📡 [API 응답] 채널 목록 조회 완료: userId={}, 채널수={}개", 
            userId, youtubeChannelDtos != null ? youtubeChannelDtos.size() : 0);
        
        return ResponseEntity.ok(youtubeChannelDtos);
    }

    // Get one channel by ID
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOneChannelByIdAndUserId(@PathVariable("id") Integer id) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        YoutubeChannelDto youtubeChannelDto = channelService.getOneChannelByIdAndUserId(id, userId);
        
        if (youtubeChannelDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(youtubeChannelDto);
    }

    /**
     * 채널 새로고침 (수동 동기화)
     * 
     * MySQL에만 저장하고 Redis 동기화는 실행하지 않습니다.
     * Redis 동기화는 첫 등록 시(OAuth 콜백) 또는 별도 엔드포인트(/redis/sync)를 통해 실행됩니다.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/sync")
    public ResponseEntity<?> syncChannels(
            @org.springframework.web.bind.annotation.RequestParam(name = "syncVideos", defaultValue = "true") boolean syncVideos
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 새로고침은 MySQL만 저장 (Redis 동기화 X)
        // syncVideos=false로 하면 Redis 동기화가 실행되지 않음
        List<YoutubeChannelDto> synced = youtubeService.syncChannels(userId, false);
        return ResponseEntity.ok(synced);
    }

    /**
     * Redis 초기 동기화 엔드포인트
     * 
     * 사용자의 모든 채널을 YouTube API로 조회하여:
     * - 각 채널의 상위 20개 영상 (쇼츠 제외, 조회수 순)을 Redis에 저장
     * - 각 영상의 댓글 100개를 Redis에 저장
     * 
     * 주의: 이 엔드포인트는 수동으로 초기 동기화를 트리거할 때 사용합니다.
     * 증분 동기화는 스케줄러에 의해 자동으로 실행됩니다.
     * 
     * @return Redis 동기화 결과 (채널 개수, 비디오 개수, 댓글 개수)
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/redis/sync")
    public ResponseEntity<?> syncChannelsToRedis() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("success", false, "message", "로그인이 필요합니다")
            );
        }

        try {
            log.info("Redis 초기 동기화 시작: userId={}", userId);
            RedisSyncResult result = youtubeRedisSyncService.syncToRedis(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Redis 동기화 완료",
                "result", Map.of(
                    "channelCount", result.getChannelCount(),
                    "videoCount", result.getVideoCount(),
                    "commentCount", result.getCommentCount(),
                    "success", result.isSuccess(),
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : ""
                )
            ));
        } catch (Exception e) {
            log.error("Redis 동기화 실패: userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of("success", false, "message", e.getMessage())
            );
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<YoutubeChannelDto>> getAllChannelsForAdmin() {

        List<YoutubeChannelDto> youtubeChannelDtos = channelService.getAllChannelsForAdmin();
        return ResponseEntity.ok(youtubeChannelDtos);
    }

    // View a specific user's channels
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getChannelsByUserIdForAdmin(@PathVariable("userId") Integer userId) {

        List<YoutubeChannelDto> youtubeChannelDtos = channelService.getChannelsByUserId(userId);
        return ResponseEntity.ok(youtubeChannelDtos);
    }




    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteChannelById(@PathVariable("id") Integer id){
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        
        // id (which channel) + userId (whose channel)
        Integer deleteCount = channelService.deleteChannelById(id, userId);
        
        if (deleteCount == 1) {
            return ResponseEntity.ok("Delete Success"); 
        }
        return new ResponseEntity<>("Delete Failed: Not found or not your channel", HttpStatus.NOT_FOUND);
    }



}
