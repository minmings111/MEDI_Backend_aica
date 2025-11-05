# OAuth2 프론트엔드 테스트 앱 (React + Vite)

## 🚀 빠른 시작

### 1. 프로젝트 생성

```bash
npm create vite@latest oauth2-test-app -- --template react
cd oauth2-test-app
npm install
npm install react-router-dom axios
```

---

## 📁 프로젝트 구조

```
oauth2-test-app/
├── src/
│   ├── api/
│   │   └── auth.js           # API 클라이언트
│   ├── components/
│   │   └── GoogleLoginButton.jsx
│   ├── pages/
│   │   ├── LoginPage.jsx
│   │   ├── OAuth2Callback.jsx
│   │   └── Dashboard.jsx
│   ├── App.jsx
│   ├── main.jsx
│   └── index.css
├── .env
└── package.json
```

---

## 🔧 환경 변수 설정

### `.env`

```env
VITE_API_URL=http://localhost:8080
```

---

## 📡 API 주소 정리

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `http://localhost:8080/api/auth/oauth2/google/url` | Google 로그인 URL 조회 |
| GET | `http://localhost:8080/oauth2/authorization/google` | Google 로그인 시작 (리다이렉트) |
| GET | `http://localhost:8080/api/auth/oauth2/user` | 로그인한 사용자 정보 조회 |
| GET | `http://localhost:8080/api/auth/oauth2/status` | 로그인 상태 확인 |
| POST | `http://localhost:8080/api/auth/oauth2/logout` | 로그아웃 |

---

## 📄 전체 코드

### 1. `src/api/auth.js`

```javascript
import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

export const authAPI = {
  getGoogleLoginUrl: async () => {
    const response = await api.get('/api/auth/oauth2/google/url');
    return response.data;
  },

  getUser: async () => {
    const response = await api.get('/api/auth/oauth2/user');
    return response.data;
  },

  checkStatus: async () => {
    const response = await api.get('/api/auth/oauth2/status');
    return response.data;
  },

  logout: async () => {
    const response = await api.post('/api/auth/oauth2/logout');
    return response.data;
  }
};
```

---

### 2. `src/components/GoogleLoginButton.jsx`

```jsx
import { useState } from 'react';

const GoogleLoginButton = () => {
  const [loading, setLoading] = useState(false);
  const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

  const handleGoogleLogin = () => {
    setLoading(true);
    window.location.href = `${API_URL}/oauth2/authorization/google`;
  };

  return (
    <button
      onClick={handleGoogleLogin}
      disabled={loading}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '10px',
        padding: '12px 24px',
        border: '1px solid #dadce0',
        borderRadius: '8px',
        backgroundColor: '#fff',
        cursor: loading ? 'not-allowed' : 'pointer',
        fontSize: '16px',
        fontWeight: '500',
        transition: 'all 0.2s',
        opacity: loading ? 0.6 : 1
      }}
    >
      <svg width="20" height="20" viewBox="0 0 20 20">
        <path fill="#4285F4" d="M19.6 10.2c0-.7-.1-1.4-.2-2H10v3.8h5.4c-.2 1.2-1 2.2-2 2.9v2.5h3.2c1.9-1.7 3-4.3 3-7.2z"/>
        <path fill="#34A853" d="M10 20c2.7 0 4.9-.9 6.6-2.4l-3.2-2.5c-.9.6-2 .9-3.4.9-2.6 0-4.8-1.8-5.6-4.1H1.1v2.6C2.8 17.4 6.2 20 10 20z"/>
        <path fill="#FBBC05" d="M4.4 12c-.2-.6-.3-1.3-.3-2s.1-1.4.3-2V5.4H1.1C.4 6.8 0 8.4 0 10s.4 3.2 1.1 4.6l3.3-2.6z"/>
        <path fill="#EA4335" d="M10 4c1.5 0 2.8.5 3.8 1.5l2.9-2.9C15 1 12.7 0 10 0 6.2 0 2.8 2.6 1.1 6.4l3.3 2.6C5.2 5.8 7.4 4 10 4z"/>
      </svg>
      {loading ? '로그인 중...' : 'Google로 로그인'}
    </button>
  );
};

export default GoogleLoginButton;
```

---

### 3. `src/pages/LoginPage.jsx`

