package com.medi.backend.global.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;

@Component
public class AuthUtil {
    
    private final UserMapper userMapper;

    public AuthUtil(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Integer getCurrentUserId() {
        // get Authentication information(login) from HttpSession of Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // no login or anonymousUser
        if (authentication == null || !authentication.isAuthenticated() 
            || "anonymousUser".equals(authentication.getName())) {
            return null;  // not logged in
        }

        // get email(name) from login and find UserDto
        String email = authentication.getName();
        UserDTO user = userMapper.findByEmail(email);

        return user != null ? user.getId() : null;  // user found → return ID, not found → null
    }

    public boolean isAdmin() {
        // get Authentication information
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // no login or anonymousUser
        if (authentication == null || !authentication.isAuthenticated() 
            || "anonymousUser".equals(authentication.getName())) {
            return false;
        }
        
        // Check if user has ROLE_ADMIN authority
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }
}