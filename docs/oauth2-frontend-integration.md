# OAuth2 프론트엔드 연동 가이드

## 📋 개요

백엔드 OAuth2 구현이 완료되었으므로, React/Vue/Angular 등 프론트엔드에서 Google OAuth2 로그인을 연동하는 방법을 안내합니다.

---

## 🔧 React 연동 예제

### 1. 로그인 버튼 컴포넌트

```jsx
// components/GoogleLoginButton.jsx
import React from 'react';
import { FcGoogle } from 'react-icons/fc';

const GoogleLoginButton = () => {
  const handleGoogleLogin = async () => {
    try {
      // 백엔드에서 Google 로그인 URL 가져오기
      const response = await fetch('http://localhost:8080/api/auth/oauth2/google/url', {
        credentials: 'include' // 쿠키 포함
      });
      
      const data = await response.json();
      
      // Google 로그인 페이지로 리다이렉트
      window.location.href = `http://localhost:8080${data.url}`;
      
    } catch (error) {
      console.error('Google 로그인 오류:', error);
      alert('로그인에 실패했습니다. 다시 시도해주세요.');
    }
  };

  return (
    <button
      onClick={handleGoogleLogin}
      className="google-login-btn"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        padding: '12px 24px',
        border: '1px solid #dadce0',
        borderRadius: '4px',
        backgroundColor: '#fff',
        cursor: 'pointer',
        fontSize: '14px',
        fontWeight: '500',
        transition: 'background-color 0.2s'
      }}
      onMouseOver={(e) => e.currentTarget.style.backgroundColor = '#f8f9fa'}
      onMouseOut={(e) => e.currentTarget.style.backgroundColor = '#fff'}
    >
      <FcGoogle size={20} />
      Google로 로그인
    </button>
  );
};

export default GoogleLoginButton;
```

### 2. OAuth2 콜백 페이지

```jsx
// pages/OAuth2Callback.jsx
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const OAuth2Callback = () => {
  const navigate = useNavigate();
  const [status, setStatus] = useState('로그인 처리 중...');

  useEffect(() => {
    const fetchUserInfo = async () => {
      try {
        // 백엔드에서 사용자 정보 가져오기
        const response = await fetch('http://localhost:8080/api/auth/oauth2/user', {
          credentials: 'include' // 쿠키 포함
        });

        if (!response.ok) {
          throw new Error('로그인 실패');
        }

        const data = await response.json();
        
        if (data.success) {
          // 로컬 스토리지에 사용자 정보 저장 (선택사항)
          localStorage.setItem('user', JSON.stringify(data.user));
          
          setStatus('로그인 성공! 리다이렉트 중...');
          
          // 대시보드로 이동
          setTimeout(() => {
            navigate('/dashboard');
          }, 1000);
        } else {
          throw new Error(data.message || '로그인 실패');
        }
        
      } catch (error) {
        console.error('사용자 정보 조회 오류:', error);
        setStatus('로그인에 실패했습니다.');
        
        setTimeout(() => {
          navigate('/login');
        }, 2000);
      }
    };

    fetchUserInfo();
  }, [navigate]);

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      flexDirection: 'column',
      gap: '20px'
    }}>
      <div className="spinner"></div>
      <p>{status}</p>
    </div>
  );
};

export default OAuth2Callback;
```

### 3. OAuth2 에러 페이지

```jsx
// pages/OAuth2Error.jsx
import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

const OAuth2Error = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const errorMessage = searchParams.get('error') || '알 수 없는 오류가 발생했습니다.';

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      flexDirection: 'column',
      gap: '20px'
    }}>
      <h2>로그인 실패</h2>
      <p>{errorMessage}</p>
      <button 
        onClick={() => navigate('/login')}
        style={{
          padding: '10px 20px',
          backgroundColor: '#1a73e8',
          color: '#fff',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
      >
        다시 시도
      </button>
    </div>
  );
};

