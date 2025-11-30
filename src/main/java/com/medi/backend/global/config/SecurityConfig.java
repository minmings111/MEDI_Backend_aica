package com.medi.backend.global.config;

import com.medi.backend.global.security.service.CustomUserDetailsService;
import com.medi.backend.global.security.service.CustomOAuth2UserService;
import com.medi.backend.global.security.handler.OAuth2AuthenticationSuccessHandler;
import com.medi.backend.global.security.handler.OAuth2AuthenticationFailureHandler;
import com.medi.backend.global.security.resolver.CustomOAuth2AuthorizationRequestResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 전역 설정
 * - 세션 기반 인증
 * - 기존 회원가입 시스템과 호환
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enable method-level security settings
public class SecurityConfig {
    
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;  // application.yml의 CORS 허용 출처
    
    @Autowired
    private CustomUserDetailsService userDetailsService;  // 커스텀 사용자 인증 서비스
    
    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;  // OAuth2 사용자 서비스
    
    @Autowired
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;  // OAuth2 성공 핸들러
    
    @Autowired
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;  // OAuth2 실패 핸들러
    
    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;  // OAuth2 클라이언트 등록 정보
    
    /**
     * 비밀번호 암호화 Bean (BCrypt 알고리즘)
     * - 회원가입 시 비밀번호 암호화
     * - 로그인 시 비밀번호 검증
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /**
     * 인증 제공자 설정 (기존 회원가입 시스템과 호환)
     * Spring Security 6.x에서 DaoAuthenticationProvider 직접 사용은 deprecated되었지만
     * 기존 시스템과의 호환성을 위해 유지
     */
    @Bean
    @SuppressWarnings("deprecation")
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);  // 커스텀 UserDetailsService 사용
        authProvider.setPasswordEncoder(passwordEncoder());      // BCrypt 암호화 사용
        authProvider.setHideUserNotFoundExceptions(false);       // 사용자 찾기 실패 예외 노출
        return authProvider;
    }
    
    /**
     * 인증 매니저 Bean
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    /**
     * 세션 레지스트리 Bean (동시 세션 관리용)
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
    
    /**
     * 인증 실패 시 JSON 응답 (React API 호환)
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(
                "{\"success\":false,\"message\":\"로그인이 필요합니다\",\"error\":\"UNAUTHORIZED\"}"
            );
        };
    }
    
    /**
     * 권한 부족 시 JSON 응답 (React API 호환)
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write(
                "{\"success\":false,\"message\":\"접근 권한이 없습니다\",\"error\":\"FORBIDDEN\"}"
            );
        };
    }
    
    /**
     * Security 필터 체인 설정 (세션 기반 인증)
     * - 인증/인가 규칙
     * - 세션 관리
     * - CSRF, CORS 설정
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 인증 제공자 설정
            .authenticationProvider(authenticationProvider())
            
            // CSRF 설정 (상용화 전이므로 개발 편의를 위해 비활성화)
            .csrf(csrf -> csrf.disable())
            
            // CORS 설정 적용 (쿠키 허용)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 세션 관리 설정 - 동시 세션 제어
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)  // 필요시 세션 생성
                .maximumSessions(1)                                         // 동시 세션 1개로 제한 (보안 강화)
                .maxSessionsPreventsLogin(false)                            // 새 로그인 시 기존 세션 만료
                .sessionRegistry(sessionRegistry())                         // 세션 레지스트리 설정
            )
            
            // 세션 관리 설정 - 세션 고정 공격 방지
            // 기존: .maximumSessions(1).and().sessionFixation().changeSessionId()
            // 변경: 별도의 .sessionManagement() 블록으로 분리하여 .and() 제거
            // 향후 버전 호환성 확보 및 코드 가독성 향상
            .sessionManagement(session -> session
                .sessionFixation().changeSessionId()                       // 세션 고정 공격 방지
                // React API 호환: invalidSessionUrl 제거 (리다이렉트 대신 401 응답)
            )
            
            // SecurityContext 자동 저장 설정
            .securityContext(context -> context
                .requireExplicitSave(false)                                 // SecurityContext 자동 저장 활성화
            )
            
            // 인증 규칙 설정
            //여기는 나중에 리스트로 관리
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").hasRole("ADMIN") // health check

                .requestMatchers("/api/billing/plans").permitAll() // 플랜 전체 조회
                .requestMatchers("/api/billing/plans/{id}").permitAll() // 1개 플랜 조회

                .requestMatchers("/api/auth/login").permitAll()             // 로그인
                .requestMatchers("/api/auth/check-email").permitAll()       // 이메일 중복 확인
                .requestMatchers("/api/auth/register").permitAll()          // 회원가입
                .requestMatchers("/api/auth/send-verification").permitAll() // 이메일 인증 전송
                .requestMatchers("/api/auth/verify-email").permitAll()      // 이메일 인증 확인
                .requestMatchers("/api/auth/me").permitAll()                // 현재 세션 확인
                .requestMatchers("/api/auth/send-password-reset").permitAll() // 비밀번호 재설정 코드 전송
                .requestMatchers("/api/auth/reset-password").permitAll()    // 비밀번호 재설정
                .requestMatchers("/api/auth/oauth2/**").permitAll()         // OAuth2 API 엔드포인트
                .requestMatchers("/oauth2/**").permitAll()                  // OAuth2 엔드포인트
                .requestMatchers("/login/oauth2/**").permitAll()            // OAuth2 로그인 콜백
                .requestMatchers("/api/test/**").permitAll()
                .requestMatchers("/api/v1/analysis/**").permitAll()  // AI 서버에서 POST로 결과 전송
                .requestMatchers("/api/filter/prompt/**").permitAll()  // 에이전트용 프롬프트 조회 API
                .requestMatchers("/api/youtube/analysis/channel/save").permitAll()  // FastAPI Agent에서 분석 결과 저장
                .requestMatchers("/api/youtube/connect").authenticated()
                .requestMatchers("/api/youtube/oauth/callback").permitAll()
                .requestMatchers("/api/youtube/token/status").authenticated()
                .requestMatchers("/swagger-ui/**").permitAll()              // Swagger UI
                .requestMatchers("/v3/api-docs/**").permitAll()             // Swagger API Docs
                .requestMatchers("/swagger-ui.html").permitAll()            // Swagger 메인
                .anyRequest().authenticated()                               // 나머지는 인증 필요
            )
            
            // 기본 폼 로그인 비활성화 (REST API 사용)
            .formLogin(form -> form.disable())
            
            // HTTP Basic 인증 비활성화
            .httpBasic(basic -> basic.disable())
            
            // React API 호환: 인증/권한 실패 시 JSON 응답
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint())  // 인증 실패 시 JSON 응답
                .accessDeniedHandler(accessDeniedHandler())            // 권한 부족 시 JSON 응답
            )
            
            // 로그아웃 설정 (React API 호환)
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                // React API 호환: logoutSuccessUrl 제거 (리다이렉트 대신 JSON 응답)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "MEDI_SESSION")  // 세션 쿠키도 함께 삭제
                .permitAll()
            )
            
            // OAuth2 로그인 설정 (Google)
            .oauth2Login(oauth2 -> oauth2
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/code/*")                         // OAuth2 리다이렉트 엔드포인트
                )
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(authorizationRequestRepository())  // OAuth2 인증 요청 저장소 명시적 설정
                    .authorizationRequestResolver(customOAuth2AuthorizationRequestResolver())  // 커스텀 리졸버 적용 (prompt=select_account 강제)
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(customOAuth2UserService)
                )
                .successHandler(oAuth2AuthenticationSuccessHandler)          // OAuth2 로그인 성공 핸들러
                .failureHandler(oAuth2AuthenticationFailureHandler)          // OAuth2 로그인 실패 핸들러
            );
        
        return http.build();
    }
    
    /**
     * CORS 설정 (프론트엔드와 통신하기 위해 필수)
     * - 세션 쿠키 허용
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용할 출처 (프론트엔드 주소)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        
        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // 허용할 헤더
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // 인증 정보 포함 여부 (세션 쿠키를 위해 true로 설정)
        configuration.setAllowCredentials(true);
        
        // preflight 요청 캐시 시간 (1시간)
        configuration.setMaxAge(3600L);
        
        // 모든 경로에 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
    
    /**
     * OAuth2 인증 요청 저장소 Bean
     * 세션 기반으로 OAuth2 인증 요청을 저장하여 authorization_request_not_found 에러 방지
     * 
     * @return HttpSessionOAuth2AuthorizationRequestRepository
     */
    @Bean
    public HttpSessionOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }
    
    /**
     * 커스텀 OAuth2 인증 요청 리졸버 Bean
     * Google OAuth 로그인 시 prompt=select_account를 강제로 추가하여
     * 로그아웃 후에도 자동 로그인되는 문제를 해결합니다.
     * 
     * @return CustomOAuth2AuthorizationRequestResolver
     */
    @Bean
    public CustomOAuth2AuthorizationRequestResolver customOAuth2AuthorizationRequestResolver() {
        return new CustomOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"  // OAuth2 인증 요청 기본 URI
        );
    }
}