```jsx
import GoogleLoginButton from '../components/GoogleLoginButton';

const LoginPage = () => {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: '#f5f5f5',
      padding: '20px'
    }}>
      <div style={{
        backgroundColor: 'white',
        padding: '40px',
        borderRadius: '12px',
        boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
        maxWidth: '400px',
        width: '100%'
      }}>
        <h1 style={{ textAlign: 'center', marginBottom: '30px', color: '#333' }}>
          Medi OAuth2 테스트
        </h1>
        
        <GoogleLoginButton />
        
        <div style={{ 
          marginTop: '20px', 
          padding: '15px', 
          backgroundColor: '#f0f7ff',
          borderRadius: '8px',
          fontSize: '14px',
          color: '#555'
        }}>
          <strong>테스트 순서:</strong>
          <ol style={{ marginTop: '10px', paddingLeft: '20px' }}>
            <li>Google 로그인 버튼 클릭</li>
            <li>Google 계정 선택</li>
            <li>자동으로 대시보드로 이동</li>
          </ol>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
```

---

### 4. `src/pages/OAuth2Callback.jsx`

```jsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI } from '../api/auth';

const OAuth2Callback = () => {
  const navigate = useNavigate();
  const [status, setStatus] = useState('로그인 처리 중...');
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchUserInfo = async () => {
      try {
        const data = await authAPI.getUser();
        
        if (data.success) {
          setStatus('✅ 로그인 성공! 대시보드로 이동합니다...');
          
          setTimeout(() => {
            navigate('/dashboard');
          }, 1000);
        } else {
          throw new Error(data.message || '로그인 실패');
        }
      } catch (err) {
        console.error('사용자 정보 조회 오류:', err);
        setError(err.response?.data?.message || err.message);
        setStatus('❌ 로그인에 실패했습니다.');
        
        setTimeout(() => {
          navigate('/');
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
          <div className="spinner" style={{
            border: '4px solid #f3f3f3',
            borderTop: '4px solid #4285F4',
            borderRadius: '50%',
            width: '50px',
            height: '50px',
            animation: 'spin 1s linear infinite',
            margin: '0 auto 20px'
          }}></div>
        )}
        
        <h2 style={{ color: error ? '#dc3545' : '#333', marginBottom: '10px' }}>
          {status}
        </h2>
        
        {error && (
          <p style={{ color: '#666', fontSize: '14px' }}>
            {error}
          </p>
        )}
      </div>
    </div>
  );
};

export default OAuth2Callback;
```

---

### 5. `src/pages/Dashboard.jsx`

```jsx
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authAPI } from '../api/auth';

const Dashboard = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = async () => {
    try {
      const data = await authAPI.checkStatus();
      
      if (data.isLoggedIn) {
        setUser(data);
      } else {
        navigate('/');
      }
    } catch (error) {
      console.error('인증 확인 오류:', error);
      navigate('/');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await authAPI.logout();
      navigate('/');
    } catch (error) {
      console.error('로그아웃 오류:', error);
    }
  };

  if (loading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        minHeight: '100vh' 
      }}>
        로딩 중...
      </div>
    );
  }

  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: '#f5f5f5',
      padding: '40px 20px'
    }}>
      <div style={{
        maxWidth: '800px',
        margin: '0 auto'
      }}>
        <div style={{
          backgroundColor: 'white',
          padding: '30px',
          borderRadius: '12px',
          boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
          marginBottom: '20px'
        }}>
          <h1 style={{ marginBottom: '20px', color: '#333' }}>
            ✅ OAuth2 로그인 성공!
          </h1>
          
          <div style={{
            backgroundColor: '#f8f9fa',
            padding: '20px',
            borderRadius: '8px',
            marginBottom: '20px'
          }}>
            <h3 style={{ marginBottom: '15px', color: '#555' }}>사용자 정보</h3>
            
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <tbody>
                <tr>
                  <td style={{ padding: '10px', fontWeight: 'bold', width: '150px' }}>이름:</td>
                  <td style={{ padding: '10px' }}>{user?.name || 'N/A'}</td>
                </tr>
                <tr style={{ backgroundColor: '#fff' }}>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>이메일:</td>
                  <td style={{ padding: '10px' }}>{user?.email || 'N/A'}</td>
                </tr>
                <tr>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>로그인 방식:</td>
                  <td style={{ padding: '10px' }}>
                    <span style={{
                      backgroundColor: '#4285F4',
                      color: 'white',
                      padding: '4px 12px',
                      borderRadius: '4px',
                      fontSize: '14px'
                    }}>
                      {user?.provider || 'N/A'}
                    </span>
                  </td>
                </tr>
                <tr style={{ backgroundColor: '#fff' }}>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>로그인 상태:</td>
                  <td style={{ padding: '10px' }}>
                    <span style={{
                      backgroundColor: '#34A853',
                      color: 'white',
                      padding: '4px 12px',
                      borderRadius: '4px',
                      fontSize: '14px'
                    }}>
                      로그인됨
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          
          <button
            onClick={handleLogout}
            style={{
              padding: '12px 24px',
              backgroundColor: '#dc3545',
              color: 'white',
              border: 'none',
              borderRadius: '8px',
              fontSize: '16px',
              cursor: 'pointer',
              transition: 'background-color 0.2s'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = '#c82333'}
            onMouseOut={(e) => e.target.style.backgroundColor = '#dc3545'}
          >
            로그아웃
          </button>
        </div>
        
        <div style={{
          backgroundColor: '#e7f3ff',
          padding: '20px',
          borderRadius: '12px',
          border: '1px solid #b3d9ff'
        }}>
          <h3 style={{ marginBottom: '10px', color: '#0056b3' }}>
            💡 테스트 완료!
          </h3>
          <p style={{ color: '#555', marginBottom: '10px' }}>
            Google OAuth2 로그인이 정상적으로 작동합니다.
          </p>
          <ul style={{ paddingLeft: '20px', color: '#555' }}>
            <li>세션 기반 인증 ✅</li>
            <li>사용자 정보 조회 ✅</li>
            <li>자동 회원가입 ✅</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
```

