# MEDI 프론트엔드 개발 가이드

## 📌 기본 정보

- **프레임워크**: React + Vite + TypeScript
- **HTTP 클라이언트**: Axios
- **상태 관리**: Redux Toolkit
- **서버 주소**: `http://localhost:8080`
- **인증 방식**: 세션 기반 (쿠키 자동 관리)
- **세션 쿠키명**: `MEDI_SESSION`
- **Content-Type**: `application/json`

## 🚀 프로젝트 설정

### 1. 프로젝트 생성

```bash
# Vite로 React + TypeScript 프로젝트 생성
npm create vite@latest medi-frontend -- --template react-ts

cd medi-frontend

# 필수 의존성 설치
npm install
npm install axios react-router-dom @reduxjs/toolkit react-redux
npm install -D @types/react @types/react-dom
```

### 2. 프로젝트 구조 (권장)

```
medi-frontend/
├── src/
│   ├── api/
│   │   └── axiosConfig.ts          # Axios 인스턴스 설정
│   ├── store/
│   │   ├── index.ts                # Redux store 설정
│   │   └── slices/
│   │       ├── authSlice.ts        # 인증 상태 관리
│   │       └── youtubeSlice.ts     # YouTube 데이터 상태 관리
│   ├── types/                      # TypeScript 타입 정의
│   │   ├── auth.types.ts           # 인증 관련 타입
│   │   └── youtube.types.ts        # YouTube 관련 타입
│   ├── components/                 # 컴포넌트
│   ├── pages/                      # 페이지 컴포넌트
│   └── App.tsx                     # 메인 App 컴포넌트
```

## 🔐 핵심 원칙

### 1. 모든 요청에 쿠키 포함 필수

**Axios 사용 (권장):**
```typescript
import axios from 'axios';

// Axios 인스턴스 생성 (전역 설정)
const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,  // ← 이것 없으면 세션 쿠키가 전송되지 않음!
  headers: {
    'Content-Type': 'application/json'
  }
});

// 사용
api.get('/api/auth/me');
```

**Fetch 사용 (대안):**
```typescript
fetch('http://localhost:8080/api/auth/me', {
  method: 'GET',
  credentials: 'include',  // ← 이것 없으면 세션 쿠키가 전송되지 않음!
  headers: {
    'Content-Type': 'application/json',
  },
})
```

**이유**: 세션 기반 인증이므로 쿠키가 자동으로 전송되어야 합니다.

### 2. 앱 시작 시 `/api/auth/me` 호출 필수

**왜 필요한가?**

프론트엔드는 **쿠키를 읽을 수 없습니다**. 쿠키는 `http-only: true`로 설정되어 있어 JavaScript에서 접근할 수 없습니다. 또한 페이지 새로고침 시 Redux나 React 상태가 초기화되므로, 사용자 정보를 복구하기 위해 백엔드에 물어봐야 합니다.

#### 핵심 문제점

1. **쿠키는 JavaScript로 읽을 수 없음**
   ```typescript
   // ❌ 이건 불가능합니다!
   const cookie = document.cookie; // MEDI_SESSION 쿠키가 안 보임!
   // http-only: true이므로 보안상 JavaScript 접근 불가
   ```

2. **페이지 새로고침 시 상태 초기화**
   ```typescript
   // 사용자가 F5를 눌렀을 때
   // Redux 상태 초기화 → user = null
   // 쿠키는 브라우저에 있지만 확인 불가
   // 사용자 정보를 어디서 가져올지 모름
   ```

3. **세션 만료 감지 필요**
   - 쿠키가 있어도 서버 세션이 만료되었을 수 있음
   - 서버 재시작, 세션 타임아웃(30분), 서버에서 세션 삭제 등
   - 프론트엔드만으로는 세션 유효성 확인 불가

#### 해결 방법: `/api/auth/me` 호출

**Axios + Redux 사용 (권장):**
```typescript
// types/auth.types.ts
export interface User {
  id: number;
  email: string;
  name: string;
  role: string;
}

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  lastVerified: number | null;
}

// store/slices/authSlice.ts
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import api from '../../api/axiosConfig';
import type { User, AuthState } from '../../types/auth.types';

interface AuthMeResponse {
  authenticated: boolean;
  user?: User;
  sessionId?: string;
  message?: string;
}

// 앱 시작 시 세션 확인
export const checkAuthStatus = createAsyncThunk(
  'auth/checkAuthStatus',
  async (_, { rejectWithValue }) => {
    try {
      const response = await api.get<AuthMeResponse>('/api/auth/me');
      const data = response.data;
      
      if (data.authenticated && data.user) {
        return data.user;  // user 객체만 반환
      } else {
        return rejectWithValue('로그인되지 않음');
      }
    } catch (error: any) {
      return rejectWithValue(error.message || '세션 확인 실패');
    }
  }
);

// App.tsx - 앱 시작 시
import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { checkAuthStatus } from './store/slices/authSlice';

function App() {
  const dispatch = useDispatch();
  
  useEffect(() => {
    dispatch(checkAuthStatus());
  }, [dispatch]);
  
  return <Router>...</Router>;
}
```

