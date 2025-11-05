package com.medi.backend.user.dto;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password")
public class UserDTO {
    private Integer id;
    private String email;

    @JsonIgnore
    private String password;

    private String name;
    private String phone;
    private Boolean isTermsAgreed;
    private String role;
    private String createdAt;
    private String updatedAt;
    
    // OAuth2 관련 필드
    private String provider;        // 로그인 방식 (LOCAL/GOOGLE)
    private String providerId;      // Google sub ID
    private String profileImage;    // Google 프로필 이미지 URL
}