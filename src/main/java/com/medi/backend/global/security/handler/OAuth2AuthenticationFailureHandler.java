package com.medi.backend.global.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 실패 핸들러
 * Google 로그인 실패 시 에러 처리 및 리다이렉트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    /**
     * OAuth2 로그인 실패 시 호출
     * 
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param exception 인증 예외
     * @throws IOException IO 예외
     * @throws ServletException 서블릿 예외
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       AuthenticationException exception) throws IOException, ServletException {
        
        log.error("OAuth2 로그인 실패: {}", exception.getMessage(), exception);
        
        // 에러 메시지 추출
        String errorMessage = exception.getLocalizedMessage();
        
        // CORS 설정에서 첫 번째 허용 도메인 가져오기 (프론트엔드 URL)
        String frontendUrl = allowedOrigins.split(",")[0];
        
        // 로그인 페이지로 리다이렉트 (에러 메시지 포함)
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
            .queryParam("error", errorMessage)
            .build()
            .toUriString();
        
        log.info("OAuth2 로그인 실패 후 리다이렉트: {}", targetUrl);
        
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

