package com.medi.backend.auth.dto;

import lombok.Data;

/**
 * 비밀번호 재설정 요청 DTO
 */
@Data
public class PasswordResetRequest {
    private String email;           // 이메일
    private String code;            // 인증 코드
    private String newPassword;     // 새 비밀번호
}
