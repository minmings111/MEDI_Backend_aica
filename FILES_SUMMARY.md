# 📦 생성된 파일 목록 및 설명

## ✅ Git에 커밋해야 하는 파일 (팀원과 공유)

### 배포 설정 파일
| 파일명 | 용도 | 설명 |
|--------|------|------|
| `docker-compose.prod.yml` | 프로덕션 배포 | Nginx + Backend + Redis 설정 (RDS 연동 가능) |
| `nginx/conf.d/default.conf` | Nginx 설정 | 리버스 프록시, SSL, 라우팅 설정 |
| `.env.example` | 환경 변수 템플릿 | 실제 값 없이 구조만 제공 |

### 자동화 스크립트
| 파일명 | 용도 | 실행 시점 |
|--------|------|----------|
| `setup.sh` | 초기 설정 | 로컬 개발 환경 구성 시 |
| `deploy.sh` | 배포 자동화 | 개발 환경 배포 시 |
| `setup-ssl.sh` | SSL 인증서 발급 | 프로덕션 서버에서 최초 1회 |
| `renew-ssl.sh` | SSL 인증서 갱신 | Cron 작업으로 자동 실행 |

### 설정 템플릿
| 파일명 | 용도 | 설명 |
|--------|------|------|
| `src/main/resources/application.yml.example` | 설정 파일 템플릿 | 전체 설정 구조 예시 |
| `src/main/resources/application-prod.yml` | 프로덕션 설정 | 환경 변수 참조 방식 |

### 수정된 기존 파일
| 파일명 | 변경 내용 |
|--------|----------|
| `Dockerfile` | yt-dlp 설치 추가, JVM 메모리 1.5GB로 증가 |
| `docker-compose.yml` | 환경 변수 주입 방식으로 변경 |
| `README.md` | 배포 가이드 추가 |

---

## ❌ Git에 커밋하면 안 되는 파일 (.gitignore에 포함됨)

| 파일명 | 이유 | 생성 위치 |
|--------|------|----------|
| `.env` | 실제 비밀번호 포함 | 로컬 & 서버 |
| `src/main/resources/application.yml` | 민감 정보 포함 | 로컬 & 서버 |
| `certbot/` | SSL 인증서 | 서버에서 자동 생성 |
| `logs/` | 애플리케이션 로그 | 실행 시 자동 생성 |

---

## 📋 파일 사용 순서

### 1️⃣ 로컬 개발 시
```bash
# 초기 설정
bash setup.sh

# 개발 환경 실행
docker-compose up -d
```

### 2️⃣ 프로덕션 배포 시
```bash
# EC2 서버에서
git clone https://github.com/team-yun-chan/backend.git
cd backend

# 환경 변수 설정
cp .env.example .env
nano .env  # 실제 값 입력

# Nginx 설정 업데이트
nano nginx/conf.d/default.conf  # 도메인 변경

# 배포
docker compose -f docker-compose.prod.yml up -d

# SSL 인증서 발급
bash setup-ssl.sh
```

---

## 🔄 파일 관계도

```
로컬 개발
  ├── .env.example → .env (복사 후 수정)
  ├── application.yml.example → application.yml (복사 후 수정)
  └── docker-compose.yml (개발용)

프로덕션 배포
  ├── .env.example → .env (서버에서 생성)
  ├── docker-compose.prod.yml (프로덕션용)
  ├── nginx/conf.d/default.conf (도메인 수정)
  ├── setup-ssl.sh (SSL 발급)
  └── renew-ssl.sh (SSL 갱신)
```

---

## 🎯 지금 해야 할 일

### 1. Git 커밋 (로컬에서)
```bash
cd c:\medi\backend
git add .
git commit -m "feat: Add production deployment configuration"
git push origin BE_MIN
```

### 2. AWS 인프라 준비
- EC2 인스턴스 생성 (t3.large)
- RDS MySQL 생성 (선택)
- Route 53 도메인 연결

### 3. 서버 배포
- EC2에 SSH 접속
- Docker 설치
- 코드 클론 및 배포
- SSL 인증서 발급

자세한 내용은 **배포 초보자 가이드**를 참고하세요!
