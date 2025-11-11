package com.medi.backend.global.security.service;

import com.medi.backend.auth.dto.OAuth2UserInfo;
import com.medi.backend.auth.service.OAuth2AuthService;
import com.medi.backend.user.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 커스텀 OAuth2 사용자 서비스
 * Spring Security OAuth2 로그인 시 사용자 정보를 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    
    private final OAuth2AuthService oauth2AuthService;
    
    /**
     * OAuth2 사용자 정보 로드 및 처리
     * Google 로그인 성공 후 호출됨
     * 
     * @param userRequest OAuth2 사용자 요청 정보
     * @return OAuth2User 객체
     * @throws OAuth2AuthenticationException OAuth2 인증 예외
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("OAuth2 사용자 정보 로드 시작");
        
        // 1. Google에서 사용자 정보 가져오기
        OAuth2User oauth2User = super.loadUser(userRequest);
        
        // 2. Provider 정보 추출 (google)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 Provider: {}", registrationId);
        
        // 3. Google 사용자 정보 추출
        Map<String, Object> attributes = oauth2User.getAttributes();
        log.debug("OAuth2 사용자 attributes: {}", attributes);
        
        OAuth2UserInfo oauth2UserInfo = oauth2AuthService.extractGoogleUserInfo(attributes);
        
        // 4. 사용자 처리 (회원가입 또는 로그인)
        UserDTO user = oauth2AuthService.processOAuth2User(oauth2UserInfo);
        
        // 5. Spring Security OAuth2User 객체로 변환
        // 사용자 정보를 attributes에 추가하여 Handler에서 사용할 수 있도록 함
        Map<String, Object> modifiedAttributes = new HashMap<>(attributes);
        modifiedAttributes.put("userId", user.getId());
        modifiedAttributes.put("email", user.getEmail());
        modifiedAttributes.put("name", user.getName());
        modifiedAttributes.put("role", user.getRole());
        modifiedAttributes.put("provider", user.getProvider());
        modifiedAttributes.put("providerId", user.getProviderId());
        modifiedAttributes.put("profileImage", user.getProfileImage());
        
        String userNameAttributeName = userRequest.getClientRegistration()
            .getProviderDetails()
            .getUserInfoEndpoint()
            .getUserNameAttributeName();
        
        // sub (Google 고유 ID)를 사용자 이름 속성으로 사용
        if (userNameAttributeName == null || userNameAttributeName.isEmpty()) {
            userNameAttributeName = "sub";
        }
        
        log.info("OAuth2 사용자 정보 로드 완료: userId={}, email={}", user.getId(), user.getEmail());
        
        return new DefaultOAuth2User(
            java.util.Collections.singleton(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole())),
            modifiedAttributes,
            userNameAttributeName
        );
    }
}

