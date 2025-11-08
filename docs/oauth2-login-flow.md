# Google OAuth2 로그인 흐름 (API & 응답 형식)

## 🔄 전체 흐름 다이어그램

```
사용자
  ↓
[1] Google 로그인 버튼 클릭
  ↓
[2] 백엔드로 리다이렉트 → Google 로그인 페이지로 이동
  ↓
[3] Google 계정 선택 및 권한 동의
  ↓
[4] Google → 백엔드 콜백 (인증 코드 전달)
  ↓
[5] 백엔드: 사용자 정보 조회 → DB 확인
  ↓
[6] 신규 사용자? → 자동 회원가입 → 로그인 처리
    기존 사용자? → 바로 로그인 처리
  ↓
[7] 세션 생성 → 프론트엔드로 리다이렉트
  ↓
[8] 프론트엔드: 사용자 정보 조회 API 호출
  ↓
[9] 대시보드 표시 (로그인 완료)
```

---

## 📡 단계별 API 호출 상세

### **[1단계] Google 로그인 버튼 클릭**

**프론트엔드 액션:**
```javascript
window.location.href = 'http://localhost:8080/oauth2/authorization/google';
```

**설명:** 사용자를 백엔드의 OAuth2 인증 시작 URL로 리다이렉트

---

### **[2단계] 백엔드 → Google 로그인 페이지 리다이렉트**

**자동 처리:** Spring Security가 자동으로 Google 로그인 페이지로 리다이렉트

**Google 로그인 URL (예시):**
```
https://accounts.google.com/o/oauth2/v2/auth?
  response_type=code
  &client_id=YOUR_CLIENT_ID
  &redirect_uri=http://localhost:8080/login/oauth2/code/google
  &scope=profile email
```

**사용자 액션:** Google 계정 선택 및 권한 동의

---

### **[3단계] Google → 백엔드 콜백**

**Google이 호출하는 URL:**
```
GET http://localhost:8080/login/oauth2/code/google?code=AUTH_CODE
```

**설명:** Google이 인증 코드를 백엔드로 전달

---

### **[4단계] 백엔드 내부 처리 (자동)**

#### 4-1. Google에게 액세스 토큰 요청 (자동)
```
POST https://oauth2.googleapis.com/token
Content-Type: application/x-www-form-urlencoded

code=AUTH_CODE
&client_id=YOUR_CLIENT_ID
&client_secret=YOUR_CLIENT_SECRET
&redirect_uri=http://localhost:8080/login/oauth2/code/google
&grant_type=authorization_code
```

**Google 응답:**
```json
{
  "access_token": "ya29.a0AfB_byC...",
  "expires_in": 3599,
  "scope": "openid https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email",
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

#### 4-2. Google에게 사용자 정보 요청 (자동)
```
GET https://www.googleapis.com/oauth2/v3/userinfo
Authorization: Bearer ya29.a0AfB_byC...
```

**Google 응답:**
```json
{
  "sub": "1234567890",
  "name": "홍길동",
  "given_name": "길동",
  "family_name": "홍",
  "picture": "https://lh3.googleusercontent.com/a/AAcHTtc...",
  "email": "hong@gmail.com",
  "email_verified": true,
  "locale": "ko"
}
```

#### 4-3. CustomOAuth2UserService 처리

**백엔드 내부 로직:**

**① Provider ID로 기존 사용자 확인**
```sql
SELECT * FROM users 
WHERE provider = 'GOOGLE' 
AND provider_id = '1234567890';
```

**② 결과에 따른 처리:**

**신규 사용자인 경우 (회원가입):**
```sql
INSERT INTO users (
  email, name, provider, provider_id, profile_image,
  password, phone, is_terms_agreed, role, created_at, updated_at
) VALUES (
  'hong@gmail.com',
  '홍길동',
  'GOOGLE',
  '1234567890',
  'https://lh3.googleusercontent.com/a/AAcHTtc...',
  NULL,  -- OAuth 사용자는 비밀번호 없음
  NULL,  -- OAuth 사용자는 전화번호 없음
  true,  -- 자동 약관 동의
  'USER',
  NOW(),
  NOW()
);
```

**기존 사용자인 경우 (로그인):**
- 사용자 정보 조회만 수행
- 추가 DB 작업 없음

#### 4-4. 세션 생성 및 리다이렉트

**세션에 저장되는 정보:**
```javascript
{
  "user": {
    "id": 1,
    "email": "hong@gmail.com",
    "name": "홍길동",
    "provider": "GOOGLE",
    "providerId": "1234567890",
    "profileImage": "https://lh3.googleusercontent.com/a/AAcHTtc...",
    "role": "USER"
  }
}
```

**프론트엔드로 리다이렉트:**
```
HTTP/1.1 302 Found
Location: http://localhost:3000/oauth2/callback
Set-Cookie: MEDI_SESSION=ABC123...; Path=/; HttpOnly; SameSite=Lax
```

---

### **[5단계] 프론트엔드: OAuth2 콜백 페이지**

**URL:** `http://localhost:3000/oauth2/callback`

