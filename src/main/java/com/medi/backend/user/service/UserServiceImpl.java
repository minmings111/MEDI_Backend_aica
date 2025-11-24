package com.medi.backend.user.service;

import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 사용자 서비스 구현체
 * 
 * ⚠️ 메모리 주의:
 * - getAllUsers()는 전체 사용자 목록을 메모리에 로드하므로 대규모 데이터에서는 OOM 위험이 있습니다.
 * - 운영 환경에서는 페이징 도입 또는 API 비활성화를 권장합니다.
 */
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 전체 사용자 조회 (페이징 미적용)
     * ⚠️ 대량 데이터 환경에서는 페이징 쿼리로 교체해야 합니다.
     */
    @Override
    public List<UserDTO> getAllUsers() {
        // TODO: 페이징 지원 메서드로 교체 (selectAllUsersWithPaging 등)
        return userMapper.selectAllUsers();
    }
}