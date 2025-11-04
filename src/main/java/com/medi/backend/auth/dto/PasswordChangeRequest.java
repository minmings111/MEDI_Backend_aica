package com.medi.backend.auth.dto;

import lombok.Data;

/**
 * 로그인 상태에서 비밀번호 변경 요청 DTO
 */
@Data
public class PasswordChangeRequest {
    private String currentPassword;     // 현재 비밀번호
    private String newPassword;         // 새 비밀번호
    private String confirmPassword;     // 새 비밀번호 확인
}
