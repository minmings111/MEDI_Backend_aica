package com.medi.backend.youtube.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.service.VideoService;

@RestController
@RequestMapping("/api/youtube/videos")
public class VideoController {
    private final VideoService videoService;
    private final AuthUtil authUtil;

    public VideoController(VideoService videoService, AuthUtil authUtil){
        this.videoService = videoService;
        this.authUtil = authUtil;
    }
    
    // Get videos by channel ID
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<?> getVideosByChannelIdAndUserId(@PathVariable("channelId") Integer channelId) {
        Integer userId = authUtil.getCurrentUserId();
        
        List<YoutubeVideoDto> youtubeVideoDtos = videoService.getVideosByChannelIdAndUserId(channelId, userId);
        return ResponseEntity.ok(youtubeVideoDtos);
    }

    // Get one video by ID
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<?> getVideoByIdAndUserId(@PathVariable("id") Integer id) {
        Integer userId = authUtil.getCurrentUserId();
        
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
        
        List<YoutubeVideoDto> youtubeVideoDtos = videoService.getVideosByUserId(userId);
        return ResponseEntity.ok(youtubeVideoDtos);
    }

    // Get all videos (Admin only)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<YoutubeVideoDto>> getAllVideosForAdmin() {
        List<YoutubeVideoDto> youtubeVideoDtos = videoService.getAllVideosForAdmin();
        return ResponseEntity.ok(youtubeVideoDtos);
    }
}
