package com.medi.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medi.backend.user.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 로그인 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // null 필드는 응답에서 제외
public class LoginResponse {
    
    private Boolean success;
    private String message;
    private UserInfo user;
    private String sessionId;  // 보안상 제거 권장 (쿠키로 자동 전달)
    private String error;      // 에러 코드 (실패 시에만)
    
    /**
     * 중첩된 사용자 정보 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Integer id;
        private String email;
        private String name;
        private String role;
        private String profileImage;  // OAuth 프로필 이미지
    }
    
    /**
     * 성공 응답 생성 메서드
     */
    public static LoginResponse success(UserDTO user, String sessionId) {
        return LoginResponse.builder()
            .success(true)
            .message("로그인 성공")
            .user(UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .profileImage(user.getProfileImage())
                .build())
            .sessionId(sessionId)
            .build();
    }
    
    /**
     * 실패 응답 생성 메서드
     */
    public static LoginResponse failure(String message, String errorCode) {
        return LoginResponse.builder()
            .success(false)
            .message(message)
            .error(errorCode)
            .build();
    }
}
