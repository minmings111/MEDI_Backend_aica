package com.medi.backend.global.security.service;

import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security UserDetailsService 구현
 * - 기존 회원가입 시스템과 호환
 * - 로그인 시 사용자 정보 조회 및 권한 설정
 */
@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    @Autowired
    private UserMapper userMapper;  // 기존 회원가입에서 사용하는 매퍼 재사용
    
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("로그인 시도: {}", email);
        
        // 1. 기존 회원가입 시스템의 UserMapper를 사용하여 사용자 조회
        UserDTO user = userMapper.findByEmail(email);
        
        // 2. 사용자가 없으면 예외 발생
        if (user == null) {
            log.warn("사용자를 찾을 수 없습니다: {}", email);
            throw new UsernameNotFoundException("이메일 또는 비밀번호가 올바르지 않습니다");
        }
        
        // 3. 권한 설정 (기존 role 필드 활용)
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        
        log.debug("사용자 인증 정보 로드 완료: {} (권한: {})", email, user.getRole());
        
        // 4. Spring Security UserDetails 객체 반환
        return User.builder()
                .username(user.getEmail())          // 이메일을 username으로 사용
                .password(user.getPassword())       // 기존 BCrypt 암호화된 비밀번호
                .authorities(authorities)           // 권한 정보
                .accountExpired(false)              // 계정 만료 여부
                .accountLocked(false)               // 계정 잠금 여부
                .credentialsExpired(false)          // 자격 증명 만료 여부
                .disabled(false)                    // 계정 비활성화 여부
                .build();
    }
}
