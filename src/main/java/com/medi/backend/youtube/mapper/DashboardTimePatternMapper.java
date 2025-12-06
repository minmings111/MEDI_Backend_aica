package com.medi.backend.youtube.mapper;

import com.medi.backend.youtube.dto.DashboardTimePatternDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 대시보드 시간대별 악플 통계 Mapper
 * 
 * 테이블: dashboard_time_pattern_stats
 * 용도: 대시보드 OverviewTab의 "악플 집중 시간대" 그래프 데이터 조회
 */
@Mapper
public interface DashboardTimePatternMapper {

    /**
     * 채널별 대시보드 시간대별 악플 통계 조회
     * 
     * @param channelId 채널 ID
     * @return 시간대별 악플 통계 데이터 (채널당 1개만 존재)
     */
    Optional<DashboardTimePatternDto> findByChannelId(@Param("channelId") Integer channelId);
}

