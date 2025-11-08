# 수정된 프론트엔드 코드 (OAuth2 통합)

## 📁 파일 구조

```
src/
├── pages/
│   ├── Login.jsx           # 로그인 페이지 (수정됨)
│   ├── OAuth2Callback.jsx  # OAuth2 콜백 페이지 (새로 추가)
│   └── Dashboard.jsx       # 대시보드 (참고)
├── App.jsx                 # 라우터 설정 (수정 필요)
└── Login.css              # 스타일
```

---

## 1. `src/pages/Login.jsx` (수정됨)

```jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './Login.css';

function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  // 페이지 로드 시 로그인 상태 확인
  useEffect(() => {
    checkLoginStatus();
  }, []);

  // ✅ 수정: OAuth2 통합 로그인 상태 확인
  const checkLoginStatus = async () => {
    try {
      // OAuth2와 일반 로그인 모두 지원하는 엔드포인트
      const response = await fetch('http://localhost:8080/api/auth/oauth2/status', {
        credentials: 'include',
      });
      
      const data = await response.json();
      
      // isLoggedIn 필드로 확인
      if (data.isLoggedIn) {
        // 이미 로그인됨 → 대시보드로 이동
        console.log('이미 로그인됨:', data);
        navigate('/dashboard');
      }
    } catch (error) {
      console.error('로그인 상태 확인 실패:', error);
    }
  };

  // 폼 검증
  const validateForm = () => {
    if (!email) {
      setError('이메일을 입력해주세요');
      return false;
    }
    
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(email)) {
      setError('올바른 이메일 형식이 아닙니다');
      return false;
    }
    
    if (!password) {
      setError('비밀번호를 입력해주세요');
      return false;
    }
    
    if (password.length < 8) {
      setError('비밀번호는 최소 8자 이상이어야 합니다');
      return false;
    }
    
    return true;
  };

  // 일반 로그인
  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    
    if (!validateForm()) {
      return;
    }
    
    setLoading(true);
    
    try {
      const response = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          email: email,
          password: password,
        }),
      });
      
      const result = await response.json();
      
      if (response.ok && result.success) {
        console.log('일반 로그인 성공');
        navigate('/dashboard');
      } else {
        setError(result.message || '이메일 또는 비밀번호가 올바르지 않습니다');
      }
    } catch (err) {
      setError('서버와의 통신 중 오류가 발생했습니다');
      console.error('Login error:', err);
    } finally {
      setLoading(false);
    }
  };

  // ✅ Google OAuth2 로그인 (정상 작동)
  const handleGoogleLogin = () => {
    // Spring Security가 자동으로 Google 로그인 페이지로 리다이렉트
    window.location.href = 'http://localhost:8080/oauth2/authorization/google';
  };

  return (
    <div className="login-container">
      <div className="login-box">
        <h2 className="login-title">로그인</h2>
        
        {/* 일반 로그인 폼 */}
        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="email">이메일</label>
            <input
              type="email"
              id="email"
              className="form-input"
              placeholder="example@email.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              autoFocus
            />
          </div>
          
          <div className="form-group">
            <label htmlFor="password">비밀번호</label>
            <input
              type="password"
              id="password"
              className="form-input"
              placeholder="비밀번호를 입력하세요"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
          </div>
          
          {error && <div className="error-message">{error}</div>}
          
          <button 
            type="submit" 
            className="login-button"
            disabled={loading}
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className="separator">또는</div>

        {/* ✅ Google OAuth2 로그인 버튼 */}
        <button 
          onClick={handleGoogleLogin} 
          className="google-login-button"
          type="button"
          disabled={loading}
        >
          <svg width="20" height="20" viewBox="0 0 20 20" style={{ marginRight: '10px' }}>
            <path fill="#4285F4" d="M19.6 10.2c0-.7-.1-1.4-.2-2H10v3.8h5.4c-.2 1.2-1 2.2-2 2.9v2.5h3.2c1.9-1.7 3-4.3 3-7.2z"/>
            <path fill="#34A853" d="M10 20c2.7 0 4.9-.9 6.6-2.4l-3.2-2.5c-.9.6-2 .9-3.4.9-2.6 0-4.8-1.8-5.6-4.1H1.1v2.6C2.8 17.4 6.2 20 10 20z"/>
            <path fill="#FBBC05" d="M4.4 12c-.2-.6-.3-1.3-.3-2s.1-1.4.3-2V5.4H1.1C.4 6.8 0 8.4 0 10s.4 3.2 1.1 4.6l3.3-2.6z"/>
            <path fill="#EA4335" d="M10 4c1.5 0 2.8.5 3.8 1.5l2.9-2.9C15 1 12.7 0 10 0 6.2 0 2.8 2.6 1.1 6.4l3.3 2.6C5.2 5.8 7.4 4 10 4z"/>
          </svg>
          Google로 로그인
        </button>
        
        <div className="login-footer">
          <a href="/forgot-password" className="link">비밀번호를 잊으셨나요?</a>
          <p className="signup-link">
            계정이 없으신가요? <a href="/signup">회원가입</a>
          </p>
        </div>
      </div>
    </div>
  );
}

export default Login;
```

