package com.medi.backend.auth.dto;

import lombok.Data;

/**
 * 로그인 응답 DTO
 */
@Data
public class LoginResponse {
    private boolean success;
    private String message;
    private UserInfo user;      // 사용자 정보 (비밀번호 제외)
    
    /**
     * 사용자 정보 (민감 정보 제외)
     */
    @Data
    public static class UserInfo {
        private Integer id;
        private String email;
        private String name;
        private String role;
        private String createdAt;
    }
}
