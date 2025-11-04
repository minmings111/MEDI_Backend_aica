package com.medi.backend.auth.dto;

import lombok.Data;

/**
 * 로그인 요청 DTO
 */
@Data
public class LoginRequest {
    private String email;       // 이메일
    private String password;    // 비밀번호 (평문)
    private Boolean rememberMe; // 로그인 유지 여부 (선택사항)
}
