package com.medi.backend.report.dto;

import lombok.Data;
import java.util.Map;

/**
 * 보고서 생성 요청 DTO
 * 프론트에서 보고서 생성 요청 시 사용
 */
@Data
public class ReportRequest {
    /**
     * YouTube 채널 ID (필수)
     */
    private String channelId;
    
    /**
     * 추가 요청 데이터 (선택사항)
     * 프론트에서 전달하는 추가 정보를 담을 수 있음
     */
    private Map<String, Object> data;
}

