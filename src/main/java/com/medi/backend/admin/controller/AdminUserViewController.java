package com.medi.backend.admin.controller;

import com.medi.backend.filter.dto.FilterPreferenceResponse;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.user.dto.UserDTO;
import com.medi.backend.user.mapper.UserMapper;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자용 사용자 화면 조회 컨트롤러 (읽기 전용)
 * 
 * ✅ 안전한 방식:
 * - 실제 사용자 계정으로 로그인하지 않음
 * - 비밀번호 불필요
 * - 사용자 동의 불필요 (서비스 제공을 위한 관리 목적)
 * - 읽기 전용 조회만 수행
 * - 모든 접근 로그 기록
 * 
 * ⚠️ 법적 고려사항:
 * - 개인정보보호법: 서비스 제공을 위한 최소한의 접근
 * - 접근 로그를 남겨 감사 추적 가능
 * - 민감한 정보는 마스킹 처리 권장
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserViewController {

    private final UserMapper userMapper;
    private final ChannelService channelService;
    private final FilterPreferenceService filterPreferenceService;
    private final AuthUtil authUtil;

    /**
     * 사용자 목록 조회 (관리자 전용)
     * GET /api/admin/users/list
     * 
     * @return 사용자 목록 (비밀번호 제외, 이메일 마스킹)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getUserList() {
        Integer adminId = authUtil.getCurrentUserId();
        log.info("📋 [관리자 조회] 사용자 목록 조회: adminId={}", adminId);
        
        try {
            List<UserDTO> users = userMapper.selectAllUsers();
            
            // 응답 데이터 구성 (비밀번호 제외, 이메일 마스킹)
            List<Map<String, Object>> userList = new java.util.ArrayList<>();
            for (UserDTO user : users) {
                if ("ADMIN".equals(user.getRole())) {
                    continue; // 관리자는 제외
                }
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("email", maskEmail(user.getEmail()));
                userMap.put("name", user.getName());
                userMap.put("role", user.getRole());
                userMap.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt() : "");
                userList.add(userMap);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("users", userList);
            response.put("totalCount", userList.size());
            
            log.info("✅ [관리자 조회] 사용자 목록 조회 완료: adminId={}, 사용자수={}명", adminId, userList.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [관리자 조회] 사용자 목록 조회 실패: adminId={}", adminId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "사용자 목록 조회 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 사용자 기본 정보 조회
     * GET /api/admin/users/{userId}/info
     * 
     * @param userId 조회할 사용자 ID
     * @return 사용자 기본 정보 (비밀번호 제외)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}/info")
    public ResponseEntity<Map<String, Object>> getUserInfo(@PathVariable("userId") Integer userId) {
        Integer adminId = authUtil.getCurrentUserId();
        log.info("📋 [관리자 조회] 사용자 정보 조회: adminId={}, targetUserId={}", adminId, userId);
        
        try {
            UserDTO user = userMapper.findById(userId);
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "사용자를 찾을 수 없습니다");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            // 비밀번호는 응답에 포함하지 않음
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", Map.of(
                "id", user.getId(),
                "email", maskEmail(user.getEmail()), // 이메일 마스킹 처리
                "name", user.getName(),
                "role", user.getRole(),
                "createdAt", user.getCreatedAt()
            ));
            
            log.info("✅ [관리자 조회] 사용자 정보 조회 완료: adminId={}, targetUserId={}", adminId, userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [관리자 조회] 사용자 정보 조회 실패: adminId={}, targetUserId={}", adminId, userId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "사용자 정보 조회 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 사용자의 채널 목록 조회 (사용자가 보는 화면과 동일)
     * GET /api/admin/users/{userId}/channels
     * 
     * @param userId 조회할 사용자 ID
     * @return 사용자의 채널 목록
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}/channels")
    public ResponseEntity<Map<String, Object>> getUserChannels(@PathVariable("userId") Integer userId) {
        Integer adminId = authUtil.getCurrentUserId();
        log.info("📋 [관리자 조회] 사용자 채널 목록 조회: adminId={}, targetUserId={}", adminId, userId);
        
        try {
            List<YoutubeChannelDto> channels = channelService.getChannelsByUserId(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("channels", channels);
            response.put("channelCount", channels != null ? channels.size() : 0);
            
            log.info("✅ [관리자 조회] 사용자 채널 목록 조회 완료: adminId={}, targetUserId={}, 채널수={}개", 
                adminId, userId, channels != null ? channels.size() : 0);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [관리자 조회] 사용자 채널 목록 조회 실패: adminId={}, targetUserId={}", adminId, userId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "채널 목록 조회 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 사용자의 필터 설정 조회 (사용자가 보는 화면과 동일)
     * GET /api/admin/users/{userId}/filter-preferences
     * 
     * @param userId 조회할 사용자 ID
     * @param channelId 채널 ID (선택적, null이면 전역 설정 조회)
     * @return 사용자의 필터 설정
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}/filter-preferences")
    public ResponseEntity<Map<String, Object>> getUserFilterPreferences(
            @PathVariable("userId") Integer userId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer channelId) {
        Integer adminId = authUtil.getCurrentUserId();
        log.info("📋 [관리자 조회] 사용자 필터 설정 조회: adminId={}, targetUserId={}, channelId={}", 
            adminId, userId, channelId);
        
        try {
            Optional<FilterPreferenceResponse> preference = filterPreferenceService.getPreference(userId, channelId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("channelId", channelId);
            
            if (preference.isPresent()) {
                response.put("preference", preference.get());
                response.put("hasPreference", true);
            } else {
                response.put("preference", null);
                response.put("hasPreference", false);
            }
            
            log.info("✅ [관리자 조회] 사용자 필터 설정 조회 완료: adminId={}, targetUserId={}, channelId={}, hasPreference={}", 
                adminId, userId, channelId, preference.isPresent());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [관리자 조회] 사용자 필터 설정 조회 실패: adminId={}, targetUserId={}, channelId={}", 
                adminId, userId, channelId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "필터 설정 조회 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 사용자 대시보드 조회 (사용자가 보는 화면의 모든 정보)
     * GET /api/admin/users/{userId}/dashboard
     * 
     * @param userId 조회할 사용자 ID
     * @return 사용자 대시보드 정보 (채널, 필터 설정 등)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}/dashboard")
    public ResponseEntity<Map<String, Object>> getUserDashboard(@PathVariable("userId") Integer userId) {
        Integer adminId = authUtil.getCurrentUserId();
        log.info("📋 [관리자 조회] 사용자 대시보드 조회: adminId={}, targetUserId={}", adminId, userId);
        
        try {
            // 1. 사용자 기본 정보
            UserDTO user = userMapper.findById(userId);
            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "사용자를 찾을 수 없습니다");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
            // 2. 채널 목록
            List<YoutubeChannelDto> channels = channelService.getChannelsByUserId(userId);
            
            // 3. 전역 필터 설정
            Optional<FilterPreferenceResponse> globalPreference = filterPreferenceService.getPreference(userId, null);
            
            // 4. 각 채널별 필터 설정 (선택적으로 추가 가능)
            Map<Integer, FilterPreferenceResponse> channelPreferences = new HashMap<>();
            if (channels != null) {
                for (YoutubeChannelDto channel : channels) {
                    Optional<FilterPreferenceResponse> channelPref = 
                        filterPreferenceService.getPreference(userId, channel.getId());
                    if (channelPref.isPresent()) {
                        channelPreferences.put(channel.getId(), channelPref.get());
                    }
                }
            }
            
            // 5. 응답 구성
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", Map.of(
                "id", user.getId(),
                "email", maskEmail(user.getEmail()), // 이메일 마스킹
                "name", user.getName(),
                "role", user.getRole()
            ));
            response.put("channels", channels);
            response.put("globalFilterPreference", globalPreference.orElse(null));
            response.put("channelFilterPreferences", channelPreferences);
            
            log.info("✅ [관리자 조회] 사용자 대시보드 조회 완료: adminId={}, targetUserId={}, 채널수={}개", 
                adminId, userId, channels != null ? channels.size() : 0);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [관리자 조회] 사용자 대시보드 조회 실패: adminId={}, targetUserId={}", adminId, userId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "대시보드 조회 중 오류가 발생했습니다");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 이메일 마스킹 처리 (개인정보보호)
     * 예: user@example.com → u***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email; // 마스킹할 부분이 없으면 그대로 반환
        }
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        // 첫 글자만 보이고 나머지는 *로 마스킹
        String maskedLocal = localPart.charAt(0) + "*".repeat(Math.max(0, localPart.length() - 1));
        
        return maskedLocal + domain;
    }
}

