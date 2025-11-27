package com.medi.backend.filter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이메일 알림 설정 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationSettings {
    /**
     * 이메일 알림 활성화 여부
     */
    private Boolean enabled;
    
    /**
     * 시간 단위 (HOURLY: 시간당, DAILY: 일당)
     * null이거나 없으면 기본값 HOURLY 사용
     */
    private String timeUnit; // "HOURLY" or "DAILY"
    
    /**
     * 필터링된 댓글 개수 기준 (이 개수 이상이면 이메일 발송)
     * 예: timeUnit이 HOURLY이고 threshold가 10이면, 시간당 필터링된 댓글이 10개 이상일 때 이메일 발송
     */
    private Integer threshold;
    
    /**
     * 알림을 받을 이메일 주소 (선택적)
     * null이면 users 테이블의 email 사용
     */
    private String email;
}

