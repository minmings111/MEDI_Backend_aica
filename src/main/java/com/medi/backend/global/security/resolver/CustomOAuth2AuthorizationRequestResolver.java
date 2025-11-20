package com.medi.backend.global.security.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 인증 요청 리졸버 커스터마이징
 * 
 * Google OAuth 로그인 시 prompt=select_account를 강제로 추가하여
 * 로그아웃 후에도 자동 로그인되는 문제를 해결합니다.
 * 
 * application.yml의 additional-parameters가 적용되지 않는 경우를 대비해
 * 코드에서 강제로 prompt 파라미터를 추가합니다.
 */
@Slf4j
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomOAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            String authorizationRequestBaseUri) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        // [수정] defaultResolver.resolve(request)를 호출하여 URL에서 registrationId를 자동 추출하도록 함
        OAuth2AuthorizationRequest originalRequest = defaultResolver.resolve(request);
        
        if (originalRequest == null) {
            return null;
        }
        
        return customizeAuthorizationRequest(originalRequest);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        // 기본 리졸버로 인증 요청 생성
        OAuth2AuthorizationRequest originalRequest = defaultResolver.resolve(request, clientRegistrationId);
        
        if (originalRequest == null) {
            return null;
        }

        return customizeAuthorizationRequest(originalRequest);
    }

    /**
     * 공통 커스터마이징 로직 분리
     */
    private OAuth2AuthorizationRequest customizeAuthorizationRequest(OAuth2AuthorizationRequest originalRequest) {
        // prompt=select_account를 강제로 추가
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(originalRequest);
        
        Map<String, Object> additionalParams = new HashMap<>(originalRequest.getAdditionalParameters());
        additionalParams.put("prompt", "select_account");
        
        builder.additionalParameters(additionalParams);

        OAuth2AuthorizationRequest modifiedRequest = builder.build();
        
        log.debug("OAuth2 authorization request: Added prompt=select_account");
        
        return modifiedRequest;
    }
}

