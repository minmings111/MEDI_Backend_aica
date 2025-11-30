package com.medi.backend.youtube.mapper;

import com.medi.backend.youtube.dto.ChannelThreatAnalysisDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ChannelThreatAnalysisMapper {

    /**
     * 채널 분석 결과 저장
     */
    void insertAnalysis(ChannelThreatAnalysisDto dto);

    /**
     * 채널 분석 결과 업데이트
     */
    void updateAnalysis(ChannelThreatAnalysisDto dto);

    /**
     * 채널 최신 분석 결과 조회 (generated_at DESC LIMIT 1)
     */
    Optional<ChannelThreatAnalysisDto> findLatestByChannelId(@Param("channelId") Integer channelId);

    /**
     * 채널 특정 시간 분석 결과 조회
     */
    Optional<ChannelThreatAnalysisDto> findByChannelIdAndGeneratedAt(
        @Param("channelId") Integer channelId,
        @Param("generatedAt") LocalDateTime generatedAt
    );

    /**
     * 채널 분석 기록 목록 (generated_at DESC 정렬)
     */
    List<ChannelThreatAnalysisDto> findByChannelIdOrderByGeneratedAt(
        @Param("channelId") Integer channelId,
        @Param("limit") Integer limit
    );

    /**
     * 기존 데이터 존재 여부 확인 (UPSERT용)
     */
    boolean existsByChannelIdAndGeneratedAt(
        @Param("channelId") Integer channelId,
        @Param("generatedAt") LocalDateTime generatedAt
    );
}

