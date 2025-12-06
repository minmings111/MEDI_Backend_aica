package com.medi.backend.youtube.service;

import com.medi.backend.youtube.dto.DashboardTimePatternDto;
import com.medi.backend.youtube.mapper.DashboardTimePatternMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 대시보드 시간대별 악플 통계 Service
 * 
 * 용도: 대시보드 OverviewTab의 "악플 집중 시간대" 그래프 데이터 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardTimePatternService {

    private final DashboardTimePatternMapper mapper;

    /**
     * 채널별 대시보드 시간대별 악플 통계 조회
     * 
     * @param channelId 채널 ID
     * @return 시간대별 악플 통계 데이터 (프론트엔드 형식에 맞게 변환)
     */
    public Map<String, Object> getTimePatterns(Integer channelId) {
        log.info("대시보드 시간대별 악플 통계 조회: channelId={}", channelId);

        DashboardTimePatternDto dto = mapper.findByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("대시보드 시간대별 악플 통계를 찾을 수 없습니다. channelId=" + channelId));

        // 프론트엔드 형식에 맞게 변환
        Map<String, Object> response = new HashMap<>();
        
        // distribution: Map<String, Integer> 그대로 사용
        response.put("distribution", dto.getTimeDistribution());
        
        // red_zone: 객체로 변환
        Map<String, Object> redZone = new HashMap<>();
        redZone.put("time_slot", dto.getRedZoneTimeSlot());
        redZone.put("count", dto.getRedZoneCount());
        redZone.put("percentage", dto.getRedZonePercentage());
        response.put("red_zone", redZone);

        log.info("대시보드 시간대별 악플 통계 조회 성공: channelId={}, redZone={}", 
                channelId, dto.getRedZoneTimeSlot());

        return response;
    }
}

