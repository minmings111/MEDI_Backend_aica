# Google OAuth2 회원가입/로그인 구현 계획

## 구현 개요
**기존 테이블 구조를 그대로 활용**하여 OAuth2 회원가입과 로그인 기능만 구현합니다. 기존 계정 연동은 제외하고, Google 계정을 통한 신규 회원가입과 로그인에만 집중합니다.

## 기존 테이블 구조 분석 ✅

### users 테이블 (OAuth 회원가입/로그인용) - 이미 완벽함
```sql
-- OAuth 관련 필드들이 이미 존재
provider VARCHAR(20) DEFAULT 'LOCAL'           -- 로그인 방식 구분 (LOCAL/GOOGLE)
provider_id VARCHAR(255) NULL                  -- Google sub ID
profile_image VARCHAR(2048) NULL               -- Google 프로필 이미지
password VARCHAR(255) NULL                     -- OAuth 사용자는 NULL 가능
phone VARCHAR(20) NULL                         -- OAuth 사용자는 NULL 가능
```

## 파일 구조 (OAuth2 회원가입/로그인 전용)
```
src/main/java/com/medi/backend/
├── auth/
│   ├── controller/
│   │   ├── AuthController.java              # 기존 일반 로그인 (유지)
│   │   └── OAuth2AuthController.java        # 🆕 OAuth2 회원가입/로그인 전용
│   ├── service/
│   │   ├── AuthService.java                # 기존 인증 서비스 (유지)
│   │   └── OAuth2AuthService.java          # 🆕 OAuth2 회원가입/로그인 로직
│   └── dto/
│       └── OAuth2UserInfo.java             # 🆕 OAuth2 사용자 정보
├── global/security/
│   ├── service/
│   │   └── CustomOAuth2UserService.java    # 🆕 Spring Security OAuth2 연동
│   └── handler/
│       ├── OAuth2LoginSuccessHandler.java  # 🆕 로그인 성공 처리
│       └── OAuth2LoginFailureHandler.java  # 🆕 로그인 실패 처리
└── user/
    ├── dto/UserDTO.java                    # OAuth 필드 추가 (기존 테이블 매핑)
    └── mapper/UserMapper.java              # OAuth 메서드 추가
```

## 주요 구현사항

### 1. 의존성 및 설정
- `build.gradle`에 Spring Security OAuth2 Client 추가
- `application.yml`에 Google OAuth2 설정:
  ```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            google:
              client-id: ${GOOGLE_CLIENT_ID}
              client-secret: ${GOOGLE_CLIENT_SECRET}
              scope: profile, email
              redirect-uri: http://localhost:8080/login/oauth2/code/google
  ```

### 2. 데이터베이스 매핑 (기존 테이블 활용)
- `UserDTO`에 기존 OAuth 필드 매핑:
  - `provider` → `provider`
  - `providerId` → `provider_id`  
  - `profileImage` → `profile_image`
- `UserMapper`에 OAuth 관련 쿼리 추가

### 3. OAuth2 컨트롤러
- `OAuth2AuthController`: OAuth2 회원가입/로그인 전용 API
  - `GET /api/auth/oauth2/google/url`: Google 로그인 URL 제공
  - `GET /api/auth/oauth2/user`: OAuth2 사용자 정보 조회
  - `GET /api/auth/oauth2/status`: OAuth2 로그인 상태 확인

### 4. OAuth2 서비스 레이어
- `OAuth2AuthService`: OAuth2 회원가입/로그인 비즈니스 로직
  - Google 사용자 정보 처리
  - **신규 사용자 자동 회원가입** (`users` 테이블에 저장)
  - **기존 Google 사용자 로그인** (Provider ID 기반)

### 5. Spring Security 통합
- `CustomOAuth2UserService`: OAuth2 사용자 정보 로드 및 처리
- `OAuth2LoginSuccessHandler`: 로그인 성공 시 세션 설정
- `OAuth2LoginFailureHandler`: 로그인 실패 시 에러 처리
- `SecurityConfig` 업데이트: OAuth2 로그인 설정 추가

### 6. 의존성 주입 구조
```java
OAuth2AuthController
├── OAuth2AuthService (회원가입/로그인 비즈니스 로직)
├── UserMapper (기존 users 테이블 활용)
└── AuthService (기존 서비스 재사용)

OAuth2AuthService
├── UserMapper (users 테이블 CRUD)
└── PasswordEncoder (필요시)

CustomOAuth2UserService
└── OAuth2AuthService (사용자 처리 위임)
```

