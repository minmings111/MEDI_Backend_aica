package com.medi.backend.youtube.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.service.ChannelService;

@RestController
@RequestMapping("/api/youtube/channels")
public class ChannelController {
    private final ChannelService channelService;
    private final AuthUtil authUtil;

    public ChannelController(ChannelService channelService, AuthUtil authUtil) {
        this.channelService = channelService;
        this.authUtil = authUtil;
    }

    // View my channels
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<?> getChannelsByUserId() {
        Integer userId = authUtil.getCurrentUserId();

        List<YoutubeChannelDto> youtubeChannelDtos = channelService.getChannelsByUserId(userId);
        return ResponseEntity.ok(youtubeChannelDtos);
    }

    // Get one channel by ID
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOneChannelByIdAndUserId(@PathVariable("id") Integer id) {
        Integer userId = authUtil.getCurrentUserId();
        
        YoutubeChannelDto youtubeChannelDto = channelService.getOneChannelByIdAndUserId(id, userId);
        
        if (youtubeChannelDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(youtubeChannelDto);
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
        
        // id (which channel) + userId (whose channel)
        Integer deleteCount = channelService.deleteChannelById(id, userId);
        
        if (deleteCount == 1) {
            return ResponseEntity.ok("Delete Success"); 
        }
        return new ResponseEntity<>("Delete Failed: Not found or not your channel", HttpStatus.NOT_FOUND);
    }



}
