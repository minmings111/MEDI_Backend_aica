package com.medi.backend.global.security.handler;

import com.medi.backend.global.security.dto.CustomUserDetails;
import com.medi.backend.user.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 핸들러
 * Google 로그인 성공 시 세션 설정 및 리다이렉트 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    /**
     * OAuth2 로그인 성공 시 호출
     * 
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param authentication 인증 정보
     * @throws IOException IO 예외
     * @throws ServletException 서블릿 예외
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
        
        log.info("OAuth2 로그인 성공 처리 시작");
        
        // OAuth2User에서 사용자 정보 추출
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        
        // 사용자 정보 생성
        UserDTO user = UserDTO.builder()
            .id((Integer) oauth2User.getAttribute("userId"))
            .email((String) oauth2User.getAttribute("email"))
            .name((String) oauth2User.getAttribute("name"))
            .role((String) oauth2User.getAttribute("role"))
            .provider((String) oauth2User.getAttribute("provider"))
            .providerId((String) oauth2User.getAttribute("providerId"))
            .profileImage((String) oauth2User.getAttribute("profileImage"))
            .password("") // OAuth2 사용자는 비밀번호 없음
            .build();
        
        // CustomUserDetails 생성 (일반 로그인과 동일한 방식)
        CustomUserDetails customUserDetails = new CustomUserDetails(user);
        
        // SecurityContext에 CustomUserDetails로 인증 정보 저장
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authToken = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                customUserDetails, 
                null, 
                customUserDetails.getAuthorities()
            );
        securityContext.setAuthentication(authToken);
        SecurityContextHolder.setContext(securityContext);
        
        // 세션에 SecurityContext 저장 (일반 로그인과 동일한 방식)
        HttpSession session = request.getSession(true);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
            securityContext
        );
        session.setMaxInactiveInterval(30 * 60); // 30분
        
        log.info("OAuth2 로그인 성공: userId={}, email={}, provider={}, 세션 ID={}", 
                user.getId(), user.getEmail(), user.getProvider(), session.getId());
        
        // 프론트엔드로 리다이렉트
        String targetUrl = determineTargetUrl(request, response, authentication);
        
        if (response.isCommitted()) {
            log.warn("응답이 이미 커밋되었습니다. 리다이렉트할 수 없습니다: {}", targetUrl);
            return;
        }
        
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
    
    /**
     * 리다이렉트 URL 결정
     * - OAuth2 로그인 시 항상 대시보드로 이동
     * 
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @param authentication 인증 정보
     * @return 리다이렉트 URL
     */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, 
                                       HttpServletResponse response, 
                                       Authentication authentication) {
        
        // CORS 설정에서 첫 번째 허용 도메인 가져오기 (프론트엔드 URL)
        String frontendUrl = allowedOrigins.split(",")[0];
        
        // 사용자 ID 추출 (로깅용)
        Object principal = authentication.getPrincipal();
        Integer userId = null;
        
        if (principal instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) principal).getId();
        } else if (principal instanceof OAuth2User) {
            Object userIdAttr = ((OAuth2User) principal).getAttribute("userId");
            if (userIdAttr instanceof Integer) {
                userId = (Integer) userIdAttr;
            }
        }
        
        // OAuth2 로그인 시 항상 대시보드로 이동
        String redirectUrl = frontendUrl + "/dashboard";
        
        log.info("OAuth2 로그인 성공 후 리다이렉트: userId={}, fullUrl={}", userId, redirectUrl);
        
        return redirectUrl;
    }
}