---

### 6. `src/App.jsx`

```jsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import OAuth2Callback from './pages/OAuth2Callback';
import Dashboard from './pages/Dashboard';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route path="/oauth2/callback" element={<OAuth2Callback />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
```

---

### 7. `src/index.css`

```css
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

button:active {
  transform: translateY(0);
}
```

---

### 8. `src/main.jsx`

```jsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

---

## 🎯 실행 방법

### 1. 백엔드 실행

```bash
cd c:\medi\backend
./gradlew bootRun
```

백엔드가 `http://localhost:8080`에서 실행됩니다.

### 2. 프론트엔드 실행

```bash
cd oauth2-test-app
npm run dev
```

프론트엔드가 `http://localhost:5173`에서 실행됩니다.

### 3. 브라우저에서 접속

```
http://localhost:5173
```

---

## ✅ 테스트 체크리스트

### 백엔드 확인
- [ ] 백엔드 서버가 `http://localhost:8080`에서 실행 중
- [ ] Google OAuth2 클라이언트 ID/Secret 환경 변수 설정 완료
- [ ] Google Cloud Console에서 리다이렉트 URI 등록: `http://localhost:8080/login/oauth2/code/google`
- [ ] `application.yml`의 CORS 설정에 `http://localhost:5173` 포함

### 프론트엔드 확인
- [ ] `.env` 파일에 `VITE_API_URL=http://localhost:8080` 설정
- [ ] `npm install` 완료
- [ ] 프론트엔드 서버가 `http://localhost:5173`에서 실행 중

### 로그인 플로우 테스트
1. [ ] `http://localhost:5173` 접속
2. [ ] "Google로 로그인" 버튼 클릭
3. [ ] Google 계정 선택 화면으로 리다이렉트
4. [ ] 계정 선택 및 권한 동의
5. [ ] 자동으로 콜백 페이지로 이동
6. [ ] 사용자 정보가 표시된 대시보드로 이동
7. [ ] 로그아웃 버튼으로 로그아웃 테스트

---

## 🐛 문제 해결

### 1. CORS 오류 발생

**백엔드 `application.yml` 확인:**

```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173
```

### 2. 쿠키가 전송되지 않음

**브라우저 개발자 도구 → Application → Cookies 확인**

- `MEDI_SESSION` 쿠키가 있어야 함
- `withCredentials: true` 설정 확인

### 3. 리다이렉트 URI 오류

**Google Cloud Console 확인:**

- 등록된 URI: `http://localhost:8080/login/oauth2/code/google`
- 대소문자, 슬래시 정확히 일치해야 함

### 4. 환경 변수가 로드되지 않음

**Vite는 `VITE_` 접두사 필수:**

```env
VITE_API_URL=http://localhost:8080
```

---

## 📊 API 응답 예시

### GET `/api/auth/oauth2/status` (로그인됨)

```json
{
  "isLoggedIn": true,
  "provider": "GOOGLE",
  "email": "user@gmail.com",
  "name": "홍길동"
}
```

### GET `/api/auth/oauth2/user` (로그인됨)

```json
{
  "success": true,
  "user": {
    "id": 1,
    "email": "user@gmail.com",
    "name": "홍길동",
    "provider": "GOOGLE",
    "providerId": "google-sub-id",
    "profileImage": "https://lh3.googleusercontent.com/...",
    "role": "USER"
  },
  "message": "사용자 정보 조회 성공"
}
```

### POST `/api/auth/oauth2/logout`

```json
{
  "success": true,
  "message": "로그아웃 성공"
}
```

---

## 🎉 완료!

이제 프론트엔드에서 Google OAuth2 로그인을 테스트할 수 있습니다.

**테스트 순서:**
1. 백엔드 실행
2. 프론트엔드 실행
3. 브라우저에서 `http://localhost:5173` 접속
4. Google 로그인 테스트

**작성일**: 2025-11-04  
**버전**: 1.0.0

