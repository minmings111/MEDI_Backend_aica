package com.medi.backend.global.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.medi.backend.global.security.dto.CustomUserDetails;

/**
 * 인증 관련 유틸리티 클래스
 * - 세션에서 현재 로그인한 사용자 정보 조회
 * - DB 조회 없이 메모리에서 바로 접근 (CustomUserDetails 사용)
 */
@Component
public class AuthUtil {

    /**
     * 현재 로그인한 사용자의 CustomUserDetails 반환
     * @return CustomUserDetails, 로그인하지 않은 경우 null
     */
    public CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        
        // anonymousUser인 경우
        if ("anonymousUser".equals(principal)) {
            return null;
        }
        
        // CustomUserDetails 타입인 경우
        if (principal instanceof CustomUserDetails) {
            return (CustomUserDetails) principal;
        }
        
        return null;
    }

    /**
     * 현재 로그인한 사용자 ID 반환 (DB 조회 없이 세션에서 가져옴)
     * @return 사용자 ID, 로그인하지 않은 경우 null
     */
    public Integer getCurrentUserId() {
        CustomUserDetails user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 현재 로그인한 사용자가 관리자인지 확인 (DB 조회 없이 세션에서 가져옴)
     * @return 관리자면 true, 아니면 false
     */
    public boolean isAdmin() {
        CustomUserDetails user = getCurrentUser();
        return user != null && "ADMIN".equals(user.getRole());
    }
}