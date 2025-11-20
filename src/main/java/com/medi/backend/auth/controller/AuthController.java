package com.medi.backend.auth.controller;

import com.medi.backend.auth.dto.LoginRequest;
import com.medi.backend.auth.dto.LoginResponse;
import com.medi.backend.global.security.dto.CustomUserDetails;
import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 세션 기반 인증 컨트롤러
 * - Spring Security + HttpSession 사용
 * - 표준적인 세션 로그인 방식
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private com.medi.backend.auth.service.AuthService authService;
    
    @Autowired
    private org.springframework.security.core.session.SessionRegistry sessionRegistry;
    
    @Autowired
    private AuthUtil authUtil;

    /**
     * 로그인 API
     * POST /api/auth/login
     * 
     * 요청 예시:
     * {
     *   "email": "user@example.com",
     *   "password": "password123"
     * }
     * 
     * @param loginRequest 로그인 요청 (이메일, 비밀번호)
     * @param bindingResult 검증 결과
     * @param request HTTP 요청
     * @return 로그인 응답 (성공/실패)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {
        
        // 1. 입력값 검증
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldError().getDefaultMessage();
            log.warn("로그인 입력값 검증 실패: {}", errorMessage);
            
            return ResponseEntity
                .badRequest()
                .body(LoginResponse.failure(errorMessage, "VALIDATION_ERROR"));
        }
        
        try {
            log.info("로그인 시도: {}", loginRequest.getEmail());
            
            // 2. Spring Security 인증 처리
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(), 
                    loginRequest.getPassword()
                );
            
            Authentication authentication = authenticationManager.authenticate(authToken);
            
            // 3. SecurityContext에 인증 정보 저장
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            
            // 4. HttpSession에 SecurityContext 저장 (세션 기반 인증의 핵심)
            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                securityContext
            );
            
            // 5. 사용자 정보 조회 (응답용)
            UserDTO user = userMapper.findByEmail(loginRequest.getEmail());
            
            if (user == null) {
                log.error("인증 성공했으나 사용자 정보 없음: {}", loginRequest.getEmail());
                return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.failure("사용자 정보를 찾을 수 없습니다", "USER_NOT_FOUND"));
            }
            
            log.info("로그인 성공: {} (세션 ID: {})", loginRequest.getEmail(), session.getId());
            
            // 6. 성공 응답 반환 (DTO 사용)
            LoginResponse response = LoginResponse.success(user, session.getId());
            return ResponseEntity.ok(response);
            
        } catch (AuthenticationException e) {
            log.warn("로그인 실패: {} - {}", loginRequest.getEmail(), e.getMessage());
            
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(LoginResponse.failure(
                    "이메일 또는 비밀번호가 올바르지 않습니다", 
                    "INVALID_CREDENTIALS"
                ));
            
        } catch (Exception e) {
            log.error("로그인 처리 중 오류 발생: {}", e.getMessage(), e);
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(LoginResponse.failure(
                    "로그인 처리 중 오류가 발생했습니다", 
                    "INTERNAL_ERROR"
                ));
        }
    }

    /**
     * 로그아웃 API
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request, 
            HttpServletResponse httpResponse) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            HttpSession session = request.getSession(false);
            String sessionId = session != null ? session.getId() : "없음";
            
            // 1. SecurityContext 클리어
            SecurityContextHolder.clearContext();
            
            // 2. 세션 무효화
            if (session != null) {
                session.invalidate();
            }
            
            // 3. 세션 쿠키 명시적으로 삭제 (브라우저에서 완전 제거)
            clearSessionCookies(request, httpResponse);
            
            log.info("로그아웃 완료 (세션 ID: {})", sessionId);
            
            response.put("success", true);
            response.put("message", "로그아웃 되었습니다");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 발생: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "로그아웃 처리 중 오류가 발생했습니다");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 현재 로그인 상태 확인 API
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            HttpServletRequest request, 
            HttpServletResponse httpResponse) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 무효한 세션 ID가 전달될 수 있으므로 안전하게 처리
            HttpSession session = null;
            try {
                session = request.getSession(false);
            } catch (IllegalStateException e) {
                // 세션이 이미 무효화된 경우 (invalid session id)
                log.debug("무효한 세션 ID 감지: {}", e.getMessage());
                // 무효한 세션 쿠키 삭제
                clearSessionCookies(request, httpResponse);
            }
            
            // AuthUtil을 사용하여 DB 조회 없이 사용자 정보 가져오기
            CustomUserDetails user = authUtil.getCurrentUser();
            
            if (user != null && session != null) {
                response.put("success", true);
                response.put("authenticated", true);

                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("email", user.getEmail());
                userInfo.put("name", user.getName());
                userInfo.put("role", user.getRole() != null ? user.getRole() : "USER");
                response.put("user", userInfo);

                response.put("sessionId", session.getId());

                return ResponseEntity.ok(response);
            } else {
                // 세션이 없거나 무효한 경우 쿠키 정리
                if (session == null) {
                    clearSessionCookies(request, httpResponse);
                }
                
                response.put("success", true);
                response.put("authenticated", false);
                response.put("message", "로그인되지 않음");
                
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            log.error("사용자 정보 조회 중 오류 발생: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "사용자 정보 조회 중 오류가 발생했습니다");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 세션 쿠키 삭제 헬퍼 메서드
     */
    private void clearSessionCookies(HttpServletRequest request, HttpServletResponse httpResponse) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                String cookieName = cookie.getName();
                if ("MEDI_SESSION".equals(cookieName) || "JSESSIONID".equals(cookieName)) {
                    Cookie deleteCookie = new Cookie(cookieName, null);
                    deleteCookie.setPath("/");
                    deleteCookie.setMaxAge(0);
                    deleteCookie.setHttpOnly(true);
                    deleteCookie.setSecure(false);
                    httpResponse.addCookie(deleteCookie);
                    log.debug("무효한 세션 쿠키 삭제: {}", cookieName);
                }
            }
        }
    }

    /**
     * 회원탈퇴 API
     * DELETE /api/auth/withdraw
     * 
     * 프론트엔드에서 비밀번호 확인 후 호출
     */
    @DeleteMapping("/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawUser(
            HttpServletRequest request, 
            HttpServletResponse httpResponse) {
        Map<String, Object> response = new HashMap<>();
        String currentUserEmail = null;  // catch 블록에서도 사용 가능하도록 밖에 선언
        
        try {
            // 1. 세션에서 현재 로그인한 사용자 정보 가져오기 (DB 조회 없음)
            CustomUserDetails user = authUtil.getCurrentUser();
            
            if (user == null) {
                log.warn("비로그인 상태에서 회원탈퇴 시도");
                response.put("success", false);
                response.put("message", "로그인이 필요합니다");
                response.put("error", "UNAUTHORIZED");
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            currentUserEmail = user.getEmail();
            Integer userId = user.getId();
            
            log.info("회원탈퇴 시도: {} (ID: {})", currentUserEmail, userId);
            
            // 2. 회원탈퇴 처리 (실제 삭제)
            int result = userMapper.deleteUser(currentUserEmail);
            
            if (result > 0) {
                // 5. 완전한 세션 무효화 처리
                try {
                    // SecurityContext 클리어
                    SecurityContextHolder.clearContext();
                    
                    // 현재 세션 무효화
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        String sessionId = session.getId();
                        session.invalidate();
                        log.debug("세션 무효화 완료: {}", sessionId);
                    }
                    
                    // 응답 헤더에 세션 쿠키 삭제 지시 (브라우저에서 쿠키 완전 제거)
                    clearSessionCookies(request, httpResponse);
                    
                } catch (Exception sessionError) {
                    log.warn("세션 무효화 중 오류 발생: {}", sessionError.getMessage());
                    // 세션 오류가 있어도 탈퇴는 완료된 상태이므로 계속 진행
                }
                
                log.info("회원탈퇴 완료: {}", currentUserEmail);
                
                response.put("success", true);
                response.put("message", "회원탈퇴가 완료되었습니다");
                response.put("sessionCleared", true);
                
                return ResponseEntity.ok(response);
            } else {
                log.error("회원탈퇴 DB 처리 실패: {} (삭제된 행 수: {})", currentUserEmail, result);
                response.put("success", false);
                response.put("message", "회원탈퇴 처리 중 오류가 발생했습니다");
                response.put("error", "WITHDRAWAL_FAILED");
                response.put("details", "데이터베이스 삭제 실패");
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (org.springframework.dao.DataAccessException dbException) {
            // 데이터베이스 관련 오류
            log.error("회원탈퇴 DB 오류 발생: {} - {}", currentUserEmail, dbException.getMessage(), dbException);
            
            response.put("success", false);
            response.put("message", "데이터베이스 처리 중 오류가 발생했습니다");
            response.put("error", "DATABASE_ERROR");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (IllegalStateException stateException) {
            // 세션 상태 관련 오류
            log.error("회원탈퇴 세션 상태 오류: {} - {}", currentUserEmail, stateException.getMessage(), stateException);
            
            response.put("success", false);
            response.put("message", "세션 처리 중 오류가 발생했습니다");
            response.put("error", "SESSION_ERROR");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            // 기타 예상치 못한 오류
            log.error("회원탈퇴 처리 중 예상치 못한 오류 발생: {} - {}", 
                     currentUserEmail != null ? currentUserEmail : "unknown", 
                     e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "회원탈퇴 처리 중 오류가 발생했습니다");
            response.put("error", "INTERNAL_ERROR");
            response.put("details", e.getClass().getSimpleName());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 이메일 인증 코드 전송 API
     * POST /api/auth/send-verification
     */
    @PostMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmailDuplicate(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String email = request.get("email");

            if (email == null || email.trim().isEmpty()) {
                response.put("available", false);
                response.put("message", "이메일을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }

            boolean exists = authService.isEmailExists(email);

            if (exists) {
                response.put("available", false);
                response.put("message", "이미 사용 중인 이메일입니다");
            } else {
                response.put("available", true);
                response.put("message", "사용 가능한 이메일입니다");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("이메일 중복 확인 중 오류 발생: {}", e.getMessage(), e);
            response.put("available", false);
            response.put("message", "이메일 중복 확인 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 이메일 인증 코드 전송 API
     * POST /api/auth/send-verification
     */
    @PostMapping("/send-verification")
    public ResponseEntity<Map<String, Object>> sendVerificationCode(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String email = request.get("email");
            
            if (email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "이메일을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 이메일 중복 체크
            if (authService.isEmailExists(email)) {
                response.put("success", false);
                response.put("message", "이미 가입된 이메일입니다");
                return ResponseEntity.badRequest().body(response);
            }
            
            String code = authService.sendVerificationCode(email);
            log.info("인증 코드 전송: {} -> {}", email, code);
            
            response.put("success", true);
            response.put("message", "인증 코드가 전송되었습니다");
            response.put("expiresIn", 300); // 5분
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("인증 코드 전송 실패: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "인증 코드 전송에 실패했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 이메일 인증 코드 확인 API
     * POST /api/auth/verify-email
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String email = request.get("email");
            String code = request.get("code");
            
            if (email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "이메일을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (code == null || code.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "인증 코드를 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean isValid = authService.verifyCode(email, code);
            
            if (isValid) {
                response.put("success", true);
                response.put("message", "이메일 인증이 완료되었습니다");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "인증 코드가 올바르지 않거나 만료되었습니다");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("이메일 인증 실패: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "이메일 인증 처리 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 회원가입 API
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody com.medi.backend.auth.dto.RegisterRequest registerRequest) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 입력값 검증
            if (registerRequest.getEmail() == null || registerRequest.getEmail().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "이메일을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (registerRequest.getPassword() == null || registerRequest.getPassword().isEmpty()) {
                response.put("success", false);
                response.put("message", "비밀번호를 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (registerRequest.getName() == null || registerRequest.getName().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "이름을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (registerRequest.getIsTermsAgreed() == null || !registerRequest.getIsTermsAgreed()) {
                response.put("success", false);
                response.put("message", "약관에 동의해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 이메일 중복 체크
            if (authService.isEmailExists(registerRequest.getEmail())) {
                response.put("success", false);
                response.put("message", "이미 가입된 이메일입니다");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 회원가입 처리
            UserDTO newUser = authService.register(registerRequest);
            
            log.info("회원가입 완료: {} (ID: {})", newUser.getEmail(), newUser.getId());
            
            response.put("success", true);
            response.put("message", "회원가입이 완료되었습니다");
            response.put("user", Map.of(
                "id", newUser.getId(),
                "email", newUser.getEmail(),
                "name", newUser.getName(),
                "role", newUser.getRole()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "회원가입 처리 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 비밀번호 찾기 - 인증 코드 전송 API
     * POST /api/auth/send-password-reset
     * 
     * 📝 설명: 비밀번호를 잊었을 때 이메일로 인증 코드 전송 (비로그인 상태)
     */
    @PostMapping("/send-password-reset")
    public ResponseEntity<Map<String, Object>> sendPasswordResetCode(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String email = request.get("email");
            
            if (email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "이메일을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            String code = authService.sendPasswordResetCode(email);
            log.info("비밀번호 재설정 코드 전송: {} -> {}", email, code);
            
            response.put("success", true);
            response.put("message", "비밀번호 재설정 코드가 전송되었습니다");
            response.put("expiresIn", 300); // 5분
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            // 사용자 존재하지 않음
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            log.error("비밀번호 재설정 코드 전송 실패: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "비밀번호 재설정 코드 전송에 실패했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 비밀번호 찾기 - 새 비밀번호 설정 API
     * POST /api/auth/reset-password
     * 
     * 📝 설명: 인증 코드 확인 후 새 비밀번호로 재설정 (비로그인 상태)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @RequestBody com.medi.backend.auth.dto.PasswordResetRequest resetRequest,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 입력값 검증
            if (resetRequest.getEmail() == null || resetRequest.getEmail().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "이메일을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (resetRequest.getCode() == null || resetRequest.getCode().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "인증 코드를 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (resetRequest.getNewPassword() == null || resetRequest.getNewPassword().isEmpty()) {
                response.put("success", false);
                response.put("message", "새 비밀번호를 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 비밀번호 길이 검증
            if (resetRequest.getNewPassword().length() < 6) {
                response.put("success", false);
                response.put("message", "비밀번호는 6자리 이상이어야 합니다");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 비밀번호 재설정 처리
            boolean success = authService.resetPassword(
                resetRequest.getEmail(), 
                resetRequest.getCode(), 
                resetRequest.getNewPassword()
            );
            
            if (success) {
                // 🔒 보안: 비밀번호 변경 후 해당 사용자의 모든 세션 무효화
                invalidateUserSessions(resetRequest.getEmail(), request);
                
                log.info("비밀번호 재설정 및 세션 무효화 완료: {}", resetRequest.getEmail());
                
                response.put("success", true);
                response.put("message", "비밀번호가 성공적으로 변경되었습니다");
                response.put("sessionInvalidated", true);
                response.put("requireLogin", true);
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "인증 코드가 올바르지 않거나 만료되었습니다");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("비밀번호 재설정 실패: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "비밀번호 재설정 처리 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 비밀번호 재설정 (로그인 상태에서) API
     * PUT /api/auth/change-password
     * 
     * 📝 설명: 현재 비밀번호 확인 후 새 비밀번호로 변경 (로그인 상태 필수)
     */
    @PutMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody com.medi.backend.auth.dto.PasswordChangeRequest changeRequest,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. 세션에서 현재 로그인한 사용자 정보 가져오기 (DB 조회 없음)
            CustomUserDetails user = authUtil.getCurrentUser();
            
            if (user == null) {
                response.put("success", false);
                response.put("message", "로그인이 필요합니다");
                response.put("error", "UNAUTHORIZED");
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            String currentUserEmail = user.getEmail();
            
            // 2. 입력값 검증
            if (changeRequest.getCurrentPassword() == null || changeRequest.getCurrentPassword().isEmpty()) {
                response.put("success", false);
                response.put("message", "현재 비밀번호를 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (changeRequest.getNewPassword() == null || changeRequest.getNewPassword().isEmpty()) {
                response.put("success", false);
                response.put("message", "새 비밀번호를 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (changeRequest.getConfirmPassword() == null || changeRequest.getConfirmPassword().isEmpty()) {
                response.put("success", false);
                response.put("message", "새 비밀번호 확인을 입력해주세요");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 3. 새 비밀번호 일치 확인
            if (!changeRequest.getNewPassword().equals(changeRequest.getConfirmPassword())) {
                response.put("success", false);
                response.put("message", "새 비밀번호가 일치하지 않습니다");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 4. 비밀번호 길이 검증
            if (changeRequest.getNewPassword().length() < 6) {
                response.put("success", false);
                response.put("message", "새 비밀번호는 6자리 이상이어야 합니다");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 5. 현재 비밀번호와 새 비밀번호 동일 여부 확인
            if (changeRequest.getCurrentPassword().equals(changeRequest.getNewPassword())) {
                response.put("success", false);
                response.put("message", "새 비밀번호는 현재 비밀번호와 달라야 합니다");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 6. 비밀번호 변경 처리
            boolean success = authService.changePassword(
                currentUserEmail,
                changeRequest.getCurrentPassword(),
                changeRequest.getNewPassword()
            );
            
            if (success) {
                // 🔒 보안: 비밀번호 변경 후 해당 사용자의 모든 세션 무효화
                invalidateUserSessions(currentUserEmail, request);
                
                log.info("비밀번호 변경 및 세션 무효화 완료: {}", currentUserEmail);
                
                response.put("success", true);
                response.put("message", "비밀번호가 성공적으로 변경되었습니다");
                response.put("sessionInvalidated", true);
                response.put("requireLogin", true);
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "현재 비밀번호가 올바르지 않습니다");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("비밀번호 변경 실패: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "비밀번호 변경 처리 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 특정 사용자의 모든 세션 무효화 (비밀번호 변경 시)
     */
    private void invalidateUserSessions(String email, HttpServletRequest request) {
        try {
            // 1. 현재 요청의 세션도 무효화 (비밀번호 재설정 요청자도 재로그인 필요)
            HttpSession currentSession = request.getSession(false);
            if (currentSession != null) {
                currentSession.invalidate();
                log.debug("현재 세션 무효화: {}", currentSession.getId());
            }
            
            // 2. SessionRegistry를 통해 해당 사용자의 모든 세션 무효화
            sessionRegistry.getAllPrincipals().forEach(principal -> {
                if (principal instanceof org.springframework.security.core.userdetails.User) {
                    org.springframework.security.core.userdetails.User user = 
                        (org.springframework.security.core.userdetails.User) principal;
                    
                    if (email.equals(user.getUsername())) {
                        sessionRegistry.getAllSessions(principal, false).forEach(sessionInfo -> {
                            sessionInfo.expireNow();
                            log.debug("사용자 세션 무효화: {} - {}", email, sessionInfo.getSessionId());
                        });
                    }
                }
            });
            
            log.info("사용자 모든 세션 무효화 완료: {}", email);
            
        } catch (Exception e) {
            log.warn("세션 무효화 중 오류 발생: {} - {}", email, e.getMessage());
            // 세션 무효화 실패해도 비밀번호 변경은 성공으로 처리
        }
    }

}