**프론트엔드 액션:** 사용자 정보 조회 API 호출

**API 요청:**
```http
GET http://localhost:8080/api/auth/oauth2/user
Cookie: MEDI_SESSION=ABC123...
```

**백엔드 응답 (성공):**
```json
{
  "success": true,
  "user": {
    "id": 1,
    "email": "hong@gmail.com",
    "name": "홍길동",
    "provider": "GOOGLE",
    "providerId": "1234567890",
    "profileImage": "https://lh3.googleusercontent.com/a/AAcHTtc...",
    "role": "USER",
    "isTermsAgreed": true,
    "createdAt": "2025-11-04 15:30:00",
    "updatedAt": "2025-11-04 15:30:00"
  },
  "message": "사용자 정보 조회 성공"
}
```

**백엔드 응답 (실패 - 로그인 안됨):**
```json
{
  "success": false,
  "message": "로그인이 필요합니다."
}
```
HTTP Status: 401

---

### **[6단계] 프론트엔드: 대시보드로 이동**

**프론트엔드 액션:**
```javascript
// 사용자 정보를 state/localStorage에 저장
localStorage.setItem('user', JSON.stringify(data.user));

// 대시보드로 이동
navigate('/dashboard');
```

---

## 🔍 추가 API (로그인 후 사용)

### 1. 로그인 상태 확인

**API 요청:**
```http
GET http://localhost:8080/api/auth/oauth2/status
Cookie: MEDI_SESSION=ABC123...
```

**응답 (로그인됨):**
```json
{
  "isLoggedIn": true,
  "provider": "GOOGLE",
  "email": "hong@gmail.com",
  "name": "홍길동"
}
```

**응답 (로그인 안됨):**
```json
{
  "isLoggedIn": false
}
```

---

### 2. 로그아웃

**API 요청:**
```http
POST http://localhost:8080/api/auth/oauth2/logout
Cookie: MEDI_SESSION=ABC123...
```

**응답:**
```json
{
  "success": true,
  "message": "로그아웃 성공"
}
```

**프론트엔드 액션:**
```javascript
// 로컬 스토리지 삭제
localStorage.removeItem('user');

// 로그인 페이지로 이동
navigate('/');
```

---

## 🎯 프론트엔드 간단 테스트 코드

### 최소 테스트 HTML

```html
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <title>OAuth2 테스트</title>
</head>
<body>
  <h1>Google OAuth2 로그인 테스트</h1>
  
  <!-- 로그인 버튼 -->
  <button onclick="login()">Google 로그인</button>
  
  <!-- 상태 확인 버튼 -->
  <button onclick="checkStatus()">로그인 상태 확인</button>
  
  <!-- 사용자 정보 조회 버튼 -->
  <button onclick="getUserInfo()">사용자 정보 조회</button>
  
  <!-- 로그아웃 버튼 -->
  <button onclick="logout()">로그아웃</button>
  
  <pre id="result"></pre>

  <script>
    const API_URL = 'http://localhost:8080';
    const resultEl = document.getElementById('result');

    // 1. Google 로그인
    function login() {
      window.location.href = `${API_URL}/oauth2/authorization/google`;
    }

    // 2. 로그인 상태 확인
    async function checkStatus() {
      try {
        const response = await fetch(`${API_URL}/api/auth/oauth2/status`, {
          credentials: 'include'
        });
        const data = await response.json();
        resultEl.textContent = JSON.stringify(data, null, 2);
      } catch (error) {
        resultEl.textContent = `오류: ${error.message}`;
      }
    }

    // 3. 사용자 정보 조회
    async function getUserInfo() {
      try {
        const response = await fetch(`${API_URL}/api/auth/oauth2/user`, {
          credentials: 'include'
        });
        const data = await response.json();
        resultEl.textContent = JSON.stringify(data, null, 2);
      } catch (error) {
        resultEl.textContent = `오류: ${error.message}`;
      }
    }

    // 4. 로그아웃
    async function logout() {
      try {
        const response = await fetch(`${API_URL}/api/auth/oauth2/logout`, {
          method: 'POST',
          credentials: 'include'
        });
        const data = await response.json();
        resultEl.textContent = JSON.stringify(data, null, 2);
      } catch (error) {
        resultEl.textContent = `오류: ${error.message}`;
      }
    }

    // 페이지 로드 시 상태 확인
    window.onload = checkStatus;
  </script>
</body>
</html>
```

**사용 방법:**
1. 위 코드를 `test-oauth2.html` 파일로 저장
2. 브라우저에서 파일 열기: `file:///C:/path/to/test-oauth2.html`
3. 버튼 클릭해서 테스트

