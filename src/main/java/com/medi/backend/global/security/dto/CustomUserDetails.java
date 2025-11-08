package com.medi.backend.global.security.dto;

import com.medi.backend.user.dto.UserDTO;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetails 구현
 * - 세션에 userId, name, role 등 모든 정보 저장
 * - DB 조회 없이 사용자 정보 접근 가능
 */
@Getter
public class CustomUserDetails implements UserDetails {
    
    private final Integer id;           // 사용자 ID (DB 조회용)
    private final String email;        // 이메일
    private final String password;      // 비밀번호 (암호화됨)
    private final String name;         // 이름
    private final String role;         // 권한 (USER, ADMIN)
    private final Collection<? extends GrantedAuthority> authorities;
    
    public CustomUserDetails(UserDTO user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.name = user.getName();
        this.role = user.getRole();
        this.authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + user.getRole())
        );
    }
    
    @Override
    public String getUsername() {
        return email;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}

