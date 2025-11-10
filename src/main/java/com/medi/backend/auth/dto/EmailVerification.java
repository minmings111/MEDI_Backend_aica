package com.medi.backend.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 이메일 인증 정보 (DB 엔티티)
 * - INSERT 시: id, createdAt은 null (DB 자동 생성)
 * - SELECT 시: 모든 필드 값 존재
 */
@Data
public class EmailVerification {
    private Integer id;                  // PK (DB 자동 생성) //여
    private String email;                // 이메일
    private String code;                 // 6자리 인증 코드
    private LocalDateTime expiresAt;     // 만료 시간 (5분 후)
    private LocalDateTime createdAt;     // 생성 시간 (DB 자동 생성)
}
