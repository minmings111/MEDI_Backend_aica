package com.medi.backend.youtube.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.VideoSyncRequest;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.service.ChannelService;
import com.medi.backend.youtube.service.YoutubeService;
import com.medi.backend.youtube.service.VideoService;

@RestController
@RequestMapping("/api/youtube/videos")
@Tag(name = "YouTube Videos", description = "채널 영상 조회 API")
public class VideoController {
    private final VideoService videoService;
    private final ChannelService channelService;
    private final YoutubeService youtubeService;
    private final AuthUtil authUtil;

    public VideoController(VideoService videoService, ChannelService channelService, YoutubeService youtubeService, AuthUtil authUtil){
        this.videoService = videoService;
        this.channelService = channelService;
        this.youtubeService = youtubeService;
        this.authUtil = authUtil;
    }
    
    // Get videos by channel ID
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<?> getVideosByChannelIdAndUserId(@PathVariable("channelId") Integer channelId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<YoutubeVideoDto> youtubeVideoDtos = videoService.getVideosByChannelIdAndUserId(channelId, userId);
        return ResponseEntity.ok(youtubeVideoDtos);
    }

    // Get one video by ID
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<?> getVideoByIdAndUserId(@PathVariable("id") Integer id) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        YoutubeVideoDto youtubeVideoDto = videoService.getVideoByIdAndUserId(id, userId);
        
        if (youtubeVideoDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(youtubeVideoDto);
    }

    // Get my videos (all videos from my channels)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<?> getVideosByUserId() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<YoutubeVideoDto> youtubeVideoDtos = videoService.getVideosByUserId(userId);
        return ResponseEntity.ok(youtubeVideoDtos);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/sync")
    public ResponseEntity<?> syncChannelVideos(@RequestBody VideoSyncRequest request) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getChannelId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("channelId is required");
        }

        YoutubeChannelDto channel = channelService.getOneChannelByIdAndUserId(request.getChannelId(), userId);
        if (channel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Channel not found");
        }

        List<YoutubeVideoDto> synced = youtubeService.syncVideos(userId, channel.getYoutubeChannelId(), request.getMaxResults());
        return ResponseEntity.ok(synced);
    }

    // Get all videos (Admin only)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<YoutubeVideoDto>> getAllVideosForAdmin() {
        List<YoutubeVideoDto> youtubeVideoDtos = videoService.getAllVideosForAdmin();
        return ResponseEntity.ok(youtubeVideoDtos);
    }
}
