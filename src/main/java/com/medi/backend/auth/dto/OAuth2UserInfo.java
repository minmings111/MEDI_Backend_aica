package com.medi.backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 사용자 정보 DTO
 * Google OAuth2에서 받은 사용자 정보를 담는 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2UserInfo {
    
    /**
     * OAuth2 Provider (GOOGLE)
     */
    private String provider;
    
    /**
     * Provider에서 제공하는 고유 ID (Google sub)
     */
    private String providerId;
    
    /**
     * 사용자 이메일
     */
    private String email;
    
    /**
     * 사용자 이름
     */
    private String name;
    
    /**
     * 프로필 이미지 URL
     */
    private String profileImage;
    
    /**
     * 이메일 인증 여부
     */
    private Boolean emailVerified;
}