---

## 2. `src/pages/OAuth2Callback.jsx` (⭐ 새로 추가 필수)

```jsx
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

function OAuth2Callback() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('Google 로그인 처리 중...');
  const [error, setError] = useState(null);

  useEffect(() => {
    // Google 로그인 후 사용자 정보 가져오기
    const fetchUserInfo = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/auth/oauth2/user', {
          credentials: 'include'
        });

        if (!response.ok) {
          throw new Error('사용자 정보를 가져올 수 없습니다.');
        }

        const data = await response.json();
        
        if (data.success) {
          console.log('OAuth2 로그인 성공:', data.user);
          setStatus('✅ 로그인 성공! 대시보드로 이동합니다...');
          
          // 선택: localStorage에 사용자 정보 저장
          localStorage.setItem('user', JSON.stringify(data.user));
          
          // 1초 후 대시보드로 이동
          setTimeout(() => {
            navigate('/dashboard');
          }, 1000);
        } else {
          throw new Error(data.message || '로그인 실패');
        }
        
      } catch (err) {
        console.error('OAuth2 콜백 오류:', err);
        setError(err.message);
        setStatus('❌ 로그인에 실패했습니다.');
        
        // 3초 후 로그인 페이지로 이동
        setTimeout(() => {
          navigate('/login');
        }, 3000);
      }
    };

    fetchUserInfo();
  }, [navigate]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: '#f5f5f5'
    }}>
      <div style={{
        backgroundColor: 'white',
        padding: '40px',
        borderRadius: '12px',
        boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
        textAlign: 'center',
        maxWidth: '400px'
      }}>
        {!error && (
          <div style={{
            border: '4px solid #f3f3f3',
            borderTop: '4px solid #4285F4',
            borderRadius: '50%',
            width: '50px',
            height: '50px',
            animation: 'spin 1s linear infinite',
            margin: '0 auto 20px'
          }}></div>
        )}
        
        <h2 style={{ 
          color: error ? '#dc3545' : '#333', 
          marginBottom: '10px',
          fontSize: '18px'
        }}>
          {status}
        </h2>
        
        {error && (
          <p style={{ 
            color: '#666', 
            fontSize: '14px',
            marginTop: '10px'
          }}>
            {error}
          </p>
        )}
      </div>
      
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

export default OAuth2Callback;
```

---

## 3. `src/pages/Dashboard.jsx` (참고용)

```jsx
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

function Dashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/auth/oauth2/status', {
        credentials: 'include'
      });
      
      const data = await response.json();
      
      if (data.isLoggedIn) {
        setUser(data);
      } else {
        navigate('/login');
      }
    } catch (error) {
      console.error('인증 확인 오류:', error);
      navigate('/login');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await fetch('http://localhost:8080/api/auth/oauth2/logout', {
        method: 'POST',
        credentials: 'include'
      });
      
      localStorage.removeItem('user');
      navigate('/login');
    } catch (error) {
      console.error('로그아웃 오류:', error);
    }
  };

  if (loading) {
    return <div style={{ padding: '40px', textAlign: 'center' }}>로딩 중...</div>;
  }

  return (
    <div style={{ padding: '40px', maxWidth: '800px', margin: '0 auto' }}>
      <div style={{
        backgroundColor: 'white',
        padding: '30px',
        borderRadius: '12px',
        boxShadow: '0 2px 10px rgba(0,0,0,0.1)'
      }}>
        <h1>대시보드</h1>
        
        {user && (
          <div style={{
            backgroundColor: '#f8f9fa',
            padding: '20px',
            borderRadius: '8px',
            marginTop: '20px',
            marginBottom: '20px'
          }}>
            <h3>사용자 정보</h3>
            <table style={{ width: '100%', marginTop: '15px' }}>
              <tbody>
                <tr>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>이름:</td>
                  <td style={{ padding: '10px' }}>{user.name}</td>
                </tr>
                <tr>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>이메일:</td>
                  <td style={{ padding: '10px' }}>{user.email}</td>
                </tr>
                <tr>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>로그인 방식:</td>
                  <td style={{ padding: '10px' }}>
                    <span style={{
                      backgroundColor: user.provider === 'GOOGLE' ? '#4285F4' : '#28a745',
                      color: 'white',
                      padding: '4px 12px',
                      borderRadius: '4px',
                      fontSize: '14px'
                    }}>
                      {user.provider}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
        
        <button
          onClick={handleLogout}
          style={{
            padding: '12px 24px',
            backgroundColor: '#dc3545',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '16px',
            cursor: 'pointer'
          }}
        >
          로그아웃
        </button>
      </div>
    </div>
  );
}

export default Dashboard;
```