## OAuth2 회원가입/로그인 플로우
1. 프론트엔드에서 `/api/auth/oauth2/google/url` 호출
2. Google 로그인 URL로 리다이렉트 (scope: profile, email)
3. 사용자가 Google에서 로그인 인증
4. Google이 `/login/oauth2/code/google`로 콜백
5. `CustomOAuth2UserService`에서 사용자 정보 처리:
   - **Google Provider ID로 기존 사용자 확인**
   - **기존 Google 사용자**: 로그인 처리
   - **신규 사용자**: 자동 회원가입 후 로그인 처리
6. `OAuth2LoginSuccessHandler`에서 세션 설정
7. 프론트엔드 대시보드로 리다이렉트

## 사용자 처리 로직 (OAuth 회원가입/로그인만)
```java
// Google Provider ID로 기존 사용자 확인
UserDTO existingUser = userMapper.findByProviderAndProviderId("GOOGLE", googleSub);

if (existingUser != null) {
    // 🔑 기존 Google 사용자 → 로그인
    log.info("기존 Google 사용자 로그인: {}", existingUser.getEmail());
    return existingUser;
    
} else {
    // 🆕 신규 Google 사용자 → 자동 회원가입
    UserDTO newUser = new UserDTO();
    newUser.setEmail(googleEmail);
    newUser.setName(googleName);
    newUser.setProvider("GOOGLE");
    newUser.setProviderId(googleSub);
    newUser.setProfileImage(googlePicture);
    newUser.setPassword(null);          // OAuth 사용자는 비밀번호 없음
    newUser.setPhone(null);             // OAuth 사용자는 전화번호 없음
    newUser.setIsTermsAgreed(true);     // OAuth 로그인은 자동 약관 동의
    newUser.setRole("USER");
    
    userMapper.insertUser(newUser);     // 자동 회원가입
    log.info("Google OAuth 회원가입 완료: {}", newUser.getEmail());
    return newUser;
}
```

## 기술적 고려사항
- **데이터베이스 변경 없음**: 기존 테이블 구조 그대로 활용
- **기존 시스템과 독립**: 일반 로그인과 OAuth2 로그인 완전 분리
- **확장성**: 향후 `youtube_oauth_tokens` 테이블로 권한 위임 기능 추가 가능
- **최소 권한**: 로그인용으로만 profile, email scope 사용
- **세션 통합**: 기존 세션 기반 인증과 동일한 방식
- **자동 회원가입**: Google 로그인 시 신규 사용자 자동 생성
- **중복 방지**: Provider ID 기반으로 중복 계정 방지

## 구현 후 지원되는 기능
1. **일반 회원가입/로그인** (이메일 + 비밀번호) - 기존 유지
2. **Google OAuth2 회원가입** (Google 계정으로 신규 가입) - 신규 추가
3. **Google OAuth2 로그인** (기존 Google 사용자 로그인) - 신규 추가
4. **통합 사용자 관리** (하나의 users 테이블에서 관리)

### 사용자 시나리오
- **신규 사용자**: Google 로그인 → 자동 회원가입 → 즉시 로그인 완료
- **기존 Google 사용자**: Google 로그인 → 즉시 로그인 완료
- **기존 일반 사용자**: 기존 방식대로 이메일/비밀번호 로그인 유지

### 제외되는 기능
- ❌ 기존 계정과 Google 계정 연동 (구현하지 않음)
- ❌ 이메일 기반 계정 매칭 (구현하지 않음)
- ❌ 권한 위임 기능 (현재 단계에서는 제외)

## 구현 체크리스트

### 1단계: 의존성 및 설정
- [ ] `build.gradle`에 OAuth2 의존성 추가
- [ ] `application.yml`에 OAuth2 설정 추가

### 2단계: 데이터 모델 확장
- [ ] `UserDTO`에 기존 OAuth 필드 매핑
- [ ] `UserMapper`에 OAuth 관련 쿼리 추가

### 3단계: DTO 및 서비스 구현
- [ ] `OAuth2UserInfo` DTO 클래스 생성
- [ ] `OAuth2AuthService` 회원가입/로그인 비즈니스 로직 구현

### 4단계: 컨트롤러 구현
- [ ] `OAuth2AuthController` 회원가입/로그인 API 엔드포인트 구현

### 5단계: Spring Security 연동
- [ ] `CustomOAuth2UserService` Spring Security 연동 구현
- [ ] OAuth2 로그인 성공/실패 핸들러 구현

### 6단계: Security 설정
- [ ] `SecurityConfig`에 OAuth2 로그인 설정 추가

### 7단계: 테스트
- [ ] OAuth2 회원가입/로그인 플로우 테스트 및 검증

## 참고 사항
- Google OAuth2 클라이언트 ID/Secret은 환경변수로 관리
- 리다이렉트 URI는 Google Cloud Console에서 설정 필요
- 개발 환경에서는 `http://localhost:8080/login/oauth2/code/google` 사용
- 배포 환경에서는 실제 도메인으로 변경 필요
