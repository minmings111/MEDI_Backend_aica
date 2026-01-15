# Medi Backend

YouTube 채널 관리 및 AI 기반 댓글 필터링 서비스의 백엔드 API 서버입니다.

## 📖 개요

Medi Backend는 YouTube 크리에이터를 위한 채널 관리 및 댓글 모더레이션 플랫폼의 서버 애플리케이션입니다. YouTube Data API와 연동하여 채널/영상 데이터를 수집하고, AI Agent와 협업하여 악성 댓글을 자동으로 필터링합니다.

## ✨ 주요 기능

- **사용자 인증**: 이메일/비밀번호 로그인, Google OAuth2 소셜 로그인
- **YouTube 통합**: YouTube 채널 연결, 영상/댓글 동기화, 자막 추출
- **AI 댓글 필터링**: Redis 기반 작업 큐를 통한 AI Agent 연동 및 악성 댓글 자동 분류
- **구독/결제 시스템**: 다양한 플랜 관리, 결제 수단 등록, 구독 히스토리
- **대시보드**: 사용자별 필터링 통계, 채널/영상별 분석, 관리자 통계
- **챗봇 연동**: FastAPI 기반 챗봇 서버와 통합

## 🛠 기술 스택

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17
- **Database**: MySQL (MyBatis 3.0.3)
- **Cache & Queue**: Redis (Lettuce)
- **Authentication**: Spring Security, OAuth2 (Google)
- **External APIs**: YouTube Data API v3
- **Tools**: yt-dlp (자막 추출), Docker, Gradle

## 📁 프로젝트 구조

```
src/main/java/com/medi/backend/
├── auth/              # 인증/인가 (로그인, 회원가입, OAuth2)
├── youtube/           # YouTube API 연동 (채널, 영상, 댓글)
├── billing/           # 결제/구독 시스템
├── filter/            # 댓글 필터링 시스템
├── agent/             # AI Agent 통합 (결과 수신)
├── userdashboard/     # 사용자 대시보드 통계
├── admin/             # 관리자 기능 및 통계
├── chatbot/           # 챗봇 서버 연동
└── global/            # 공통 설정 및 유틸리티
```

## 🚀 시작하기

### 사전 요구사항

- Java 17 이상
- MySQL 8.0
- Redis 6.0 이상
- yt-dlp (자막 추출용)

### 환경 설정

1. **MySQL 데이터베이스 생성**
   ```sql
   CREATE DATABASE medi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Redis 실행**
   ```bash
   # Docker 사용 시
   docker run -d -p 6379:6379 redis:latest
   ```

3. **환경 변수 설정**
   
   `src/main/resources/application.yml` 파일에서 다음 항목을 설정:
   - MySQL 연결 정보 (URL, username, password)
   - Redis 연결 정보
   - Google OAuth2 클라이언트 정보
   - YouTube Data API 키

### 빌드 및 실행

```bash
# Gradle 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 파일 실행
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

서버는 기본적으로 `http://localhost:8080`에서 실행됩니다.

## 📚 API 문서

### Swagger UI
실행 후 다음 URL에서 API 문서를 확인할 수 있습니다:
- `http://localhost:8080/swagger-ui.html`

### 상세 API 문서
전체 API 엔드포인트 및 요청/응답 예시는 다음 문서를 참조하세요:
- [API 엔드포인트 상세 문서](docs/api_endpoints_detailed.txt)

## 📖 추가 문서

프로젝트의 상세한 정보는 `docs/` 폴더의 문서를 참조하세요:

- **[배포 가이드](docs/DEPLOYMENT_DETAILED.md)**: EC2 배포 전체 프로세스 (도메인, AWS 설정, Docker, HTTPS)
- **[Redis 가이드](docs/redis.txt)**: Redis 데이터 구조, 작업 큐, 동기화 프로세스
- **[개인정보 처리방침](docs/privacy_policy.md)**: 서비스 개인정보 처리 정책

## 🔗 관련 프로젝트

- **AI Agent**: Python FastAPI 기반 댓글 분석 및 필터링 서버
- **Frontend**: React 기반 사용자 인터페이스

## 📄 라이선스

This project is licensed under the MIT License.