**Fetch 사용 (대안):**
```typescript
// 앱 시작 시 (페이지 로드/새로고침)
useEffect(() => {
  const checkAuthStatus = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/auth/me', {
        credentials: 'include',  // 쿠키 자동 전송
      });
      
      const data = await response.json() as AuthMeResponse;
      
      if (data.authenticated && data.user) {
        setUser(data.user);
        setIsLoggedIn(true);
      } else {
        setUser(null);
        setIsLoggedIn(false);
      }
    } catch (error) {
      console.error('세션 확인 실패:', error);
      setUser(null);
      setIsLoggedIn(false);
    }
  };
  
  checkAuthStatus();
}, []);
```

#### 언제 사용하나?

- ✅ **앱 시작 시** (페이지 로드/새로고침)
- ✅ **로그인 상태 확인이 필요할 때**
- ✅ **사용자 정보를 표시해야 할 때**

#### `/api/auth/me` 없이는?

```typescript
// ❌ 문제가 되는 시나리오
function App() {
  const [user, setUser] = useState<User | null>(null);
  
  // 페이지 새로고침 시
  // Redux/상태 초기화 → user = null
  // 쿠키는 있지만 확인 불가
  // 사용자 정보를 어디서 가져올지 모름 ❌
  
  // 결과: 로그인 페이지로 잘못 리다이렉트
  // 실제로는 세션이 유효한데도!
}
```

### 3. 페이지 로딩 시 자동 데이터 가져오기

`useEffect`를 사용하여 페이지가 마운트될 때 자동으로 API를 호출합니다.

---

## 🗂️ 전역 상태 관리 (State Management)

**이 프로젝트는 Redux Toolkit을 사용합니다.**

### 메인: Redux Toolkit (권장)

#### 1. Axios 설정 (필수)

```typescript
// api/axiosConfig.ts
import axios, { AxiosError } from 'axios';
import store from '../store';
import { logoutUser } from '../store/slices/authSlice';

// Axios 인스턴스 생성
const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,  // 모든 요청에 쿠키 포함
  headers: {
    'Content-Type': 'application/json'
  }
});

// 응답 인터셉터: 401 처리 (세션 만료)
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // 세션 만료 시 로그아웃 처리
      store.dispatch(logoutUser());
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
```

#### 2. Redux Store 설정

```typescript
// store/index.ts
import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
// import youtubeReducer from './slices/youtubeSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    // youtube: youtubeReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

#### 3. Redux Provider 설정

```typescript
// main.tsx (Vite)
import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { store } from './store';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <App />
    </Provider>
  </React.StrictMode>
);
```

#### 4. Auth Slice (인증 상태 관리)

```typescript
// store/slices/authSlice.ts
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import api from '../../api/axiosConfig';
import type { User, AuthState } from '../../types/auth.types';

interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  success: boolean;
  message?: string;
  user?: User;
  error?: string;
}

interface AuthMeResponse {
  authenticated: boolean;
  user?: User;
  sessionId?: string;
  message?: string;
}

// 비동기 액션: 세션 확인
export const checkAuthStatus = createAsyncThunk(
  'auth/checkAuthStatus',
  async (_, { rejectWithValue }) => {
    try {
      const response = await api.get<AuthMeResponse>('/api/auth/me');
      const data = response.data;
      
      if (data.authenticated && data.user) {
        return data.user;  // user 객체만 반환
      } else {
        return rejectWithValue('로그인되지 않음');
      }
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || error.message);
    }
  }
);

// 비동기 액션: 로그인
export const loginUser = createAsyncThunk(
  'auth/loginUser',
  async ({ email, password }: LoginRequest, { rejectWithValue }) => {
    try {
      const response = await api.post<LoginResponse>('/api/auth/login', { email, password });
      const data = response.data;
      
      if (data.success && data.user) {
        return data.user;  // user 객체만 반환
      } else {
        return rejectWithValue(data.message || '로그인 실패');
      }
    } catch (error: any) {
      return rejectWithValue(
        error.response?.data?.message || '로그인 중 오류가 발생했습니다'
      );
    }
  }
);

// 비동기 액션: 로그아웃
export const logoutUser = createAsyncThunk(
  'auth/logoutUser',
  async (_, { rejectWithValue }) => {
    try {
      await api.post('/api/auth/logout');
    } catch (error: any) {
      return rejectWithValue(error.message);
    }
  }
);

const initialState: AuthState = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  lastVerified: null,  // 마지막 검증 시간
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // checkAuthStatus
      .addCase(checkAuthStatus.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(checkAuthStatus.fulfilled, (state, action) => {
        state.isLoading = false;
        state.user = action.payload;
        state.isAuthenticated = true;
        state.lastVerified = Date.now();
        state.error = null;
      })
      .addCase(checkAuthStatus.rejected, (state, action) => {
        state.isLoading = false;
        state.user = null;
        state.isAuthenticated = false;
        state.lastVerified = null;
        state.error = action.payload;
      })
      // loginUser
      .addCase(loginUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.isLoading = false;
        state.user = action.payload;
        state.isAuthenticated = true;
        state.lastVerified = Date.now();
        state.error = null;
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.isLoading = false;
        state.isAuthenticated = false;
        state.error = action.payload;
      })
      // logoutUser
      .addCase(logoutUser.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(logoutUser.fulfilled, (state) => {
        state.isLoading = false;
        state.user = null;
        state.isAuthenticated = false;
        state.lastVerified = null;
        state.error = null;
      })
      .addCase(logoutUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload;
      });
  },
});

