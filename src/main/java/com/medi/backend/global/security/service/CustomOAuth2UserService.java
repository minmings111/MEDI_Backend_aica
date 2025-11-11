package com.medi.backend.global.security.service;

import com.medi.backend.auth.dto.OAuth2UserInfo;
import com.medi.backend.auth.service.OAuth2AuthService;
import com.medi.backend.user.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 커스텀 OIDC 사용자 서비스
 * Google OAuth2 로그인 시 사용자 정보를 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends OidcUserService {

    private final OAuth2AuthService oauth2AuthService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("[OAuth2][DEBUG] CustomOAuth2UserService.loadUser (OIDC) invoked. registrationId={}, clientId={}",
                userRequest.getClientRegistration().getRegistrationId(),
                userRequest.getClientRegistration().getClientId());

        OidcUser oidcUser = super.loadUser(userRequest);
        log.debug("[OAuth2][DEBUG] raw OIDC attributes: {}", oidcUser.getAttributes());

        Map<String, Object> attributes = new HashMap<>(oidcUser.getAttributes());
        Map<String, Object> augmentedAttributes = processAndAugmentAttributes(
                userRequest.getClientRegistration().getRegistrationId(),
                attributes);

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        if (userNameAttributeName == null || userNameAttributeName.isEmpty()) {
            userNameAttributeName = "sub";
        }

        OidcIdToken idToken = userRequest.getIdToken();
        OidcUserInfo userInfo = new OidcUserInfo(augmentedAttributes);
        Collection<? extends GrantedAuthority> authorities = oidcUser.getAuthorities();

        return new DefaultOidcUser(authorities, idToken, userInfo, userNameAttributeName);
    }

    private Map<String, Object> processAndAugmentAttributes(String registrationId, Map<String, Object> attributes) {
        log.info("[OAuth2][DEBUG] registrationId={}, attributeKeys={}", registrationId, attributes.keySet());
        log.debug("[OAuth2][DEBUG] attributes before mapping: {}", attributes);

        OAuth2UserInfo oauth2UserInfo = oauth2AuthService.extractGoogleUserInfo(attributes);
        log.info("[OAuth2][DEBUG] extracted user info: provider={}, email={}, providerId={}",
                oauth2UserInfo.getProvider(), oauth2UserInfo.getEmail(), oauth2UserInfo.getProviderId());

        UserDTO user = oauth2AuthService.processOAuth2User(oauth2UserInfo);
        log.info("[OAuth2][DEBUG] processOAuth2User returned: id={}, provider={}, email={}, providerId={}",
                user.getId(), user.getProvider(), user.getEmail(), user.getProviderId());

        Map<String, Object> augmented = new HashMap<>(attributes);
        augmented.put("userId", user.getId());
        augmented.put("email", user.getEmail());
        augmented.put("name", user.getName());
        augmented.put("role", user.getRole());
        augmented.put("provider", user.getProvider());
        augmented.put("providerId", user.getProviderId());
        augmented.put("profileImage", user.getProfileImage());
        return augmented;
    }
}

