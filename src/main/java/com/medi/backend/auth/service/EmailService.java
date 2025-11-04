package com.medi.backend.auth.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 이메일 전송 서비스
 */
@Service
public class EmailService {
    
    @Autowired
    private JavaMailSender mailSender;  // Spring Boot가 자동으로 설정
    
    @Value("${spring.mail.username}")
    private String fromEmail;  // 발신자 이메일 (application.yml에서 가져옴)
    
    /**
     * 이메일 인증 코드 전송
     * @param toEmail 수신자 이메일
     * @param code 6자리 인증 코드
     */
    public void sendVerificationEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        
        // 발신자
        message.setFrom(fromEmail);
        
        // 수신자
        message.setTo(toEmail);
        
        // 제목
        message.setSubject("[MEDI] 이메일 인증 코드");
        
        // 본문
        message.setText(
            "안녕하세요.\n\n" +
            "회원가입을 위한 이메일 인증 코드입니다.\n\n" +
            "인증 코드: " + code + "\n\n" +
            "이 코드는 5분간 유효합니다.\n\n" +
            "감사합니다."
        );
        
        // 전송
        mailSender.send(message);
    }
    
    /**
     * 비밀번호 재설정 이메일 전송
     * @param toEmail 수신자 이메일
     * @param code 6자리 인증 코드
     */
    public void sendPasswordResetEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        
        // 발신자
        message.setFrom(fromEmail);
        
        // 수신자
        message.setTo(toEmail);
        
        // 제목
        message.setSubject("[MEDI] 비밀번호 재설정 인증 코드");
        
        // 본문
        message.setText(
            "안녕하세요.\n\n" +
            "비밀번호 재설정을 위한 인증 코드입니다.\n\n" +
            "인증 코드: " + code + "\n\n" +
            "이 코드는 5분간 유효합니다.\n" +
            "본인이 요청하지 않았다면 이 이메일을 무시해주세요.\n\n" +
            "감사합니다."
        );
        
        // 전송
        mailSender.send(message);
    }
}