export default OAuth2Error;
```

### 4. 라우팅 설정

```jsx
// App.jsx
import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import OAuth2Callback from './pages/OAuth2Callback';
import OAuth2Error from './pages/OAuth2Error';
import Dashboard from './pages/Dashboard';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/oauth2/callback" element={<OAuth2Callback />} />
        <Route path="/oauth2/error" element={<OAuth2Error />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

### 5. 로그인 페이지

```jsx
// pages/LoginPage.jsx
import React from 'react';
import GoogleLoginButton from '../components/GoogleLoginButton';

const LoginPage = () => {
  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      flexDirection: 'column',
      gap: '20px'
    }}>
      <h1>Medi 로그인</h1>
      
      {/* 일반 로그인 폼 */}
      <form style={{ width: '300px' }}>
        <input 
          type="email" 
          placeholder="이메일"
          style={{ width: '100%', padding: '10px', marginBottom: '10px' }}
        />
        <input 
          type="password" 
          placeholder="비밀번호"
          style={{ width: '100%', padding: '10px', marginBottom: '10px' }}
        />
        <button 
          type="submit"
          style={{ width: '100%', padding: '10px', backgroundColor: '#1a73e8', color: '#fff', border: 'none' }}
        >
          로그인
        </button>
      </form>
      
      {/* 구분선 */}
      <div style={{ display: 'flex', alignItems: 'center', width: '300px' }}>
        <hr style={{ flex: 1 }} />
        <span style={{ padding: '0 10px', color: '#888' }}>또는</span>
        <hr style={{ flex: 1 }} />
      </div>
      
      {/* Google 로그인 */}
      <GoogleLoginButton />
    </div>
  );
};

export default LoginPage;
```

### 6. 사용자 정보 훅 (Custom Hook)

```jsx
// hooks/useAuth.js
import { useState, useEffect } from 'react';

export const useAuth = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/auth/oauth2/status', {
        credentials: 'include'
      });

      const data = await response.json();

      if (data.isLoggedIn) {
        setUser({
          email: data.email,
          name: data.name,
          provider: data.provider
        });
      } else {
        setUser(null);
      }
    } catch (error) {
      console.error('인증 상태 확인 오류:', error);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  const logout = async () => {
    try {
      await fetch('http://localhost:8080/api/auth/oauth2/logout', {
        method: 'POST',
        credentials: 'include'
      });

      localStorage.removeItem('user');
      setUser(null);
      window.location.href = '/login';
      
    } catch (error) {
      console.error('로그아웃 오류:', error);
    }
  };

  return { user, loading, logout, checkAuthStatus };
};
```

### 7. 대시보드에서 사용 예제

```jsx
// pages/Dashboard.jsx
import React from 'react';
import { useAuth } from '../hooks/useAuth';
import { Navigate } from 'react-router-dom';

const Dashboard = () => {
  const { user, loading, logout } = useAuth();

  if (loading) {
    return <div>로딩 중...</div>;
  }

  if (!user) {
    return <Navigate to="/login" />;
  }

  return (
    <div style={{ padding: '20px' }}>
      <h1>대시보드</h1>
      
      <div style={{ 
        padding: '20px', 
        border: '1px solid #ddd', 
        borderRadius: '8px',
        marginBottom: '20px'
      }}>
        <h3>사용자 정보</h3>
        <p><strong>이름:</strong> {user.name}</p>
        <p><strong>이메일:</strong> {user.email}</p>
        <p><strong>로그인 방식:</strong> {user.provider}</p>
      </div>
      
      <button 
        onClick={logout}
        style={{
          padding: '10px 20px',
          backgroundColor: '#dc3545',
          color: '#fff',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer'
        }}
      >
        로그아웃
      </button>
    </div>
  );
};

export default Dashboard;
```

---

## 🔧 Vue.js 연동 예제

### 1. Google 로그인 컴포넌트

```vue
<!-- components/GoogleLoginButton.vue -->
<template>
  <button @click="handleGoogleLogin" class="google-login-btn">
    <img src="google-icon.svg" alt="Google" />
    Google로 로그인
  </button>
</template>

<script>
export default {
  name: 'GoogleLoginButton',
  methods: {
    async handleGoogleLogin() {
      try {
        const response = await fetch('http://localhost:8080/api/auth/oauth2/google/url', {
          credentials: 'include'
        });
        
        const data = await response.json();
        window.location.href = `http://localhost:8080${data.url}`;
        
      } catch (error) {
        console.error('Google 로그인 오류:', error);
        alert('로그인에 실패했습니다.');
      }
    }
  }
}
</script>

<style scoped>
.google-login-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  border: 1px solid #dadce0;
  border-radius: 4px;
  background-color: #fff;
  cursor: pointer;
}

.google-login-btn:hover {
  background-color: #f8f9fa;
}
</style>
```

### 2. OAuth2 콜백 페이지

```vue
<!-- pages/OAuth2Callback.vue -->
<template>
  <div class="callback-container">
    <div class="spinner"></div>
    <p>{{ status }}</p>
  </div>
