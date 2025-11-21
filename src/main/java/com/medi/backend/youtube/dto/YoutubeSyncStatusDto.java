package com.medi.backend.youtube.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * YouTube Redis 동기화 상태 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeSyncStatusDto {
    
    private Long id;
    private Integer userId;
    private SyncStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum SyncStatus {
        PENDING,      // 대기 중
        IN_PROGRESS,  // 진행 중
        SUCCESS,      // 성공
        FAILED        // 실패
    }
}