export const { clearError } = authSlice.actions;
export default authSlice.reducer;
```

#### 5. 사용 예시

```typescript
// App.tsx
import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { checkAuthStatus } from './store/slices/authSlice';
import type { AppDispatch, RootState } from './store';

function App() {
  const dispatch = useDispatch<AppDispatch>();
  const { isLoading } = useSelector((state: RootState) => state.auth);

  // 앱 시작 시 세션 확인
  useEffect(() => {
    dispatch(checkAuthStatus());
  }, [dispatch]);

  if (isLoading) {
    return <div>로딩 중...</div>;
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
      </Routes>
    </BrowserRouter>
  );
}

// LoginPage.tsx
import { useState, FormEvent } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { loginUser } from '../store/slices/authSlice';
import type { AppDispatch } from '../store';

function LoginPage() {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    
    const result = await dispatch(loginUser({ email, password }));
    
    if (loginUser.fulfilled.match(result)) {
      navigate('/dashboard');
    } else {
      alert(result.payload || '로그인 실패');
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="이메일"
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="비밀번호"
      />
      <button type="submit">로그인</button>
    </form>
  );
}

// DashboardPage.tsx
import { useSelector, useDispatch } from 'react-redux';
import { Navigate } from 'react-router-dom';
import { logoutUser } from '../store/slices/authSlice';
import type { AppDispatch, RootState } from '../store';

function DashboardPage() {
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth);
  const dispatch = useDispatch<AppDispatch>();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" />;
  }

  const handleLogout = () => {
    dispatch(logoutUser());
  };

  return (
    <div>
      <h1>환영합니다, {user.name}님!</h1>
      <button onClick={handleLogout}>로그아웃</button>
    </div>
  );
}
```

---

### 참고: 다른 상태 관리 방법들 (선택사항)

이 프로젝트는 Redux Toolkit을 메인으로 사용하지만, 참고용으로 다른 방법들도 소개합니다.

#### 방법 1: Context API (간단한 프로젝트용)

Axios를 사용한 Context API 예시:

```typescript
// contexts/AuthContext.tsx
import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import api from '../api/axiosConfig';
import type { User } from '../types/auth.types';

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; message?: string }>;
  logout: () => Promise<void>;
  setUser: (user: User | null) => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const checkSession = async () => {
      try {
        const response = await api.get<{ authenticated: boolean; user?: User }>('/api/auth/me');
        if (response.data.authenticated && response.data.user) {
          setUser(response.data.user);
        }
      } catch (error) {
        console.error('세션 확인 실패:', error);
      } finally {
        setLoading(false);
      }
    };
    checkSession();
  }, []);

  const login = async (email: string, password: string) => {
    try {
      const response = await api.post<{ success: boolean; message?: string; user?: User }>('/api/auth/login', { email, password });
      if (response.data.success && response.data.user) {
        setUser(response.data.user);
        return { success: true };
      }
      return { success: false, message: response.data.message };
    } catch (error: any) {
      return { 
        success: false, 
        message: error.response?.data?.message || '로그인 중 오류가 발생했습니다' 
      };
    }
  };

  const logout = async () => {
    try {
      await api.post('/api/auth/logout');
      setUser(null);
    } catch (error) {
      console.error('로그아웃 실패:', error);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, setUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth는 AuthProvider 내부에서 사용해야 합니다');
  }
  return context;
}
```

#### 방법 2: Zustand (간단한 프로젝트용)

```bash
npm install zustand
```

```javascript
// stores/authStore.js
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import api from '../api/axiosConfig';

export const useAuthStore = create(
  persist(
    (set) => ({
      user: null,
      loading: true,
      
      initialize: async () => {
        try {
          const response = await api.get('/api/auth/me');
          const data = response.data;
          if (data.authenticated && data.user) {
            set({ user: data.user, loading: false });
          } else {
            set({ user: null, loading: false });
          }
        } catch (error) {
          set({ user: null, loading: false });
        }
      },
      
      login: async (email, password) => {
        try {
          const response = await api.post('/api/auth/login', { email, password });
          if (response.data.success) {
            set({ user: response.data.user });
            return { success: true };
          }
          return { success: false, message: response.data.message };
        } catch (error) {
          return { 
            success: false, 
            message: error.response?.data?.message || '로그인 중 오류가 발생했습니다' 
          };
        }
      },
      
      logout: async () => {
        try {
          await api.post('/api/auth/logout');
        } finally {
          set({ user: null });
        }
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({ user: state.user }),
    }
  )
);
```

---

### 참고: 다른 상태 관리 방법들

#### 방법 1: Context API (간단한 프로젝트용)

### 방법 4: React Query + Context (서버 상태 관리)

```bash
npm install @tanstack/react-query
```

```javascript
// contexts/QueryProvider.jsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

export function QueryProvider({ children }) {
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}

// hooks/useAuth.js
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

export function useAuth() {
  return useQuery({
    queryKey: ['auth'],
    queryFn: async () => {
      const response = await fetch('http://localhost:8080/api/auth/me', {
        credentials: 'include',
      });
      const data = await response.json();
      return data;
    },
  });
}

export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ email, password }) => {
      const response = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
      });
      return response.json();
    },
    onSuccess: (data) => {
      if (data.success) {
        queryClient.setQueryData(['auth'], data);
      }
    },
  });
}

