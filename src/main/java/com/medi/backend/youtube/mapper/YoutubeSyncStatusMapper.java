package com.medi.backend.youtube.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.youtube.dto.YoutubeSyncStatusDto;

@Mapper
public interface YoutubeSyncStatusMapper {
    
    /**
     * 사용자의 최신 동기화 상태 조회
     */
    YoutubeSyncStatusDto findLatestByUserId(@Param("userId") Integer userId);
    
    /**
     * 동기화 상태 생성
     */
    void insert(YoutubeSyncStatusDto status);
    
    /**
     * 동기화 상태 업데이트
     */
    void updateStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("errorMessage") String errorMessage
    );
    
    /**
     * 동기화 완료 시간 업데이트
     */
    void updateCompletedAt(@Param("id") Long id);
}