---

## 📊 데이터베이스 변화

### 신규 사용자 회원가입 시

**로그인 전 (users 테이블):**
```
(비어있음)
```

**로그인 후 (users 테이블):**
```
id | email            | name   | provider | provider_id | profile_image              | password | phone | is_terms_agreed | role | created_at          | updated_at
---|------------------|--------|----------|-------------|----------------------------|----------|-------|-----------------|------|---------------------|--------------------
1  | hong@gmail.com   | 홍길동 | GOOGLE   | 1234567890  | https://lh3.google...      | NULL     | NULL  | 1               | USER | 2025-11-04 15:30:00 | 2025-11-04 15:30:00
```

### 기존 사용자 로그인 시

**변화 없음** - 기존 데이터 그대로 유지

---

## ⚡ 빠른 테스트 순서

### 1. 백엔드 실행
```bash
./gradlew bootRun
```

### 2. 브라우저에서 테스트

**방법 1: 직접 URL 접속**
```
http://localhost:8080/oauth2/authorization/google
```
→ Google 로그인 → 성공 시 프론트엔드로 리다이렉트

**방법 2: HTML 파일 사용**
- 위의 `test-oauth2.html` 파일 사용
- 버튼으로 각 API 테스트

**방법 3: Postman/Thunder Client**
```http
GET http://localhost:8080/api/auth/oauth2/status
```
(단, 브라우저로 먼저 로그인 후 쿠키 복사 필요)

---

## 🔒 인증 상태 확인 방법

### 브라우저 개발자 도구

**Application → Cookies → http://localhost:8080**

확인할 쿠키:
- `MEDI_SESSION`: 세션 ID
- `HttpOnly`: true (보안)
- `SameSite`: Lax

세션 쿠키가 있으면 → 로그인됨  
세션 쿠키가 없으면 → 로그인 안됨

---

## 🎯 테스트 시나리오

### 시나리오 1: 신규 사용자 회원가입 + 로그인
1. Google 로그인 버튼 클릭
2. Google 계정 선택 (처음 사용하는 이메일)
3. 권한 동의
4. 자동으로 회원가입됨 → 즉시 로그인
5. 사용자 정보 조회 API 호출 → 성공

**예상 결과:**
- DB에 새 사용자 추가됨
- 세션 생성됨
- 프론트엔드에서 사용자 정보 표시

### 시나리오 2: 기존 사용자 로그인
1. Google 로그인 버튼 클릭
2. Google 계정 선택 (이미 가입된 이메일)
3. 즉시 로그인
4. 사용자 정보 조회 API 호출 → 성공

**예상 결과:**
- DB 변화 없음 (기존 데이터 유지)
- 세션 생성됨
- 프론트엔드에서 사용자 정보 표시

### 시나리오 3: 로그아웃
1. 로그아웃 API 호출
2. 세션 삭제됨
3. 로그인 페이지로 이동

**예상 결과:**
- 세션 쿠키 삭제됨
- 사용자 정보 조회 시 401 에러

---

## 🐛 오류 상황 및 응답

### 1. 로그인하지 않고 사용자 정보 조회
```http
GET http://localhost:8080/api/auth/oauth2/user
(쿠키 없음)
```

**응답:**
```json
{
  "success": false,
  "message": "로그인이 필요합니다."
}
```
HTTP Status: 401

### 2. 이미 일반 회원가입한 이메일로 Google 로그인 시도
```json
{
  "error": "이미 가입된 이메일입니다. 일반 로그인을 이용해주세요."
}
```
(백엔드에서 예외 처리됨)

### 3. Google OAuth2 설정 오류
- Google Cloud Console 설정 확인
- 클라이언트 ID/Secret 확인
- 리다이렉트 URI 확인

---

## 📋 요약

### 주요 API 엔드포인트

| 순서 | API | 설명 |
|------|-----|------|
| 1 | `GET /oauth2/authorization/google` | 로그인 시작 |
| 2 | `GET /login/oauth2/code/google` | 콜백 (자동) |
| 3 | `GET /api/auth/oauth2/user` | 사용자 정보 조회 |
| 4 | `GET /api/auth/oauth2/status` | 로그인 상태 확인 |
| 5 | `POST /api/auth/oauth2/logout` | 로그아웃 |

### 인증 흐름 핵심

1. **Google 로그인** → 인증 코드 받기
2. **백엔드 자동 처리** → 액세스 토큰 받기 → 사용자 정보 받기
3. **DB 확인** → 신규면 회원가입, 기존이면 로그인
4. **세션 생성** → 프론트엔드로 리다이렉트
5. **프론트엔드** → 사용자 정보 조회 → 대시보드 표시

---

**작성일**: 2025-11-04
**버전**: 1.0.0