---

## 4. `src/App.jsx` (라우터 설정 - ⭐ 중요)

```jsx
import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import OAuth2Callback from './pages/OAuth2Callback';  // ⭐ 추가
import Dashboard from './pages/Dashboard';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/oauth2/callback" element={<OAuth2Callback />} />  {/* ⭐ 필수 */}
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/" element={<Navigate to="/login" />} />
        <Route path="*" element={<Navigate to="/login" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

---

## 5. 백엔드 설정 확인

### `application.yml` - CORS 설정

```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173
```

프론트엔드가 3000번 포트면 3000, 5173번이면 5173 포함되어야 합니다.

---

## 📊 API 엔드포인트 정리

| API | 용도 | 일반 로그인 | OAuth2 로그인 |
|-----|------|------------|---------------|
| `/api/auth/login` | 일반 로그인 | ✅ | ❌ |
| `/oauth2/authorization/google` | OAuth2 시작 | ❌ | ✅ |
| `/api/auth/oauth2/user` | 사용자 정보 조회 | ✅ | ✅ |
| `/api/auth/oauth2/status` | 로그인 상태 확인 | ✅ | ✅ |
| `/api/auth/oauth2/logout` | 로그아웃 | ✅ | ✅ |

---

## 🔄 OAuth2 로그인 흐름

```
1. 사용자가 "Google로 로그인" 버튼 클릭
   ↓
2. window.location.href = '/oauth2/authorization/google'
   ↓
3. 백엔드가 Google 로그인 페이지로 리다이렉트
   ↓
4. 사용자가 Google 계정 선택 및 권한 동의
   ↓
5. Google이 백엔드로 인증 코드 전달
   ↓
6. 백엔드가 사용자 정보 처리 (회원가입 or 로그인)
   ↓
7. 백엔드가 프론트엔드로 리다이렉트: http://localhost:3000/oauth2/callback
   ↓
8. OAuth2Callback 컴포넌트가 사용자 정보 조회 API 호출
   ↓
9. 성공 시 대시보드로 이동
```

---

## ✅ 체크리스트

### 프론트엔드
- [ ] `OAuth2Callback.jsx` 파일 생성
- [ ] `App.jsx`에 `/oauth2/callback` 라우트 추가
- [ ] `Login.jsx`의 API 엔드포인트 수정 (`/api/auth/oauth2/status`)
- [ ] 로그인 페이지에서 로그아웃 버튼 제거

### 백엔드
- [ ] `application.yml`에 프론트엔드 URL 추가 (CORS)
- [ ] `OAuth2AuthenticationSuccessHandler`에서 리다이렉트 URL 확인:
  ```java
  String redirectUrl = frontendUrl + "/oauth2/callback";
  ```

### Google Cloud Console
- [ ] 리다이렉트 URI: `http://localhost:8080/login/oauth2/code/google`

---

## 🎯 테스트 순서

1. 백엔드 실행: `./gradlew bootRun`
2. 프론트엔드 실행: `npm start`
3. 브라우저에서 `http://localhost:3000/login` 접속
4. "Google로 로그인" 버튼 클릭
5. Google 계정 선택
6. 자동으로 `/oauth2/callback` → `/dashboard`로 이동

---

**작성일**: 2025-11-04  
**버전**: 1.0.0



