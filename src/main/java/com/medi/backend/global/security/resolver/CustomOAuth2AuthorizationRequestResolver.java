package com.medi.backend.global.security.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
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
        return resolve(request, null);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        // 기본 리졸버로 인증 요청 생성
        OAuth2AuthorizationRequest originalRequest = defaultResolver.resolve(request, clientRegistrationId);
        
        if (originalRequest == null) {
            return null;
        }

        // prompt=select_account를 강제로 추가
        // 기존 additionalParameters를 복사하고 prompt 파라미터를 추가/덮어쓰기
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(originalRequest);
        
        // 기존 additionalParameters 복사
        Map<String, Object> additionalParams = new HashMap<>(originalRequest.getAdditionalParameters());
        
        // prompt 파라미터를 select_account로 강제 설정
        // 이렇게 하면 로그아웃 후에도 계정 선택 화면이 나타남
        additionalParams.put("prompt", "select_account");
        
        // 수정된 additionalParameters 설정
        builder.additionalParameters(additionalParams);

        OAuth2AuthorizationRequest modifiedRequest = builder.build();
        
        log.debug("OAuth2 authorization request: Added prompt=select_account to force account selection");
        
        log.info("OAuth2 authorization request modified: clientRegistrationId={}, prompt=select_account", 
                clientRegistrationId);
        
        return modifiedRequest;
    }
}

