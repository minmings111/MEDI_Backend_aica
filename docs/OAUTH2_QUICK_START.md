# OAuth2 빠른 시작 가이드

## 🎯 5분 안에 테스트하기

### 1️⃣ 백엔드 실행 (1분)

```bash
cd c:\medi\backend

# 환경 변수 설정 (PowerShell)
$env:GOOGLE_CLIENT_ID="your-google-client-id"
$env:GOOGLE_CLIENT_SECRET="your-google-client-secret"

# 실행
./gradlew bootRun
```

✅ `http://localhost:8080` 에서 실행 확인

---

### 2️⃣ 프론트엔드 설정 (2분)

```bash
# 프로젝트 생성
npm create vite@latest oauth2-test-app -- --template react
cd oauth2-test-app

# 패키지 설치
npm install
npm install react-router-dom axios

# .env 파일 생성
echo VITE_API_URL=http://localhost:8080 > .env
```

**전체 코드는 `docs/oauth2-frontend-test-app.md` 참고**

---

### 3️⃣ 실행 및 테스트 (2분)

```bash
# 프론트엔드 실행
npm run dev
```

브라우저에서 `http://localhost:5173` 접속 → Google 로그인 테스트

---

## 📋 필수 체크사항

### Google Cloud Console
- [ ] OAuth 2.0 클라이언트 ID 생성
- [ ] 리다이렉트 URI: `http://localhost:8080/login/oauth2/code/google`
- [ ] 클라이언트 ID/Secret 복사

### 백엔드 (application.yml)
```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
```

### 데이터베이스
```sql
-- OAuth2 컬럼 확인
DESC users;

-- 필요시 추가
ALTER TABLE users
ADD COLUMN provider VARCHAR(20) DEFAULT 'LOCAL',
ADD COLUMN provider_id VARCHAR(255) NULL,
ADD COLUMN profile_image VARCHAR(2048) NULL;
```

---

## 🔗 API 엔드포인트

| URL | 설명 |
|-----|------|
| `http://localhost:8080/oauth2/authorization/google` | Google 로그인 시작 |
| `http://localhost:8080/api/auth/oauth2/user` | 사용자 정보 조회 |
| `http://localhost:8080/api/auth/oauth2/status` | 로그인 상태 확인 |
| `http://localhost:8080/api/auth/oauth2/logout` | 로그아웃 |

---

## 📚 상세 문서

- **구현 계획**: `docs/oauth2-implementation-plan.md`
- **백엔드 설정**: `docs/oauth2-setup-guide.md`
- **프론트엔드 연동**: `docs/oauth2-frontend-integration.md`
- **테스트 앱**: `docs/oauth2-frontend-test-app.md` ⭐

---

## 🐛 빠른 문제 해결

| 문제 | 해결 |
|------|------|
| CORS 오류 | `application.yml`에 `http://localhost:5173` 추가 |
| 쿠키 없음 | `withCredentials: true` 확인 |
| 리다이렉트 오류 | Google Console URI 확인 |
| 의존성 오류 | `./gradlew clean build --refresh-dependencies` |

---

**테스트 성공하면 완료!** 🎉