// 사용 예시
function DashboardPage() {
  const { data: authData, isLoading } = useAuth();

  if (isLoading) return <div>로딩 중...</div>;
  if (!authData?.authenticated) return <Navigate to="/login" />;

  return <div>환영합니다, {authData.user.name}님!</div>;
}
```

---

## 🗂️ YouTube 데이터 상태 관리

### Context API 예시

```javascript
// contexts/YouTubeContext.jsx
import { createContext, useContext, useState, useEffect } from 'react';
import { useAuth } from './AuthContext';

const YouTubeContext = createContext(null);

export function YouTubeProvider({ children }) {
  const { user } = useAuth();
  const [channels, setChannels] = useState([]);
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(false);

  // 채널 목록 가져오기
  const fetchChannels = async () => {
    if (!user) return;

    try {
      setLoading(true);
      const response = await fetch('http://localhost:8080/api/youtube/channels/my', {
        credentials: 'include',
      });
      
      if (response.ok) {
        const data = await response.json();
        setChannels(data);
      }
    } catch (error) {
      console.error('채널 로딩 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  // 영상 목록 가져오기
  const fetchVideos = async () => {
    if (!user) return;

    try {
      setLoading(true);
      const response = await fetch('http://localhost:8080/api/youtube/videos/my', {
        credentials: 'include',
      });
      
      if (response.ok) {
        const data = await response.json();
        setVideos(data);
      }
    } catch (error) {
      console.error('영상 로딩 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  // 사용자 로그인 시 자동으로 데이터 가져오기
  useEffect(() => {
    if (user) {
      fetchChannels();
      fetchVideos();
    } else {
      setChannels([]);
      setVideos([]);
    }
  }, [user]);

  return (
    <YouTubeContext.Provider
      value={{
        channels,
        videos,
        loading,
        fetchChannels,
        fetchVideos,
        refreshData: () => {
          fetchChannels();
          fetchVideos();
        },
      }}
    >
      {children}
    </YouTubeContext.Provider>
  );
}

export function useYouTube() {
  const context = useContext(YouTubeContext);
  if (!context) {
    throw new Error('useYouTube는 YouTubeProvider 내부에서 사용해야 합니다');
  }
  return context;
}

// 사용 예시
function ChannelsPage() {
  const { channels, loading, fetchChannels } = useYouTube();

  useEffect(() => {
    fetchChannels();
  }, [fetchChannels]);

  if (loading) return <div>로딩 중...</div>;

  return (
    <div>
      {channels.map(channel => (
        <div key={channel.id}>{channel.channelName}</div>
      ))}
    </div>
  );
}
```

---

## 🚀 인증 플로우

### 일반 로그인 vs OAuth2 로그인

**중요**: 두 로그인 방식 모두 동일하게 작동합니다!
- 모두 세션에 `CustomUserDetails` 저장
- 모두 `AuthUtil`을 통해 사용자 정보 조회 가능
- 모두 동일한 API 사용 가능

---

## 📝 인증 API 사용법

### 1. 로그인

```javascript
// LoginPage.jsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext'; // 또는 Zustand store

function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth(); // Context API 또는 Zustand

  const handleLogin = async (e) => {
    e.preventDefault();
    
    const result = await login(email, password);
    
    if (result.success) {
      navigate('/dashboard');
    } else {
      alert(result.message);
    }
  };

  return (
    <form onSubmit={handleLogin}>
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="이메일"
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="비밀번호"
      />
      <button type="submit">로그인</button>
    </form>
  );
}
```

**응답 예시**:
```json
{
  "success": true,
  "message": "로그인 성공",
  "user": {
    "id": 123,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER"
  },
  "sessionId": "A1B2C3D4E5F6"
}
```

### 2. Google OAuth2 로그인

**Redux + Axios 사용:**

```typescript
// OAuth2LoginButton.tsx
import api from '../api/axiosConfig';

interface OAuthUrlResponse {
  url: string;
}

function OAuth2LoginButton() {
  const handleGoogleLogin = async () => {
    try {
      // 1. Google 로그인 URL 가져오기
      const response = await api.get<OAuthUrlResponse>('/api/auth/oauth2/google/url');
      const data = response.data;
      
      // 2. Google 로그인 페이지로 리다이렉트
      window.location.href = `http://localhost:8080${data.url}`;
      
    } catch (error) {
      console.error('OAuth2 로그인 실패:', error);
    }
  };

  return (
    <button onClick={handleGoogleLogin}>
      Google로 로그인
    </button>
  );
}
```

**OAuth2 콜백 처리 (Redux):**
```typescript
// OAuth2CallbackPage.tsx
import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { checkAuthStatus } from '../store/slices/authSlice';
import type { AppDispatch } from '../store';

function OAuth2CallbackPage() {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  useEffect(() => {
    // OAuth2 로그인 완료 후 세션 확인
    const checkAuth = async () => {
      const result = await dispatch(checkAuthStatus());
      
      if (checkAuthStatus.fulfilled.match(result)) {
        navigate('/dashboard');
      } else {
        navigate('/login');
      }
    };

    checkAuth();
  }, [dispatch, navigate]);

  return <div>로그인 처리 중...</div>;
}
```

### 3. 현재 로그인 상태 확인 (앱 초기화 시)

**Redux 사용:**

```typescript
// App.tsx
import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { checkAuthStatus } from './store/slices/authSlice';
import type { AppDispatch, RootState } from './store';

function App() {
  const dispatch = useDispatch<AppDispatch>();
  const { isLoading } = useSelector((state: RootState) => state.auth);

  // 앱 시작 시 세션 확인
  useEffect(() => {
    dispatch(checkAuthStatus());
  }, [dispatch]);

  if (isLoading) {
    return <div>로딩 중...</div>;
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
      </Routes>
    </BrowserRouter>
  );
}
```

---

## 📺 YouTube 채널 및 영상 관리

### 페이지 로딩 시 자동으로 데이터 가져오기

#### 1. 채널 목록 페이지

```typescript
// ChannelsPage.tsx
import { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import api from '../api/axiosConfig';
import type { RootState } from '../store';

interface Channel {
  id: number;
  userId: number;
  youtubeChannelId: string;
  channelName: string;
  channelHandle: string | null;
  thumbnailUrl: string | null;
  createdAt: string;
  updatedAt: string;
  lastSyncedAt: string;
}

function ChannelsPage() {
  const { isAuthenticated } = useSelector((state: RootState) => state.auth);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    if (!isAuthenticated) return;

    const fetchChannels = async () => {
      try {
        setLoading(true);
        const response = await api.get<Channel[]>('/api/youtube/channels/my');
        setChannels(response.data);
      } catch (error) {
        console.error('채널 로딩 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchChannels();
  }, [isAuthenticated]);

  if (loading) return <div>로딩 중...</div>;

  return (
    <div>
      <h1>내 YouTube 채널 목록</h1>
      {channels.length === 0 ? (
        <p>등록된 채널이 없습니다.</p>
      ) : (
        <ul>
          {channels.map(channel => (
            <li key={channel.id}>
              <img src={channel.thumbnailUrl} alt={channel.channelName} />
              <h3>{channel.channelName}</h3>
              <p>{channel.channelHandle}</p>
              <p>마지막 동기화: {channel.lastSyncedAt}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
```

**응답 예시**:
```json
[
  {
    "id": 1,
    "userId": 123,
    "youtubeChannelId": "UCxxxxx",
    "channelName": "내 채널",
    "channelHandle": "@mychannel",
    "thumbnailUrl": "https://...",
    "lastSyncedAt": "2024-01-15T10:30:00",
    "uploadsPlaylistId": "UUxxxxx"
  }
]
```

#### 2. 채널 상세 페이지 (채널 + 영상 함께)

```javascript
// ChannelDetailPage.jsx
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

function ChannelDetailPage() {
  const { channelId } = useParams();
  const [channel, setChannel] = useState(null);
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchChannelData = async () => {
      try {
        setLoading(true);
        
        // 1. 채널 정보 가져오기
        const channelResponse = await fetch(
          `http://localhost:8080/api/youtube/channels/${channelId}`,
          {
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
          }
        );

        if (channelResponse.ok) {
          const channelData = await channelResponse.json();
          setChannel(channelData);
        }

        // 2. 해당 채널의 영상 목록 가져오기
        const videosResponse = await fetch(
          `http://localhost:8080/api/youtube/videos/channel/${channelId}`,
          {
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
          }
        );

        if (videosResponse.ok) {
          const videosData = await videosResponse.json();
          setVideos(videosData);
        }

      } catch (err) {
        console.error('데이터 로딩 실패:', err);
      } finally {
        setLoading(false);
      }
    };

    if (channelId) {
      fetchChannelData();
    }
  }, [channelId]); // channelId가 변경될 때마다 실행

  if (loading) return <div>로딩 중...</div>;
  if (!channel) return <div>채널을 찾을 수 없습니다</div>;

  return (
    <div>
      <div>
        <img src={channel.thumbnailUrl} alt={channel.channelName} />
        <h1>{channel.channelName}</h1>
        <p>{channel.channelHandle}</p>
      </div>
      
      <h2>영상 목록 ({videos.length}개)</h2>
      <ul>
        {videos.map(video => (
          <li key={video.id}>
            <img src={video.thumbnailUrl} alt={video.title} />
            <h3>{video.title}</h3>
            <p>조회수: {video.viewCount.toLocaleString()}</p>
            <p>업로드: {new Date(video.publishedAt).toLocaleDateString()}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

#### 3. 병렬 요청 (채널 + 영상 동시에)

```javascript
// DashboardPage.jsx
import { useEffect, useState } from 'react';
import { useYouTube } from '../contexts/YouTubeContext';

function DashboardPage() {
  const { channels, videos, loading, refreshData } = useYouTube();
  // 또는 직접 상태 관리:
  // const [channels, setChannels] = useState([]);
  // const [videos, setVideos] = useState([]);
  // const [loading, setLoading] = useState(true);

  useEffect(() => {
    refreshData();
    // 또는:
    // const fetchAllData = async () => {
    //   try {
    //     setLoading(true);
    //     
    //     // 채널과 영상을 동시에 가져오기 (성능 향상)
    //     const [channelsRes, videosRes] = await Promise.all([
    //       fetch('http://localhost:8080/api/youtube/channels/my', {
    //         credentials: 'include',
    //       }),
    //       fetch('http://localhost:8080/api/youtube/videos/my', {
    //         credentials: 'include',
    //       }),
    //     ]);
    //
    //     const channelsData = await channelsRes.json();
    //     const videosData = await videosRes.json();
    //
    //     setChannels(channelsData);
    //     setVideos(videosData);
    //   } catch (err) {
    //     console.error('데이터 로딩 실패:', err);
    //   } finally {
    //     setLoading(false);
    //   }
    // };
    // fetchAllData();
  }, [refreshData]);

  if (loading) return <div>로딩 중...</div>;

  return (
    <div>
      <h1>대시보드</h1>
      <section>
        <h2>내 채널 ({channels.length}개)</h2>
        {/* 채널 목록 렌더링 */}
      </section>
      <section>
        <h2>내 영상 ({videos.length}개)</h2>
        {/* 영상 목록 렌더링 */}
      </section>
    </div>
  );
}
```

---

## 🔄 YouTube OAuth 연결 및 동기화

### 1. YouTube 채널 연결 (OAuth)

```javascript
// ConnectYouTubeButton.jsx
function ConnectYouTubeButton() {
  const handleConnect = () => {
    // Google OAuth 동의 화면으로 리다이렉트
    window.location.href = 'http://localhost:8080/api/youtube/connect';
  };

  return (
    <button onClick={handleConnect}>
      YouTube 채널 연결
    </button>
  );
}
```

### 2. 연결 상태 확인

```javascript
// YouTubeConnectionStatus.jsx
import { useEffect, useState } from 'react';

function YouTubeConnectionStatus() {
  const [isConnected, setIsConnected] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkConnection = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/youtube/token/status', {
          credentials: 'include',
        });
        
        const data = await response.json();
        setIsConnected(data.success);
      } catch (error) {
        setIsConnected(false);
      } finally {
        setLoading(false);
      }
    };

    checkConnection();
  }, []);

  if (loading) return <div>확인 중...</div>;

  return (
    <div>
      {isConnected ? (
        <span>✅ YouTube 연결됨</span>
      ) : (
        <span>❌ YouTube 미연결</span>
      )}
    </div>
  );
}
```

### 3. 채널 동기화 (수동)

```javascript
// useEffect로 초기 채널 목록 로딩
import { useEffect, useState } from 'react';

function ChannelList() {
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchChannels = async () => {
    const res = await fetch('http://localhost:8080/api/youtube/channels/my', {
      credentials: 'include',
    });
    if (!res.ok) throw new Error('채널 조회 실패');
    const data = await res.json();
    setChannels(data);
  };

  useEffect(() => {
    fetchChannels().finally(() => setLoading(false));
  }, []);

  if (loading) return <div>로딩 중...</div>;

  return (
    <ul>
      {channels.map(channel => (
        <li key={channel.id}>{channel.channelName}</li>
      ))}
    </ul>
  );
}

// SyncChannelsButton.jsx - 최신 데이터 동기화
import { useState } from 'react';

function SyncChannelsButton({ onSynced }) {
  const [syncing, setSyncing] = useState(false);

  const handleSync = async () => {
    try {
      setSyncing(true);

      const response = await fetch('http://localhost:8080/api/youtube/channels/sync', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error('채널 동기화 요청 실패');
      }

      const syncedChannels = await response.json();
      onSynced?.(syncedChannels);
    } catch (error) {
      console.error('동기화 실패:', error);
      alert('동기화 중 오류가 발생했습니다');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <button onClick={handleSync} disabled={syncing}>
      {syncing ? '동기화 중...' : '채널 동기화'}
    </button>
  );
}
```

동기화 결과는 `onSynced` 콜백으로 받아 채널 목록 상태를 즉시 갱신하거나, 동기화 이후 `fetchChannels()`를 다시 호출해도 됩니다.

### 4. 채널별 영상 동기화

```javascript
// VideoSyncButton.jsx
import { useState } from 'react';

function VideoSyncButton({ channelId, onSynced }) {
  const [syncing, setSyncing] = useState(false);

  const handleSync = async () => {
    try {
      setSyncing(true);

      const response = await fetch('http://localhost:8080/api/youtube/videos/sync', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ channelId, maxResults: 10 }),
      });

      if (!response.ok) {
        throw new Error('영상 동기화 요청 실패');
      }

      const syncedVideos = await response.json();
      onSynced?.(syncedVideos);
    } catch (error) {
      console.error('영상 동기화 실패:', error);
      alert('영상 동기화 중 오류가 발생했습니다');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <button onClick={handleSync} disabled={syncing}>
      {syncing ? '동기화 중...' : '영상 동기화'}
    </button>
  );
}
```

---

## 💳 결제 관련 API

### 구독 플랜 조회

```javascript
// PlansPage.jsx
import { useEffect, useState } from 'react';

function PlansPage() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchPlans = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/billing/plans', {
          credentials: 'include',
        });
        
        const data = await response.json();
        setPlans(data);
      } catch (error) {
        console.error('플랜 조회 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPlans();
  }, []);

  if (loading) return <div>로딩 중...</div>;

  return (
    <div>
      <h1>구독 플랜</h1>
      {plans.map(plan => (
        <div key={plan.id}>
          <h3>{plan.planName}</h3>
          <p>가격: {plan.price}원</p>
          <p>채널 제한: {plan.channelLimit}개</p>
        </div>
      ))}
    </div>
  );
}
```

---

## 🛡️ 인증 가드 (라우팅 보호)

```javascript
// ProtectedRoute.jsx
import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext'; // 또는 Zustand

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div>로딩 중...</div>;
  }

  return user ? children : <Navigate to="/login" />;
}

// 사용 예시
function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
      </Routes>
    </Router>
  );
}
```

---

## 📋 전체 API 엔드포인트 목록

### 인증 API (`/api/auth`)

| 메서드 | 엔드포인트 | 설명 | 인증 필요 |
|--------|-----------|------|----------|
| POST | `/api/auth/login` | 일반 로그인 | ❌ |
| POST | `/api/auth/logout` | 로그아웃 | ✅ |
| GET | `/api/auth/me` | 현재 로그인 상태 확인 | ❌ |
| DELETE | `/api/auth/withdraw` | 회원탈퇴 | ✅ |
| POST | `/api/auth/send-verification` | 이메일 인증 코드 전송 | ❌ |
| POST | `/api/auth/verify-email` | 이메일 인증 코드 확인 | ❌ |
| POST | `/api/auth/register` | 회원가입 | ❌ |
| POST | `/api/auth/send-password-reset` | 비밀번호 재설정 코드 전송 | ❌ |
| POST | `/api/auth/reset-password` | 비밀번호 재설정 | ❌ |
| PUT | `/api/auth/change-password` | 비밀번호 변경 | ✅ |

### OAuth2 API (`/api/auth/oauth2`)

| 메서드 | 엔드포인트 | 설명 | 인증 필요 |
|--------|-----------|------|----------|
| GET | `/api/auth/oauth2/google/url` | Google 로그인 URL 조회 | ❌ |
| GET | `/api/auth/oauth2/user` | OAuth2 사용자 정보 조회 | ❌ |
| GET | `/api/auth/oauth2/status` | OAuth2 로그인 상태 확인 | ❌ |
| POST | `/api/auth/oauth2/logout` | OAuth2 로그아웃 | ❌ |

### YouTube 채널 API (`/api/youtube/channels`)

| 메서드 | 엔드포인트 | 설명 | 인증 필요 |
|--------|-----------|------|----------|
| GET | `/api/youtube/channels/my` | 내 채널 목록 조회 | ✅ |
| GET | `/api/youtube/channels/{id}` | 특정 채널 조회 | ✅ |
| DELETE | `/api/youtube/channels/{id}` | 채널 삭제 | ✅ |
| GET | `/api/youtube/channels/all` | 전체 채널 조회 (관리자) | ✅ (ADMIN) |

### YouTube 영상 API (`/api/youtube/videos`)

| 메서드 | 엔드포인트 | 설명 | 인증 필요 |
|--------|-----------|------|----------|
| GET | `/api/youtube/videos/my` | 내 영상 목록 조회 | ✅ |
| GET | `/api/youtube/videos/channel/{channelId}` | 특정 채널의 영상 목록 | ✅ |
| GET | `/api/youtube/videos/{id}` | 특정 영상 조회 | ✅ |
| GET | `/api/youtube/videos/all` | 전체 영상 조회 (관리자) | ✅ (ADMIN) |

### 결제 API (`/api/billing`)

| 메서드 | 엔드포인트 | 설명 | 인증 필요 |
|--------|-----------|------|----------|
| GET | `/api/billing/plans` | 플랜 목록 조회 | ❌ |
| GET | `/api/billing/plans/{id}` | 플랜 상세 조회 | ❌ |
| GET | `/api/billing/payment-methods` | 결제 수단 목록 | ✅ |
| POST | `/api/billing/payment-methods` | 결제 수단 추가 | ✅ |
| DELETE | `/api/billing/payment-methods/{id}` | 결제 수단 삭제 | ✅ |
| GET | `/api/billing/subscriptions/my-active` | 활성 구독 조회 | ✅ |
| GET | `/api/billing/subscriptions/my-history` | 구독 히스토리 | ✅ |
| POST | `/api/billing/subscriptions` | 구독 생성 | ✅ |

---

## 🎯 실전 사용 시나리오

### 시나리오 1: 대시보드 페이지 (전역 상태 관리 사용)

```javascript
// DashboardPage.jsx
import { useAuth } from '../contexts/AuthContext';
import { useYouTube } from '../contexts/YouTubeContext';

function DashboardPage() {
  const { user } = useAuth();
  const { channels, videos, loading, refreshData } = useYouTube();

  useEffect(() => {
    refreshData();
  }, [refreshData]);

  if (loading) return <div>로딩 중...</div>;

  return (
    <div>
      <h1>환영합니다, {user?.name}님!</h1>
      
      <section>
        <h2>내 채널 ({channels.length}개)</h2>
        {channels.map(channel => (
          <div key={channel.id}>
            <h3>{channel.channelName}</h3>
            <p>마지막 동기화: {channel.lastSyncedAt}</p>
          </div>
        ))}
      </section>

      <section>
        <h2>내 영상 ({videos.length}개)</h2>
        {videos.map(video => (
          <div key={video.id}>
            <h3>{video.title}</h3>
            <p>조회수: {video.viewCount}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
```

---

## ⚠️ 주의사항

### 1. 에러 처리

```javascript
const fetchData = async () => {
  try {
    const response = await fetch('http://localhost:8080/api/youtube/channels/my', {
      credentials: 'include',
    });

    if (!response.ok) {
      if (response.status === 401) {
        // 로그인 필요
        window.location.href = '/login';
        return;
      }
      if (response.status === 403) {
        // 권한 없음
        alert('접근 권한이 없습니다');
        return;
      }
      throw new Error(`HTTP ${response.status}`);
    }

    const data = await response.json();
    // 데이터 처리

  } catch (error) {
    console.error('요청 실패:', error);
    // 사용자에게 에러 메시지 표시
  }
};
```

### 2. 로딩 상태 관리

```javascript
const [loading, setLoading] = useState(true);
const [error, setError] = useState(null);

useEffect(() => {
  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      // API 호출
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };
  
  fetchData();
}, []);
```

### 3. 중복 요청 방지

```javascript
const [isFetching, setIsFetching] = useState(false);

const fetchData = async () => {
  if (isFetching) return; // 이미 요청 중이면 무시
  
  setIsFetching(true);
  try {
    // API 호출
  } finally {
    setIsFetching(false);
  }
};
```

---

## 📚 유용한 패턴

### 1. 커스텀 Hook 사용

```javascript
// hooks/useAuth.js
import { useState, useEffect } from 'react';

export function useAuth() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/auth/me', {
          credentials: 'include',
        });
        const data = await response.json();
        
        if (data.authenticated) {
          setUser(data.user);
        }
      } catch (error) {
        console.error('인증 확인 실패:', error);
      } finally {
        setLoading(false);
      }
    };

    checkAuth();
  }, []);

  return { user, loading };
}
```

### 2. API 클라이언트 래퍼

```javascript
// apiClient.js
const API_BASE_URL = 'http://localhost:8080';

export const apiClient = {
  async get(endpoint) {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    return response.json();
  },

  async post(endpoint, body) {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    return response.json();
  },
};

// 사용
import { apiClient } from './apiClient';

const channels = await apiClient.get('/api/youtube/channels/my');
```

---

## 🎓 학습 포인트

1. **세션 기반 인증**: 쿠키가 자동으로 관리되므로 프론트엔드에서 신경 쓸 게 거의 없습니다
2. **useEffect 패턴**: 페이지 로딩 시 자동으로 데이터를 가져오는 표준 패턴
3. **에러 처리**: 401, 403 등 상태 코드에 따른 적절한 처리
4. **로딩 상태**: 사용자 경험을 위한 로딩/에러 상태 관리
5. **전역 상태 관리**: Context API, Zustand, Redux 등 적절한 도구 선택
6. **상태 동기화**: 로그인/로그아웃 시 전역 상태 업데이트

---

## 📞 문제 해결

### Q: 401 Unauthorized 오류가 계속 발생해요
A: `credentials: 'include'`를 확인하세요. 모든 요청에 필수입니다.

### Q: 로그인은 되는데 API 호출이 안 돼요
A: 세션 쿠키가 전송되지 않았을 수 있습니다. 브라우저 개발자 도구에서 쿠키를 확인하세요.

### Q: 페이지를 새로고침하면 로그인이 풀려요
A: 페이지 새로고침 시 Redux/React 상태가 초기화되기 때문입니다. 쿠키는 브라우저에 남아있지만, JavaScript에서 읽을 수 없으므로(`http-only: true`) 백엔드에 물어봐야 합니다. `useEffect`에서 `/api/auth/me`를 호출하여 세션을 복구하세요. 또는 전역 상태 관리(Context/Zustand/Redux)를 사용하세요.

**해결 방법:**
```javascript
// 앱 시작 시 세션 확인
useEffect(() => {
  const checkAuth = async () => {
    const response = await fetch('/api/auth/me', {
      credentials: 'include'
    });
    const data = await response.json();
    if (data.authenticated) {
      setUser(data.user); // 상태 복구
    }
  };
  checkAuth();
}, []);
```

### Q: 상태가 컴포넌트 간에 공유되지 않아요
A: Context API 또는 Zustand 같은 전역 상태 관리 라이브러리를 사용하세요.

---

이 가이드를 참고하여 프론트엔드를 구현하시면 됩니다! 🚀

