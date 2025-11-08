package com.medi.backend.auth.service;


import com.medi.backend.auth.dto.EmailVerification;
import com.medi.backend.auth.dto.LoginRequest;
import com.medi.backend.auth.dto.LoginResponse;
import com.medi.backend.auth.dto.RegisterRequest;
import com.medi.backend.auth.mapper.AuthMapper;
import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 인증 서비스 구현체
 * - 기존 회원가입 시스템과 호환
 * - 세션 기반 로그인 구현
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    
    @Autowired
    private AuthMapper authMapper;  // 이메일 인증 DB 작업
    
    @Autowired
    private UserMapper userMapper;  // 사용자 정보 DB 작업
    
    @Autowired
    private PasswordEncoder passwordEncoder;  // 비밀번호 암호화 (BCrypt)
    
    @Autowired
    private EmailService emailService;  // 이메일 전송 (같은 패키지!)
    
    @Autowired
    private AuthenticationManager authenticationManager;  // Spring Security 인증 매니저
    
    private static final String CHARACTERS = "0123456789";  // 인증 코드 문자 (숫자만)
    private static final int CODE_LENGTH = 6;               // 인증 코드 길이 (6자리)
    private static final int EXPIRATION_MINUTES = 5;        // 만료 시간 (5분)
    private final SecureRandom random = new SecureRandom(); // 보안 난수 생성기
    
    /**
     * 이메일 인증 코드 생성 및 전송
     */
    @Override
    public String sendVerificationCode(String email) {
        // 1. 기존 인증 코드 삭제 (재전송 대비)
        authMapper.deleteByEmail(email);
        
        // 2. 6자리 랜덤 코드 생성
        String code = generateCode();
        
        // 3. 인증 정보 객체 생성
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setCode(code);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
        
        // 4. DB에 저장
        authMapper.insertVerification(verification);
        
        // 5. 이메일 전송 (선택사항 - 에러 발생해도 진행) //배포시 여기 부분 수정해야함 
        try {
            emailService.sendVerificationEmail(email, code);
            System.out.println("✅ 이메일 전송 성공: " + email);
        } catch (Exception e) {
            System.err.println("❌ 이메일 전송 실패: " + e.getMessage());
            // 개발 중에는 콘솔로 확인하므로 에러 무시
        }
        
        // 6. 생성된 코드 반환 (실제로는 이메일 확인)
        return code;
    }
    
    /**
     * 이메일 인증 코드 검증
     */
    @Override
    public boolean verifyCode(String email, String code) {
        // 1. DB에서 유효한 인증 정보 조회 (만료 시간 자동 체크)
        EmailVerification verification = authMapper.findByEmailAndCode(email, code);
        
        // 2. 조회 실패 시 (코드 틀림 or 만료됨)
        if (verification == null) {
            return false;
        }
        
        // 3. 인증 성공 후 코드 삭제 (재사용 방지)
        authMapper.deleteByEmail(email);
        
        return true;
    }
    
    /**
     * 회원가입 처리
     */
    @Override
    public UserDTO register(RegisterRequest request) {
        // 1. 비밀번호 암호화 (BCrypt)
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        // 2. UserDTO 객체 생성
        UserDTO user = new UserDTO();
        user.setEmail(request.getEmail());
        user.setPassword(encodedPassword);  // 암호화된 비밀번호
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setIsTermsAgreed(request.getIsTermsAgreed());
        user.setRole("USER");  // 기본 역할
        
        // 3. DB에 사용자 정보 저장
        userMapper.insertUser(user);
        
        // 4. 저장된 사용자 정보 반환 (id 포함)
        return user;
    }
    
    /**
     * 이메일 중복 체크
     */
    @Override
    public boolean isEmailExists(String email) {
        return userMapper.existsByEmail(email) > 0;
    }
    
    /**
     * 로그인 처리 (기존 회원가입 시스템과 호환)
     */
    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = new LoginResponse();
        
        try {
            log.debug("로그인 시도: {}", request.getEmail());
            
            // 1. 기본 입력값 검증
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                response.setSuccess(false);
                response.setMessage("이메일을 입력해주세요");
                return response;
            }
            
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                response.setSuccess(false);
                response.setMessage("비밀번호를 입력해주세요");
                return response;
            }
            
            // 2. 사용자 존재 여부 확인 (기존 UserMapper 활용)
            UserDTO user = userMapper.findByEmail(request.getEmail());
            if (user == null) {
                log.warn("존재하지 않는 사용자: {}", request.getEmail());
                response.setSuccess(false);
                response.setMessage("이메일 또는 비밀번호가 올바르지 않습니다");
                return response;
            }
            
            // 3. Spring Security를 통한 인증 처리
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
            
            Authentication authentication = authenticationManager.authenticate(authToken);
            
            // 4. 인증 성공 시 SecurityContext에 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 5. 세션에 SecurityContext 저장
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                               SecurityContextHolder.getContext());
            
            // 6. 응답 데이터 구성 (비밀번호 제외)
            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
            userInfo.setId(user.getId());
            userInfo.setEmail(user.getEmail());
            userInfo.setName(user.getName());
            userInfo.setRole(user.getRole());            
            response.setSuccess(true);
            response.setMessage("로그인 성공");
            response.setUser(userInfo);
            
            log.info("로그인 성공: {} (세션 ID: {})", request.getEmail(), session.getId());
            return response;
            
        } catch (BadCredentialsException e) {
            log.warn("로그인 실패 - 잘못된 자격 증명: {}", request.getEmail());
            response.setSuccess(false);
            response.setMessage("이메일 또는 비밀번호가 올바르지 않습니다");
            return response;
        } catch (Exception e) {
            log.error("로그인 처리 중 오류 발생: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setMessage("로그인 처리 중 오류가 발생했습니다");
            return response;
        }
    }
    
    /**
     * 이메일로 사용자 조회 (기존 시스템 활용)
     */
    @Override
    public UserDTO getUserByEmail(String email) {
        return userMapper.findByEmail(email);
    }
    
    /**
     * 비밀번호 재설정용 인증 코드 전송
     */
    @Override
    public String sendPasswordResetCode(String email) {
        // 1. 사용자 존재 여부 확인
        UserDTO user = userMapper.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("등록되지 않은 이메일입니다");
        }
        
        // 2. 기존 인증 코드 삭제 (재전송 대비)
        authMapper.deleteByEmail(email);
        
        // 3. 6자리 랜덤 코드 생성
        String code = generateCode();
        
        // 4. 인증 정보 객체 생성
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setCode(code);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
        
        // 5. DB에 저장
        authMapper.insertVerification(verification);
        
        // 6. 이메일 전송
        try {
            emailService.sendPasswordResetEmail(email, code);
            log.info("비밀번호 재설정 코드 전송 성공: {}", email);
        } catch (Exception e) {
            log.error("비밀번호 재설정 이메일 전송 실패: {}", e.getMessage());
            // 개발 중에는 콘솔로 확인하므로 에러 무시
        }
        
        return code;
    }
    
    /**
     * 비밀번호 재설정
     */
    @Override
    public boolean resetPassword(String email, String code, String newPassword) {
        // 1. 인증 코드 검증
        EmailVerification verification = authMapper.findByEmailAndCode(email, code);
        if (verification == null) {
            return false; // 코드가 틀리거나 만료됨
        }
        
        // 2. 새 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(newPassword);
        
        // 3. 비밀번호 업데이트
        int result = userMapper.updatePassword(email, encodedPassword);
        
        // 4. 인증 코드 삭제 (재사용 방지)
        authMapper.deleteByEmail(email);
        
        log.info("비밀번호 재설정 완료: {}", email);
        return result > 0;
    }
    
    /**
     * 로그인 상태에서 비밀번호 변경
     */
    @Override
    public boolean changePassword(String email, String currentPassword, String newPassword) {
        // 1. 사용자 존재 여부 확인
        UserDTO user = userMapper.findByEmail(email);
        if (user == null) {
            return false;
        }
        
        // 2. 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("비밀번호 변경 실패 - 현재 비밀번호 불일치: {}", email);
            return false;
        }
        
        // 3. 새 비밀번호 암호화
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        
        // 4. 비밀번호 업데이트
        int result = userMapper.updatePassword(email, encodedNewPassword);
        
        if (result > 0) {
            log.info("비밀번호 변경 완료: {}", email);
            return true;
        }
        
        return false;
    }

    /**
     * 6자리 랜덤 인증 코드 생성 (private 메서드)
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
}