package com.medi.backend.auth.service;

import com.medi.backend.auth.dto.LoginRequest;
import com.medi.backend.auth.dto.LoginResponse;
import com.medi.backend.auth.dto.RegisterRequest;
import com.medi.backend.user.dto.UserDTO;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    /**
     * 이메일 인증 코드 생성 및 전송
     */
    String sendVerificationCode(String email);
    
    /**
     * 이메일 인증 코드 검증
     */
    boolean verifyCode(String email, String code);
    
    /**
     * 회원가입 처리
     */
    UserDTO register(RegisterRequest request);
    
    /**
     * 이메일 중복 체크
     */
    boolean isEmailExists(String email);
    
    /**
     * 로그인 처리
     */
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);
    
    /**
     * 이메일로 사용자 조회
     */
    UserDTO getUserByEmail(String email);
    
    /**
     * 비밀번호 재설정용 인증 코드 전송
     */
    String sendPasswordResetCode(String email);
    
    /**
     * 비밀번호 재설정
     */
    boolean resetPassword(String email, String code, String newPassword);
    
    /**
     * 로그인 상태에서 비밀번호 변경
     */
    boolean changePassword(String email, String currentPassword, String newPassword);
}

