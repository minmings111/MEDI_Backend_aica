package com.medi.backend.user.mapper;

import com.medi.backend.user.dto.UserDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    // 전체 사용자 조회
    List<UserDTO> selectAllUsers();
    

    /**
     * 이메일로 사용자 조회
     */
    UserDTO findByEmail(@Param("email") String email);
    
    /**
     * 사용자 정보 저장 (회원가입)
     */
    int insertUser(UserDTO user);
    
    /**
     * 이메일 존재 여부 확인 (중복 체크용)
     */
    int existsByEmail(@Param("email") String email);
    
    /**
     * 회원탈퇴 (사용자 삭제)
     */
    int deleteUser(@Param("email") String email);
    
    /**
     * 비밀번호 업데이트 (비밀번호 재설정)
     */
    int updatePassword(@Param("email") String email, @Param("password") String password);
}