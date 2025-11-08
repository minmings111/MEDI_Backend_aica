package com.medi.backend.auth.controller;

import com.medi.backend.user.dto.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 인증 컨트롤러
 * Google OAuth2 회원가입 및 로그인 API 엔드포인트
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2 인증", description = "Google OAuth2 회원가입 및 로그인 API")
public class OAuth2AuthController {
    
    /**
     * Google OAuth2 로그인 URL 제공
     * 프론트엔드에서 이 URL로 리다이렉트하여 Google 로그인 시작
     * 
     * @return Google 로그인 URL
     */
    @GetMapping("/google/url")
    @Operation(summary = "Google 로그인 URL 조회", description = "Google OAuth2 로그인을 시작하기 위한 URL을 반환합니다.")
    public ResponseEntity<Map<String, String>> getGoogleLoginUrl() {
        log.info("Google OAuth2 로그인 URL 요청");
        
        String googleAuthUrl = "/oauth2/authorization/google";
        
        Map<String, String> response = new HashMap<>();
        response.put("url", googleAuthUrl);
        response.put("message", "Google 로그인 URL입니다. 이 URL로 리다이렉트하세요.");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * OAuth2 로그인 사용자 정보 조회
     * 로그인 성공 후 세션에서 사용자 정보 가져오기
     * 
     * @param session HTTP 세션
     * @return 사용자 정보
     */
    @GetMapping("/user")
    @Operation(summary = "OAuth2 사용자 정보 조회", description = "OAuth2 로그인한 사용자의 정보를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getOAuth2User(HttpSession session) {
        log.info("OAuth2 사용자 정보 조회 요청");
        
        UserDTO user = (UserDTO) session.getAttribute("user");
        
        if (user == null) {
            log.warn("세션에 사용자 정보가 없습니다.");
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다."
            ));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", user);
        response.put("message", "사용자 정보 조회 성공");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * OAuth2 로그인 상태 확인
     * 
     * @param session HTTP 세션
     * @return 로그인 상태
     */
    @GetMapping("/status")
    @Operation(summary = "OAuth2 로그인 상태 확인", description = "현재 OAuth2 로그인 상태를 확인합니다.")
    public ResponseEntity<Map<String, Object>> getOAuth2Status(HttpSession session) {
        log.info("OAuth2 로그인 상태 확인 요청");
        
        UserDTO user = (UserDTO) session.getAttribute("user");
        boolean isLoggedIn = user != null;
        
        Map<String, Object> response = new HashMap<>();
        response.put("isLoggedIn", isLoggedIn);
        
        if (isLoggedIn) {
            response.put("provider", user.getProvider());
            response.put("email", user.getEmail());
            response.put("name", user.getName());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * OAuth2 로그아웃
     * 
     * @param session HTTP 세션
     * @return 로그아웃 결과
     */
    @PostMapping("/logout")
    @Operation(summary = "OAuth2 로그아웃", description = "OAuth2 로그인 세션을 종료합니다.")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        log.info("OAuth2 로그아웃 요청");
        
        session.invalidate();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "로그아웃 성공");
        
        return ResponseEntity.ok(response);
    }
}

