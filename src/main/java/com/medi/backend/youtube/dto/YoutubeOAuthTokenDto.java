package com.medi.backend.youtube.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YoutubeOAuthTokenDto {
    private Integer id;
    private Integer userId;
    private String googleEmail;
    private String accessToken;            // 암호화 저장 문자열
    private String refreshToken;           // 암호화 저장 문자열
    private String accessTokenExpiresAt;   // DATETIME 문자열 매핑(MyBatis에서 처리)
    private String tokenStatus;            // ACTIVE/REVOKED/EXPIRED
    private String createdAt;
    private String updatedAt;
    private String lastUsedAt;
    private String lastRefreshedAt;
}


