package com.medi.backend.auth.service;

import com.medi.backend.auth.dto.OAuth2UserInfo;
import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 인증 서비스
 * Google OAuth2 회원가입 및 로그인 비즈니스 로직 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2AuthService {
    
    private final UserMapper userMapper;
    
    /**
     * OAuth2 사용자 처리 (회원가입 또는 로그인)
     * 
     * @param oauth2UserInfo OAuth2에서 받은 사용자 정보
     * @return 처리된 사용자 정보
     */
    @Transactional
    public UserDTO processOAuth2User(OAuth2UserInfo oauth2UserInfo) {
        log.info("OAuth2 사용자 처리 시작: provider={}, email={}", 
                oauth2UserInfo.getProvider(), oauth2UserInfo.getEmail());
        
        // 1. Provider ID로 기존 사용자 확인
        UserDTO existingUser = userMapper.findByProviderAndProviderId(
            oauth2UserInfo.getProvider(), 
            oauth2UserInfo.getProviderId()
        );
        
        if (existingUser != null) {
            // 기존 Google 사용자 → 로그인
            log.info("기존 Google 사용자 로그인: email={}, userId={}", 
                    existingUser.getEmail(), existingUser.getId());
            return existingUser;
        }
        
        // 2. 이메일로 기존 일반 사용자 확인 (이메일 중복 체크)
        UserDTO emailUser = userMapper.findByEmail(oauth2UserInfo.getEmail());
        if (emailUser != null && "LOCAL".equals(emailUser.getProvider())) {
            // 이미 일반 회원가입으로 가입된 이메일
            log.warn("이미 일반 회원가입으로 가입된 이메일입니다: {}", oauth2UserInfo.getEmail());
            throw new RuntimeException("이미 가입된 이메일입니다. 일반 로그인을 이용해주세요.");
        }
        
        // 3. 신규 Google 사용자 → 자동 회원가입
        UserDTO newUser = UserDTO.builder()
            .email(oauth2UserInfo.getEmail())
            .name(oauth2UserInfo.getName())
            .provider(oauth2UserInfo.getProvider())
            .providerId(oauth2UserInfo.getProviderId())
            .profileImage(oauth2UserInfo.getProfileImage())
            .password(null)              // OAuth 사용자는 비밀번호 없음
            .phone(null)                 // OAuth 사용자는 전화번호 없음
            .isTermsAgreed(true)         // OAuth 로그인은 자동 약관 동의
            .role("USER")
            .build();
        
        // 데이터베이스에 저장
        int result = userMapper.insertOAuth2User(newUser);
        
        if (result > 0) {
            log.info("Google OAuth2 회원가입 완료: email={}, userId={}", 
                    newUser.getEmail(), newUser.getId());
            return newUser;
        } else {
            log.error("OAuth2 사용자 저장 실패: {}", oauth2UserInfo.getEmail());
            throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다.");
        }
    }
    
    /**
     * Google OAuth2 사용자 정보 추출
     * 
     * @param attributes Google에서 받은 사용자 속성 (attributes)
     * @return OAuth2UserInfo 객체
     */
    public OAuth2UserInfo extractGoogleUserInfo(java.util.Map<String, Object> attributes) {
        log.debug("Google 사용자 정보 추출: {}", attributes);
        
        return OAuth2UserInfo.builder()
            .provider("GOOGLE")
            .providerId((String) attributes.get("sub"))           // Google 고유 ID
            .email((String) attributes.get("email"))
            .name((String) attributes.get("name"))
            .profileImage((String) attributes.get("picture"))      // 프로필 이미지 URL
            .emailVerified((Boolean) attributes.get("email_verified"))
            .build();
    }
    
    /**
     * 사용자 ID로 사용자 조회
     * 
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    public UserDTO getUserById(Integer userId) {
        // UserMapper에는 ID로 조회하는 메서드가 없으므로, 필요시 추가
        // 현재는 세션에서 사용자 정보를 가져오므로 생략 가능
        return null;
    }
}

