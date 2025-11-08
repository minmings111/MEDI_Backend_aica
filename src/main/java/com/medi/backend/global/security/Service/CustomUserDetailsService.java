package com.medi.backend.global.security.service;

import com.medi.backend.global.security.dto.CustomUserDetails;
import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security UserDetailsService 구현
 * - 기존 회원가입 시스템과 호환
 * - 로그인 시 사용자 정보 조회 및 권한 설정
 * - CustomUserDetails 반환 (userId 포함하여 DB 조회 최소화)
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
        
        log.debug("사용자 인증 정보 로드 완료: {} (userId: {}, 권한: {})", 
                email, user.getId(), user.getRole());
        
        // 3. CustomUserDetails 반환 (userId, name, role 등 모든 정보 포함)
        return new CustomUserDetails(user);
    }
}