</template>

<script>
export default {
  name: 'OAuth2Callback',
  data() {
    return {
      status: '로그인 처리 중...'
    }
  },
  async mounted() {
    try {
      const response = await fetch('http://localhost:8080/api/auth/oauth2/user', {
        credentials: 'include'
      });

      const data = await response.json();
      
      if (data.success) {
        localStorage.setItem('user', JSON.stringify(data.user));
        this.status = '로그인 성공! 리다이렉트 중...';
        
        setTimeout(() => {
          this.$router.push('/dashboard');
        }, 1000);
      } else {
        throw new Error(data.message);
      }
      
    } catch (error) {
      console.error('사용자 정보 조회 오류:', error);
      this.status = '로그인에 실패했습니다.';
      
      setTimeout(() => {
        this.$router.push('/login');
      }, 2000);
    }
  }
}
</script>

<style scoped>
.callback-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  flex-direction: column;
  gap: 20px;
}
</style>
```

---

## 🔧 Axios 사용 예제

### Axios 인스턴스 설정

```javascript
// api/axios.js
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true, // 쿠키 포함
  headers: {
    'Content-Type': 'application/json'
  }
});

export default api;
```

### API 호출 예제

```javascript
// api/auth.js
import api from './axios';

export const authAPI = {
  // Google 로그인 URL 조회
  getGoogleLoginUrl: async () => {
    const response = await api.get('/api/auth/oauth2/google/url');
    return response.data;
  },

  // 사용자 정보 조회
  getUser: async () => {
    const response = await api.get('/api/auth/oauth2/user');
    return response.data;
  },

  // 로그인 상태 확인
  checkStatus: async () => {
    const response = await api.get('/api/auth/oauth2/status');
    return response.data;
  },

  // 로그아웃
  logout: async () => {
    const response = await api.post('/api/auth/oauth2/logout');
    return response.data;
  }
};
```

---

## ⚙️ 환경 변수 설정

### React (.env)

```env
REACT_APP_API_URL=http://localhost:8080
```

### Vue (.env)

```env
VUE_APP_API_URL=http://localhost:8080
```

### 사용 예제

```javascript
const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const response = await fetch(`${API_URL}/api/auth/oauth2/google/url`, {
  credentials: 'include'
});
```

---

## 🔒 보안 고려사항

### 1. CORS 설정 확인

백엔드 `application.yml`에서 프론트엔드 도메인이 허용되었는지 확인:

```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173
```

### 2. Credentials 포함

모든 API 요청에 `credentials: 'include'` 또는 `withCredentials: true` 설정:

```javascript
// Fetch API
fetch(url, { credentials: 'include' })

// Axios
axios.create({ withCredentials: true })
```

### 3. HTTPS 사용 (프로덕션)

프로덕션 환경에서는 반드시 HTTPS 사용:

```env
REACT_APP_API_URL=https://api.yourdomain.com
```

### 4. XSS 방지

사용자 입력 데이터는 항상 검증하고 이스케이프 처리:

```javascript
import DOMPurify from 'dompurify';

const sanitizedName = DOMPurify.sanitize(user.name);
```

---

## 🐛 문제 해결

### 1. CORS 오류

**증상**: `Access-Control-Allow-Origin` 오류

**해결**:
- 백엔드 `application.yml`에서 CORS 설정 확인
- `credentials: 'include'` 설정 확인
- 프론트엔드 도메인이 `allowed-origins`에 포함되어 있는지 확인

### 2. 쿠키가 저장되지 않음

**증상**: 세션 쿠키가 브라우저에 저장되지 않음

**해결**:
- `withCredentials: true` 설정 확인
- 백엔드 CORS 설정에서 `allowCredentials: true` 확인
- 개발 환경에서는 `secure: false` 설정 (HTTPS가 아닌 경우)

### 3. 리다이렉트 후 사용자 정보를 가져올 수 없음

**증상**: OAuth2 콜백에서 401 에러

**해결**:
- 쿠키가 제대로 전송되는지 브라우저 개발자 도구에서 확인
- 백엔드 로그에서 세션이 생성되었는지 확인
- 세션 타임아웃 설정 확인

---

## 📚 참고 자료

- [React Router 공식 문서](https://reactrouter.com/)
- [Axios 공식 문서](https://axios-http.com/)
- [Vue Router 공식 문서](https://router.vuejs.org/)
- [MDN - Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)

---

**작성일**: 2025-11-04  
**버전**: 1.0.0

