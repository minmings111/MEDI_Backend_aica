# Medi Backend

YouTube 채널 분석 및 댓글 관리 플랫폼의 백엔드 서버

## 🚀 빠른 시작

### 1. 환경 설정

#### 방법 A: Docker Compose 사용 (권장)

```bash
# 1. 저장소 클론
git clone https://github.com/team-yun-chan/backend.git
cd backend

# 2. 환경 변수 설정
cp .env.example .env
nano .env  # 실제 값으로 변경

# 3. 배포
bash deploy.sh
```

#### 방법 B: 로컬 개발 환경

```bash
# 1. 저장소 클론
git clone https://github.com/team-yun-chan/backend.git
cd backend

# 2. application.yml 생성
cp src/main/resources/application.yml.example src/main/resources/application.yml
nano src/main/resources/application.yml  # 실제 값으로 변경

# 3. Docker로 MySQL/Redis 실행
docker-compose up -d mysql redis

# 4. 애플리케이션 실행
./gradlew bootRun
```

### 2. 필수 설정 항목

다음 항목들을 반드시 설정해야 합니다:

- **Google OAuth2 클라이언트 ID/Secret** ([발급 방법](https://console.cloud.google.com/apis/credentials))
- **Gmail SMTP 설정** ([앱 비밀번호 생성](https://myaccount.google.com/apppasswords))
- **데이터베이스 비밀번호**
- **CORS 허용 도메인**

자세한 내용은 [설정 관리 가이드](docs/config_management_guide.md)를 참고하세요.

## 📋 주요 기능

- **YouTube 채널 연동**: Google OAuth2를 통한 채널 연결
- **자동 동기화**: 매 시간마다 영상 및 댓글 자동 수집
- **댓글 분석**: Redis 기반 실시간 댓글 데이터 처리
- **이메일 인증**: 회원가입 시 이메일 인증 코드 발송
- **RESTful API**: Swagger UI를 통한 API 문서 제공

## 🛠️ 기술 스택

- **Java 17** + **Spring Boot 3.5.6**
- **MySQL 8.0** (데이터베이스)
- **Redis 7** (캐시 및 댓글 데이터)
- **MyBatis** (SQL 매퍼)
- **Docker** + **Docker Compose** (컨테이너화)
- **yt-dlp** (YouTube 메타데이터 수집)

## 📁 프로젝트 구조

```
backend/
├── src/main/java/com/medi/backend/
│   ├── auth/           # 인증 (이메일 인증)
│   ├── chatbot/        # 챗봇 연동
│   ├── global/         # 공통 설정 (Security, Redis, Async)
│   ├── report/         # 리포트 생성
│   ├── user/           # 사용자 관리
│   └── youtube/        # YouTube 연동 및 동기화
│       ├── scheduler/  # 자동 동기화 스케줄러
│       ├── service/    # 비즈니스 로직
│       └── redis/      # Redis 댓글 데이터 처리
├── src/main/resources/
│   ├── application.yml.example  # 설정 템플릿
│   ├── db/migration/            # 데이터베이스 스키마
│   └── mapper/                  # MyBatis XML 매퍼
├── docker-compose.yml           # Docker 설정
├── Dockerfile                   # 컨테이너 이미지
├── .env.example                 # 환경 변수 템플릿
└── deploy.sh                    # 배포 스크립트
```

## 🔧 개발 환경 설정

### 필수 요구사항

- **Java 17** 이상
- **Docker** + **Docker Compose**
- **Python 3** + **pip** (yt-dlp 설치용)

### 로컬 실행

```bash
# 1. 의존성 설치 (yt-dlp 자동 설치)
./gradlew build

# 2. MySQL/Redis 실행
docker-compose up -d mysql redis

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. 브라우저에서 확인
# - API 문서: http://localhost:8080/swagger-ui.html
# - 헬스체크: http://localhost:8080/actuator/health
```

## 🐳 Docker 배포

### 전체 스택 배포

```bash
# 1. 환경 변수 설정
cp .env.example .env
nano .env

# 2. 배포
bash deploy.sh
```

### 개별 서비스 관리

```bash
# 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f backend

# 서비스 중지
docker-compose down

# 재시작
docker-compose restart backend
```

## 📊 모니터링

### Spring Boot Actuator

```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 메트릭
curl http://localhost:8080/actuator/metrics

# 애플리케이션 정보
curl http://localhost:8080/actuator/info
```

### Docker 모니터링

```bash
# 컨테이너 상태
docker-compose ps

# 메모리 사용량
docker stats medi-backend

# 로그 확인
docker-compose logs -f backend
```

## 🔒 보안 설정

### 프로덕션 체크리스트

- [ ] 강력한 데이터베이스 비밀번호 사용
- [ ] Google OAuth2 Redirect URI를 프로덕션 도메인으로 설정
- [ ] CORS 설정에 실제 프론트엔드 도메인만 포함
- [ ] `.env` 파일 권한 설정 (`chmod 600 .env`)
- [ ] MySQL/Redis 포트를 외부에 노출하지 않도록 설정
- [ ] HTTPS 인증서 설정 (프로덕션)

자세한 내용은 [배포 체크리스트](docs/deployment_checklist.md)를 참고하세요.

## 📝 API 문서

애플리케이션 실행 후 Swagger UI에서 확인:
- **로컬**: http://localhost:8080/swagger-ui.html
- **프로덕션**: https://yourdomain.com/swagger-ui.html

## 🐛 트러블슈팅

### 문제 1: "application.yml not found" 에러

```bash
# 해결: 템플릿 복사
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

### 문제 2: OOM (Out of Memory) 에러

```bash
# 해결: JVM 메모리 증가
# docker-compose.yml의 JAVA_OPTS 수정
JAVA_OPTS: "-Xms2g -Xmx2g ..."
```

### 문제 3: YouTube API 할당량 초과

```bash
# 해결: 스케줄러 실행 주기 조정
# YoutubeSyncScheduler.java의 @Scheduled cron 수정
```

더 많은 문제 해결 방법은 [설정 관리 가이드](docs/config_management_guide.md)를 참고하세요.

## 📚 문서

- [배포 체크리스트](docs/deployment_checklist.md) - 배포 전 확인 사항
- [설정 관리 가이드](docs/config_management_guide.md) - application.yml 관리 방법
- [개인정보 처리방침](docs/privacy_policy.md) - 서비스 개인정보 처리방침
- [이용약관](docs/terms_of_service.md) - 서비스 이용약관

## 🤝 기여

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이선스

This project is licensed under the MIT License.

## 👥 팀

Team Yun-Chan

## 📞 문의

프로젝트 관련 문의사항은 GitHub Issues를 이용해주세요.
