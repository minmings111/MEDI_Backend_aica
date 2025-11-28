# 🚀 EC2 배포 완전 가이드 (초보자용)

## 📖 목차
1. [배포란 무엇인가?](#1-배포란-무엇인가)
2. [전체 플로우 개요](#2-전체-플로우-개요)
3. [Phase 1: 도메인 & AWS 설정](#phase-1-도메인--aws-설정)
4. [Phase 2: 로컬에서 이미지 준비](#phase-2-로컬에서-이미지-준비)
5. [Phase 3: EC2 서버 설정](#phase-3-ec2-서버-설정)
6. [Phase 4: HTTPS 설정](#phase-4-https-설정)
7. [Phase 5: 컨테이너 실행](#phase-5-컨테이너-실행)
8. [Phase 6: 배포 확인](#phase-6-배포-확인)
9. [문제 해결](#문제-해결)

---

## 1. 배포란 무엇인가?

### 🏠 집에 비유하면

**개발 환경 (로컬 PC)**
- 집에서 요리 연습하는 것
- `localhost:8080`에서만 접속 가능
- 나만 볼 수 있음

**배포 (EC2 서버)**
- 실제 레스토랑을 열어서 손님을 받는 것
- `https://yourdomain.shop`으로 누구나 접속 가능
- 전 세계 어디서나 접속 가능

### 🎯 배포의 목적

1. **누구나 접속 가능하게**: 인터넷 어디서나 접속
2. **24시간 운영**: 내 PC를 끄지 않아도 계속 작동
3. **안정적인 서비스**: 전용 서버에서 안정적으로 운영

---

## 2. 전체 플로우 개요

```
[로컬 PC]                    [GitHub]                    [EC2 서버]
   │                            │                            │
   │ 1. 코드 작성                │                            │
   │                            │                            │
   │ 2. Docker 이미지 빌드      │                            │
   │    (컨테이너 패키징)         │                            │
   │                            │                            │
   │ 3. GitHub에 이미지 업로드   │                            │
   │ ──────────────────────────>│                            │
   │                            │                            │
   │                            │ 4. EC2에서 이미지 다운로드 │
   │                            │ ──────────────────────────>│
   │                            │                            │
   │                            │ 5. 컨테이너 실행            │
   │                            │                            │
   │                            │ 6. HTTPS 설정               │
   │                            │                            │
   │                            │ 7. 도메인 연결               │
   │                            │                            │
   │                            │ ✅ 배포 완료!               │
   │                            │    https://yourdomain.shop │
```

### 📦 핵심 개념

**Docker 이미지**
- 애플리케이션을 패키징한 것
- 예: 백엔드 코드 + Java + 라이브러리 = 하나의 이미지

**Docker 컨테이너**
- 이미지를 실행한 것
- 예: 이미지를 실행하면 → 컨테이너가 됨

**GitHub Container Registry (GHCR)**
- Docker 이미지를 저장하는 곳
- EC2에서 이미지를 다운로드할 수 있음

---

## Phase 1: 도메인 & AWS 설정

### 🎯 목적
- 인터넷에서 접속할 수 있는 주소(도메인) 준비
- 서버(EC2)와 데이터베이스(RDS) 준비

### 📝 Step 1-1: 도메인 구매

**도메인이란?**
- 인터넷 주소 (예: `google.com`, `naver.com`)
- IP 주소(예: `54.180.123.45`)를 기억하기 쉬운 이름으로 변환

**구매 방법**
1. 가비아(https://www.gabia.com) 접속
2. 원하는 도메인 검색 (예: `medi-demo.shop`)
3. 구매 (약 500원~1000원/년)
4. 결제 완료

**추천 도메인**
- `.shop`: 저렴하고 짧음
- `.com`: 가장 일반적 (비쌈)
- `.net`: 중간 가격

### 📝 Step 1-2: AWS 계정 생성 및 크레딧 확인

**AWS란?**
- 아마존의 클라우드 서비스
- 서버, 데이터베이스 등을 빌려주는 서비스

**계정 생성**
1. https://aws.amazon.com 접속
2. "AWS 계정 만들기" 클릭
3. 이메일, 비밀번호 입력
4. 결제 정보 입력 (크레딧 사용 시 과금 없음)

**$100 크레딧 확인**
- AWS 콘솔 → 우측 상단 계정명 클릭
- "크레딧" 메뉴에서 확인

### 📝 Step 1-3: EC2 인스턴스 생성

**EC2란?**
- 가상 컴퓨터를 빌려주는 서비스
- 우리 애플리케이션을 실행할 서버

**생성 방법**

1. **AWS 콘솔 접속**
   - https://console.aws.amazon.com
   - 로그인

2. **EC2 서비스 선택**
   - 검색창에 "EC2" 입력
   - "EC2" 클릭

3. **인스턴스 시작**
   - "인스턴스 시작" 버튼 클릭

4. **이름 및 태그**
   - 이름: `medi-server` (원하는 이름)

5. **애플리케이션 및 OS 이미지**
   - Ubuntu 선택
   - 버전: Ubuntu Server 22.04 LTS

6. **인스턴스 유형**
   - `t3.large` 선택
   - 2 vCPU, 8GB RAM
   - **중요**: 시연용이면 `t3.medium` (4GB RAM)도 가능

7. **키 페어 (로그인)**
   - "새 키 페어 생성" 클릭
   - 이름: `medi-key`
   - 키 페어 유형: RSA
   - 프라이빗 키 파일 형식: `.pem`
   - "키 페어 생성" 클릭
   - **중요**: `.pem` 파일 다운로드 (다시 받을 수 없음!)
   - 안전한 곳에 보관 (예: `C:\Users\YourName\Downloads\medi-key.pem`)

8. **네트워크 설정**
   - "보안 그룹 편집" 클릭
   - 다음 규칙 추가:
     ```
     SSH (22)     - 내 IP만 허용
     HTTP (80)    - 어디서나 (0.0.0.0/0)
     HTTPS (443)  - 어디서나 (0.0.0.0/0)
     ```
   - "보안 그룹 규칙 추가" 버튼으로 각각 추가

9. **스토리지**
   - 기본값 (8GB) 유지

10. **인스턴스 시작**
    - "인스턴스 시작" 버튼 클릭
    - 잠시 후 "인스턴스 보기" 클릭

11. **퍼블릭 IP 확인**
    - 인스턴스 목록에서 "퍼블릭 IPv4 주소" 확인
    - 예: `54.180.123.45`
    - 이 주소를 메모해두기

### 📝 Step 1-4: RDS 데이터베이스 생성

**RDS란?**
- MySQL 데이터베이스를 빌려주는 서비스
- 애플리케이션 데이터를 저장하는 곳

**생성 방법**

1. **RDS 서비스 선택**
   - AWS 콘솔에서 "RDS" 검색
   - "RDS" 클릭

2. **데이터베이스 생성**
   - "데이터베이스 생성" 버튼 클릭

3. **데이터베이스 생성 방법**
   - "표준 생성" 선택

4. **엔진 옵션**
   - MySQL 선택
   - 버전: MySQL 8.0

5. **템플릿**
   - "프리 티어" 선택 (12개월 무료)

6. **설정**
   - DB 인스턴스 식별자: `medi-db`
   - 마스터 사용자 이름: `admin`
   - 마스터 암호: 강력한 비밀번호 설정 (메모해두기!)
     - 예: `MySecurePassword123!`

7. **인스턴스 구성**
   - db.t3.micro (프리 티어)

8. **스토리지**
   - 기본값 (20GB) 유지

9. **연결**
   - "퍼블릭 액세스": 아니요 (보안)
   - VPC: 기본값
   - 보안 그룹: 새로 생성
     - 이름: `medi-rds-sg`
   - 가용 영역: EC2와 같은 영역 선택

10. **데이터베이스 인증**
    - "비밀번호 인증" 선택

11. **추가 구성**
    - 초기 데이터베이스 이름: `medi`

12. **데이터베이스 생성**
    - "데이터베이스 생성" 버튼 클릭
    - 생성 완료까지 약 5-10분 소요

13. **엔드포인트 확인**
    - 생성 완료 후 "연결 및 보안" 탭
    - "엔드포인트" 주소 확인
    - 예: `medi-db.abc123.us-east-1.rds.amazonaws.com:3306`
    - 이 주소를 메모해두기

### 📝 Step 1-5: RDS 보안 그룹 설정

**목적**
- EC2에서만 RDS에 접속할 수 있도록 설정

**설정 방법**

1. **RDS 보안 그룹 확인**
   - RDS 콘솔 → 데이터베이스 → `medi-db` 클릭
   - "연결 및 보안" 탭
   - "VPC 보안 그룹"에서 보안 그룹 이름 확인
   - 보안 그룹 이름 클릭

2. **인바운드 규칙 편집**
   - "인바운드 규칙" 탭
   - "인바운드 규칙 편집" 클릭
   - "규칙 추가" 클릭
   - 설정:
     ```
     유형: MySQL/Aurora
     프로토콜: TCP
     포트: 3306
     소스: EC2 보안 그룹 선택
     ```
   - "규칙 저장" 클릭

### 📝 Step 1-6: Route 53 도메인 연결

**Route 53이란?**
- AWS의 DNS 서비스
- 도메인을 EC2 IP 주소로 연결

**설정 방법**

1. **호스팅 영역 생성**
   - AWS 콘솔에서 "Route 53" 검색
   - "Route 53" 클릭
   - "호스팅 영역" → "호스팅 영역 생성"

2. **도메인 입력**
   - 도메인 이름: `yourdomain.shop` (구매한 도메인)
   - "호스팅 영역 생성" 클릭

3. **NS 레코드 복사**
   - 생성된 호스팅 영역 클릭
   - "NS" 타입 레코드 4개 확인
   - 예:
     ```
     ns-123.awsdns-45.com
     ns-678.awsdns-90.net
     ns-901.awsdns-23.org
     ns-234.awsdns-56.co.uk
     ```
   - 이 4개를 복사해두기

4. **가비아에 NS 레코드 입력**
   - 가비아 콘솔 접속
   - "도메인 관리" → 구매한 도메인 선택
   - "네임서버 변경" 클릭
   - "사용자 정의 네임서버" 선택
   - 위에서 복사한 4개 NS 레코드 입력
   - "저장" 클릭

5. **A 레코드 생성**
   - Route 53 콘솔로 돌아가기
   - 호스팅 영역 → "레코드 생성"
   - 설정:
     ```
     레코드 이름: (비워두기 또는 www)
     레코드 유형: A
     값: EC2 퍼블릭 IP (예: 54.180.123.45)
     ```
   - "레코드 생성" 클릭

6. **DNS 전파 대기**
   - 약 2-4시간 소요
   - 확인 방법:
     ```bash
     # Windows PowerShell 또는 CMD
     nslookup yourdomain.shop
     # → EC2 IP 주소가 나오면 OK
     ```

---

## Phase 2: 로컬에서 이미지 준비

### 🎯 목적
- 애플리케이션을 Docker 이미지로 패키징
- GitHub Container Registry에 업로드

### 📝 Step 2-1: GitHub Personal Access Token 생성

**목적**
- GitHub에 이미지를 업로드하기 위한 인증

**생성 방법**

1. **GitHub 접속**
   - https://github.com 로그인

2. **Settings 이동**
   - 우측 상단 프로필 클릭 → "Settings"

3. **Developer settings**
   - 좌측 하단 "Developer settings" 클릭

4. **Personal access tokens**
   - "Personal access tokens" → "Tokens (classic)"

5. **Generate new token**
   - "Generate new token (classic)" 클릭
   - Note: `Docker Image Push` (이름은 자유)
   - Expiration: `90 days` (또는 원하는 기간)
   - Scopes: `write:packages` 체크
   - "Generate token" 클릭

6. **토큰 복사**
   - 생성된 토큰 복사 (다시 볼 수 없음!)
   - 안전한 곳에 보관

### 📝 Step 2-2: Docker Desktop 설치 확인

**Docker란?**
- 컨테이너를 만들고 실행하는 도구

**설치 확인**
```bash
# Windows PowerShell 또는 CMD
docker --version
# → Docker version 24.0.0, build ... (버전이 나오면 OK)

docker-compose --version
# → Docker Compose version v2.20.0 (버전이 나오면 OK)
```

**설치되어 있지 않다면**
1. https://www.docker.com/products/docker-desktop 접속
2. "Download for Windows" 클릭
3. 설치 후 재시작

### 📝 Step 2-3: GitHub Container Registry 로그인

**목적**
- GitHub에 이미지를 업로드하기 위한 인증

**명령어**
```bash
# Windows PowerShell 또는 CMD
# YOUR_GITHUB_USERNAME을 실제 사용자명으로 변경
# YOUR_GITHUB_TOKEN을 위에서 생성한 토큰으로 변경

echo YOUR_GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin

# 성공 메시지:
# Login Succeeded
```

### 📝 Step 2-4: 백엔드 이미지 빌드 및 푸시

**이미지 빌드란?**
- 코드를 실행 가능한 패키지로 만드는 것

**명령어**
```bash
# 1. 백엔드 디렉토리로 이동
cd C:\medi\backend

# 2. 이미지 빌드
# YOUR_GITHUB_USERNAME을 실제 사용자명으로 변경
docker build -t ghcr.io/YOUR_GITHUB_USERNAME/medi-backend:latest .

# 빌드 과정:
# - Dockerfile 읽기
# - Java, Gradle 설치
# - 코드 컴파일
# - JAR 파일 생성
# - 최종 이미지 생성
# 약 5-10분 소요

# 3. 이미지 푸시 (GitHub에 업로드)
docker push ghcr.io/YOUR_GITHUB_USERNAME/medi-backend:latest

# 푸시 과정:
# - 이미지를 압축
# - GitHub에 업로드
# 약 2-5분 소요 (이미지 크기에 따라)
```

**확인 방법**
- GitHub 접속 → 프로필 → "Packages" 클릭
- `medi-backend` 패키지 확인

### 📝 Step 2-5: 프론트엔드 이미지 빌드 및 푸시

**프론트엔드 Dockerfile 필요**
- 프론트엔드 디렉토리에 `Dockerfile`이 있어야 함

**명령어**
```bash
# 1. 프론트엔드 디렉토리로 이동
cd C:\medi\frontend

# 2. Dockerfile 확인 (없으면 생성 필요)
# 예시 Dockerfile:
# FROM node:18-alpine
# WORKDIR /app
# COPY package*.json ./
# RUN npm install
# COPY . .
# RUN npm run build
# EXPOSE 3000
# CMD ["npm", "start"]

# 3. 이미지 빌드
docker build -t ghcr.io/YOUR_GITHUB_USERNAME/medi-frontend:latest .

# 4. 이미지 푸시
docker push ghcr.io/YOUR_GITHUB_USERNAME/medi-frontend:latest
```

### 📝 Step 2-6: AI Agent 이미지 빌드 및 푸시

**명령어**
```bash
# 1. AI Agent 디렉토리로 이동
cd C:\medi\AI

# 2. Dockerfile 확인 (없으면 생성 필요)
# 예시 Dockerfile:
# FROM python:3.11-slim
# WORKDIR /app
# COPY requirements.txt .
# RUN pip install --no-cache-dir -r requirements.txt
# COPY . .
# EXPOSE 8000
# CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

# 3. 이미지 빌드
docker build -t ghcr.io/YOUR_GITHUB_USERNAME/medi-ai-agent:latest .

# 4. 이미지 푸시
docker push ghcr.io/YOUR_GITHUB_USERNAME/medi-ai-agent:latest
```

---

## Phase 3: EC2 서버 설정

### 🎯 목적
- EC2 서버에 필요한 소프트웨어 설치
- 프로젝트 파일 다운로드
- 환경 변수 설정

### 📝 Step 3-1: EC2에 SSH 접속

**SSH란?**
- 원격으로 서버에 접속하는 방법

**Windows에서 접속 방법**

1. **PuTTY 사용 (추천)**
   - PuTTY 다운로드: https://www.putty.org
   - 설치 후 실행

2. **PuTTY 설정**
   - Host Name: `ubuntu@54.180.123.45` (EC2 퍼블릭 IP)
   - Port: `22`
   - Connection type: `SSH`
   - 좌측 "SSH" → "Auth" 클릭
   - "Browse" 클릭 → `.pem` 키 파일 선택
   - "Open" 클릭

3. **첫 접속 시 경고**
   - "예" 클릭 (서버 신뢰 확인)

4. **로그인 성공**
   - 터미널 창이 열리면 성공!

**또는 Windows 10/11 PowerShell 사용**
```powershell
# .pem 파일 권한 설정 (한 번만)
icacls C:\Users\YourName\Downloads\medi-key.pem /inheritance:r
icacls C:\Users\YourName\Downloads\medi-key.pem /grant:r "$($env:USERNAME):(R)"

# SSH 접속
ssh -i C:\Users\YourName\Downloads\medi-key.pem ubuntu@54.180.123.45

# 첫 접속 시 "yes" 입력
```

### 📝 Step 3-2: Docker 설치

**명령어 (EC2 서버에서 실행)**
```bash
# 1. 시스템 업데이트
sudo apt-get update

# 2. Docker 설치
sudo apt-get install -y docker.io docker-compose git

# 3. Docker 서비스 시작
sudo systemctl start docker
sudo systemctl enable docker

# 4. 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER

# 5. 그룹 변경 적용 (재접속 또는)
newgrp docker

# 6. Docker 설치 확인
docker --version
# → Docker version 24.0.0, build ... (나오면 OK)

docker-compose --version
# → Docker Compose version v2.20.0 (나오면 OK)
```

### 📝 Step 3-3: 프로젝트 클론

**클론이란?**
- GitHub에서 코드를 다운로드하는 것

**명령어 (EC2 서버에서 실행)**
```bash
# 1. 프로젝트 디렉토리로 이동
cd /opt

# 2. GitHub에서 프로젝트 다운로드
# YOUR_USERNAME을 실제 사용자명으로 변경
git clone https://github.com/YOUR_USERNAME/medi-backend.git

# 3. 프로젝트 디렉토리로 이동
cd medi-backend

# 4. 파일 확인
ls -la
# → docker-compose.prod.yml, Dockerfile 등이 보이면 OK
```

### 📝 Step 3-4: 환경 변수 파일 생성

**환경 변수란?**
- 애플리케이션 설정값 (데이터베이스 주소, 비밀번호 등)

**명령어 (EC2 서버에서 실행)**
```bash
# .env 파일 생성
nano .env
```

**파일 내용 입력**
```bash
# RDS MySQL 연결 정보
RDS_DATASOURCE_URL=jdbc:mysql://medi-db.abc123.us-east-1.rds.amazonaws.com:3306/medi?serverTimezone=Asia/Seoul&characterEncoding=utf8
RDS_DATASOURCE_USERNAME=admin
RDS_DATASOURCE_PASSWORD=MySecurePassword123!

# CORS 허용 도메인
CORS_ALLOWED_ORIGINS=https://yourdomain.shop,https://www.yourdomain.shop

# OAuth 리다이렉트 URI
OAUTH_REDIRECT_URI=https://yourdomain.shop/login/oauth2/code/google

# GitHub Container Registry 정보
GITHUB_USERNAME=YOUR_GITHUB_USERNAME
GITHUB_TOKEN=YOUR_GITHUB_TOKEN

# 도메인
DOMAIN=yourdomain.shop
```

**저장 방법**
- `Ctrl + O` (저장)
- `Enter` (확인)
- `Ctrl + X` (종료)

**파일 확인**
```bash
cat .env
# → 위 내용이 출력되면 OK
```

### 📝 Step 3-5: docker-compose.prod.yml 수정

**목적**
- GitHub 사용자명을 실제 사용자명으로 변경

**명령어**
```bash
# 파일 편집
nano docker-compose.prod.yml
```

**수정할 부분 (3곳)**
```yaml
# Line 25: 프론트엔드 이미지
image: ghcr.io/YOUR_GITHUB_USERNAME/medi-frontend:latest
# → ghcr.io/실제사용자명/medi-frontend:latest

# Line 36: 백엔드 이미지
image: ghcr.io/YOUR_GITHUB_USERNAME/medi-backend:latest
# → ghcr.io/실제사용자명/medi-backend:latest

# Line 72: AI Agent 이미지
image: ghcr.io/YOUR_GITHUB_USERNAME/medi-ai-agent:latest
# → ghcr.io/실제사용자명/medi-ai-agent:latest
```

**저장**
- `Ctrl + O` → `Enter` → `Ctrl + X`

---

## Phase 4: HTTPS 설정

### 🎯 목적
- 보안 연결(HTTPS) 설정
- 브라우저에서 자물쇠 아이콘 표시

### 📝 Step 4-1: Certbot 설치

**Certbot이란?**
- 무료 SSL 인증서를 발급해주는 도구

**명령어 (EC2 서버에서 실행)**
```bash
# Certbot 설치
sudo apt-get install -y certbot

# 설치 확인
certbot --version
# → certbot 2.0.0 (나오면 OK)
```

### 📝 Step 4-2: SSL 인증서 발급

**명령어**
```bash
# SSL 인증서 발급
# yourdomain.shop을 실제 도메인으로 변경
# your@email.com을 실제 이메일로 변경

sudo certbot certonly --standalone \
    -d yourdomain.shop \
    -d www.yourdomain.shop \
    --email your@email.com \
    --agree-tos \
    --non-interactive

# 성공 메시지:
# Congratulations! Your certificate and chain have been saved at:
# /etc/letsencrypt/live/yourdomain.shop/fullchain.pem
```

**인증서 위치 확인**
```bash
# 인증서 파일 확인
sudo ls -la /etc/letsencrypt/live/yourdomain.shop/

# 출력:
# fullchain.pem  (인증서)
# privkey.pem    (개인 키)
```

### 📝 Step 4-3: nginx.conf 파일 수정

**목적**
- 도메인 이름을 실제 도메인으로 변경

**명령어**
```bash
# 파일 편집
nano nginx/nginx.conf
```

**수정할 부분 (4곳)**
```nginx
# Line 37: 서버 이름
server_name yourdomain.shop www.yourdomain.shop;
# → 실제 도메인으로 변경

# Line 41: 인증서 경로
ssl_certificate /etc/letsencrypt/live/yourdomain.shop/fullchain.pem;
# → 실제 도메인으로 변경

# Line 42: 개인 키 경로
ssl_certificate_key /etc/letsencrypt/live/yourdomain.shop/privkey.pem;
# → 실제 도메인으로 변경
```

**저장**
- `Ctrl + O` → `Enter` → `Ctrl + X`

---

## Phase 5: 컨테이너 실행

### 🎯 목적
- Docker 이미지를 다운로드하고 실행
- 모든 서비스가 정상 작동하는지 확인

### 📝 Step 5-1: GitHub Container Registry 로그인

**명령어 (EC2 서버에서 실행)**
```bash
# .env 파일에서 토큰 읽기
export GITHUB_TOKEN=$(grep GITHUB_TOKEN .env | cut -d '=' -f2)
export GITHUB_USERNAME=$(grep GITHUB_USERNAME .env | cut -d '=' -f2)

# GitHub Container Registry 로그인
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin

# 성공 메시지:
# Login Succeeded
```

### 📝 Step 5-2: Docker 이미지 다운로드

**명령어**
```bash
# 최신 이미지 다운로드
docker-compose -f docker-compose.prod.yml pull

# 다운로드 과정:
# - 백엔드 이미지 다운로드
# - 프론트엔드 이미지 다운로드
# - AI Agent 이미지 다운로드
# - Nginx, Redis 이미지 다운로드
# 약 5-10분 소요 (이미지 크기에 따라)
```

**다운로드 확인**
```bash
# 다운로드된 이미지 확인
docker images

# 출력 예시:
# ghcr.io/username/medi-backend    latest    abc123...    2 hours ago    500MB
# ghcr.io/username/medi-frontend   latest    def456...    2 hours ago    200MB
# ...
```

### 📝 Step 5-3: 컨테이너 시작

**명령어**
```bash
# 컨테이너 시작 (백그라운드 실행)
docker-compose -f docker-compose.prod.yml up -d

# -d 옵션: 백그라운드 실행 (터미널을 계속 사용 가능)
```

**컨테이너 상태 확인**
```bash
# 실행 중인 컨테이너 확인
docker-compose -f docker-compose.prod.yml ps

# 출력 예시:
# NAME              STATUS          PORTS
# medi-nginx        Up 10 seconds  0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
# medi-backend      Up 10 seconds  8080/tcp
# medi-frontend     Up 10 seconds  3000/tcp
# medi-ai-agent     Up 10 seconds  8000/tcp
# medi-redis        Up 10 seconds  6379/tcp

# 모든 컨테이너가 "Up" 상태면 OK
```

### 📝 Step 5-4: 로그 확인

**목적**
- 애플리케이션이 정상적으로 시작되었는지 확인

**명령어**
```bash
# 전체 로그 확인
docker-compose -f docker-compose.prod.yml logs

# 특정 서비스 로그 확인
docker-compose -f docker-compose.prod.yml logs backend
docker-compose -f docker-compose.prod.yml logs frontend
docker-compose -f docker-compose.prod.yml logs nginx

# 실시간 로그 확인 (종료: Ctrl+C)
docker-compose -f docker-compose.prod.yml logs -f backend
```

**정상 로그 예시 (백엔드)**
```
medi-backend  |   .   ____          _            __ _ _
medi-backend  |  /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
medi-backend  | ( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
medi-backend  |  \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
medi-backend  |   '  |____| .__|_| |_|_| |_\__, | / / / /
medi-backend  |  =========|_|==============|___/=/_/_/_/
medi-backend  |  :: Spring Boot ::                (v3.5.6)
medi-backend  | Started BackendApplication in 15.234 seconds
```

**오류가 있다면**
- 로그를 확인하여 문제 파악
- 필요시 컨테이너 재시작:
  ```bash
  docker-compose -f docker-compose.prod.yml restart backend
  ```

---

## Phase 6: 배포 확인

### 🎯 목적
- 배포가 성공적으로 완료되었는지 확인
- 모든 기능이 정상 작동하는지 테스트

### 📝 Step 6-1: HTTPS 접속 확인

**브라우저에서 확인**
1. 브라우저 열기 (Chrome, Edge 등)
2. 주소창에 입력: `https://yourdomain.shop`
3. 확인 사항:
   - ✅ 자물쇠 아이콘 표시 (HTTPS)
   - ✅ 페이지가 정상적으로 로드됨
   - ✅ 오류 메시지 없음

**명령어로 확인 (EC2 서버에서)**
```bash
# HTTPS 접속 테스트
curl -I https://yourdomain.shop

# 정상 응답:
# HTTP/2 200
# server: nginx/1.25.0
# ...
```

### 📝 Step 6-2: Health Check 확인

**명령어**
```bash
# Health check 엔드포인트 확인
curl https://yourdomain.shop/health

# 정상 응답:
# healthy

# 백엔드 Health check
curl https://yourdomain.shop/api/actuator/health

# 정상 응답:
# {"status":"UP"}
```

### 📝 Step 6-3: API 테스트

**명령어**
```bash
# API 엔드포인트 테스트
curl https://yourdomain.shop/api/actuator/health

# 정상 응답:
# {"status":"UP"}
```

### 📝 Step 6-4: Google OAuth 설정 확인

**Google Cloud Console 설정**
1. https://console.cloud.google.com 접속
2. "API 및 서비스" → "사용자 인증 정보"
3. OAuth 2.0 클라이언트 ID 선택
4. "승인된 리디렉션 URI"에 추가:
   ```
   https://yourdomain.shop/login/oauth2/code/google
   https://yourdomain.shop/api/youtube/oauth/callback
   ```
5. "저장" 클릭

**테스트**
1. 브라우저에서 `https://yourdomain.shop/login` 접속
2. "Google로 로그인" 버튼 클릭
3. Google 로그인 화면이 나타나면 OK

### 📝 Step 6-5: 데이터베이스 연결 확인

**RDS 연결 테스트 (EC2 서버에서)**
```bash
# MySQL 클라이언트 설치
sudo apt-get install -y mysql-client

# RDS에 접속 테스트
# RDS 엔드포인트와 비밀번호를 .env에서 확인
mysql -h medi-db.abc123.us-east-1.rds.amazonaws.com -u admin -p

# 비밀번호 입력 후:
mysql> SHOW DATABASES;
# → medi 데이터베이스가 보이면 OK

mysql> USE medi;
mysql> SHOW TABLES;
# → 테이블 목록이 보이면 OK

mysql> EXIT;
```

---

## 문제 해결

### 🔴 문제 1: SSH 접속 실패

**증상**
```
Permission denied (publickey)
```

**해결 방법**
1. `.pem` 파일 경로 확인
2. 파일 권한 확인 (Windows):
   ```powershell
   icacls C:\path\to\medi-key.pem
   ```
3. 사용자명 확인: `ubuntu` (Ubuntu 이미지인 경우)

### 🔴 문제 2: Docker 이미지 다운로드 실패

**증상**
```
Error response from daemon: unauthorized
```

**해결 방법**
1. GitHub Container Registry 로그인 확인:
   ```bash
   docker login ghcr.io -u YOUR_USERNAME
   ```
2. 토큰 확인 (만료되었는지)
3. 이미지 이름 확인 (대소문자 구분)

### 🔴 문제 3: 컨테이너가 시작되지 않음

**증상**
```
medi-backend  Exit 1
```

**해결 방법**
1. 로그 확인:
   ```bash
   docker-compose -f docker-compose.prod.yml logs backend
   ```
2. 환경 변수 확인:
   ```bash
   cat .env
   ```
3. 데이터베이스 연결 확인 (RDS 보안 그룹)

### 🔴 문제 4: HTTPS 접속 불가

**증상**
```
This site can't be reached
```

**해결 방법**
1. DNS 전파 확인:
   ```bash
   nslookup yourdomain.shop
   ```
2. EC2 보안 그룹 확인 (443 포트 허용)
3. SSL 인증서 확인:
   ```bash
   sudo ls -la /etc/letsencrypt/live/yourdomain.shop/
   ```

### 🔴 문제 5: 데이터베이스 연결 실패

**증상**
```
Communications link failure
```

**해결 방법**
1. RDS 보안 그룹 확인 (EC2 보안 그룹 허용)
2. RDS 엔드포인트 확인 (.env 파일)
3. RDS가 실행 중인지 확인 (AWS 콘솔)

---

## 📊 최종 체크리스트

배포 완료 후 확인:

- [ ] `https://yourdomain.shop` 접속 → 자물쇠 아이콘 확인
- [ ] `https://yourdomain.shop/health` → "healthy" 응답
- [ ] `https://yourdomain.shop/api/actuator/health` → `{"status":"UP"}`
- [ ] Google OAuth 로그인 테스트 성공
- [ ] 데이터베이스 연결 확인
- [ ] 모바일에서도 접속 확인

---

## 🎉 배포 완료!

축하합니다! 이제 전 세계 어디서나 `https://yourdomain.shop`으로 접속할 수 있습니다!

### 다음 단계
- 시연용 테스트 계정 준비
- 샘플 데이터 준비
- 발표 자료 준비

---

## 💡 추가 팁

### 컨테이너 재시작
```bash
# 특정 서비스 재시작
docker-compose -f docker-compose.prod.yml restart backend

# 모든 서비스 재시작
docker-compose -f docker-compose.prod.yml restart
```

### 로그 실시간 확인
```bash
# 모든 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f

# 특정 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f backend
```

### 컨테이너 중지
```bash
# 모든 컨테이너 중지
docker-compose -f docker-compose.prod.yml down

# 중지 후 볼륨도 삭제
docker-compose -f docker-compose.prod.yml down -v
```

### SSL 인증서 자동 갱신
```bash
# Certbot 자동 갱신 설정
sudo systemctl enable certbot.timer
sudo systemctl start certbot.timer

# 수동 갱신
sudo certbot renew
```

---

**이제 배포에 대한 모든 것을 이해하셨습니다! 🚀**

---

## 📋 배포 전 최종 체크리스트 (시연 1주일 전)

### ⏱️ 각 단계별 예상 시간 확인

| Phase | 작업 | 예상 시간 | 선택사항 |
|-------|------|---------|--------|
| **Phase 1** | 도메인 & AWS 설정 | 2-3시간 | DNS 전파 2시간 포함 |
| **Phase 2** | 로컬 이미지 준비 | 1-2시간 | 네트워크 속도에 따라 |
| **Phase 3** | EC2 초기 설정 | 30분 | Docker 설치 포함 |
| **Phase 4** | HTTPS 설정 | 30분 | Certbot 인증서 발급 |
| **Phase 5** | 컨테이너 실행 | 30분 | 이미지 다운로드 포함 |
| **Phase 6** | 배포 확인 | 30분 | 테스트 |
| **총합** | - | **5-7시간** | DNS 전파 시간 제외 |

**권장**: 시연 3-4일 전 배포 시작

---

## 🚨 자주 실수하는 부분

### 실수 1: GitHub Token 만료

**문제**
- Phase 2에서 토큰 생성 후 복사 시 즉시 사용하지 않음
- 토큰을 다시 볼 수 없어서 새로 생성해야 함

**해결**
```bash
# Phase 2에서:
# Token 생성 후 복사 시 즉시 사용해야 함
# 다시 보려면 새로 생성해야 함!

# 확인 방법:
# GitHub Settings → Developer settings → Personal access tokens
# → 생성 날짜와 만료일 확인
```

### 실수 2: EC2 보안 그룹 미설정

**문제**
- 보안 그룹 설정을 빠뜨리면 배포 후에도 접속 불가

**해결**
```bash
# Phase 1의 보안 그룹 설정 확인:
# ✅ SSH(22): 관리자 IP만
# ✅ HTTP(80): 0.0.0.0/0
# ✅ HTTPS(443): 0.0.0.0/0

# 없으면 배포 후에도 접속 불가!
```

**확인 방법**
1. AWS 콘솔 → EC2 → 보안 그룹
2. 인바운드 규칙 확인
3. 위 3개 규칙이 모두 있는지 확인

### 실수 3: RDS 보안 그룹 미설정

**문제**
- RDS 보안 그룹에 EC2 보안 그룹을 허용하지 않으면 백엔드가 DB 접속 못함

**해결**
```bash
# Phase 1 Step 1-5에서:
# RDS 보안 그룹의 "인바운드 규칙"에
# EC2 보안 그룹을 허용했는가?

# 없으면 백엔드가 DB 접속 못함!
```

**확인 방법**
1. AWS 콘솔 → RDS → 데이터베이스 → 보안 그룹
2. 인바운드 규칙 확인
3. EC2 보안 그룹이 MySQL(3306) 포트로 허용되어 있는지 확인

### 실수 4: docker-compose.prod.yml USERNAME 미수정

**문제**
- `YOUR_GITHUB_USERNAME`을 실제 사용자명으로 변경하지 않으면 이미지를 찾을 수 없음

**해결**
```bash
# Phase 3 Step 3-5에서:
# YOUR_GITHUB_USERNAME 3개 부분 변경했는가?

grep "YOUR_GITHUB_USERNAME" docker-compose.prod.yml
# → 0개면 OK, 1개 이상이면 수정 필요!
```

**수정 위치**
- Line 25: 프론트엔드 이미지
- Line 36: 백엔드 이미지
- Line 72: AI Agent 이미지

### 실수 5: nginx.conf 도메인 미수정

**문제**
- `yourdomain.shop`을 실제 도메인으로 변경하지 않으면 SSL 인증서 오류

**해결**
```bash
# Phase 4 Step 4-3에서:
# yourdomain.com/shop을 실제 도메인으로 변경했는가?

grep "yourdomain" nginx/nginx.conf
# → 4개 모두 실제 도메인이어야 함
```

**수정 위치**
- Line 37: `server_name`
- Line 41: `ssl_certificate` 경로
- Line 42: `ssl_certificate_key` 경로

---

## 🔍 배포 직전 확인사항 (시연 당일 아침)

### EC2 서버에서 실행할 명령어

```bash
# 1. 모든 컨테이너 실행 중?
docker-compose -f docker-compose.prod.yml ps
# → 모두 "Up" 상태 확인

# 2. 환경 변수 올바른가?
cat .env | head -20
# → RDS 정보, 도메인, GitHub 정보 확인

# 3. Nginx 설정 올바른가?
cat nginx/nginx.conf | grep "yourdomain"
# → 4줄 모두 실제 도메인 확인

# 4. SSL 인증서 유효한가?
sudo certbot certificates
# → Valid (만료일 확인)

# 5. 로그에 에러 있는가?
docker-compose -f docker-compose.prod.yml logs 2>&1 | grep -i error | head -5
# → 중대한 에러 없으면 OK

# 6. API 응답하는가?
curl -I https://yourdomain.shop/api/health
# → HTTP/2 200 확인

# 모두 확인되면 시연 준비 완료! 🚀
```

### 각 확인사항 상세 설명

**1. 컨테이너 상태 확인**
```bash
docker-compose -f docker-compose.prod.yml ps

# 정상 출력:
# NAME              STATUS          PORTS
# medi-nginx        Up 2 hours      0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
# medi-backend      Up 2 hours      8080/tcp
# medi-frontend     Up 2 hours      3000/tcp
# medi-ai-agent     Up 2 hours      8000/tcp
# medi-redis        Up 2 hours      6379/tcp

# 문제가 있다면:
# - "Exit 1" 또는 "Restarting" 상태 → 로그 확인 필요
```

**2. 환경 변수 확인**
```bash
cat .env

# 확인할 항목:
# - RDS_DATASOURCE_URL: RDS 엔드포인트가 올바른가?
# - RDS_DATASOURCE_PASSWORD: 비밀번호가 올바른가?
# - CORS_ALLOWED_ORIGINS: 도메인이 올바른가?
# - OAUTH_REDIRECT_URI: 도메인이 올바른가?
```

**3. Nginx 설정 확인**
```bash
cat nginx/nginx.conf | grep "yourdomain"

# 출력이 있다면:
# → 아직 실제 도메인으로 변경하지 않은 것
# → 수정 필요!
```

**4. SSL 인증서 확인**
```bash
sudo certbot certificates

# 정상 출력:
# Certificate Name: yourdomain.shop
#   Domains: yourdomain.shop www.yourdomain.shop
#   Expiry Date: 2024-XX-XX (90일 후)
#   Certificate Path: /etc/letsencrypt/live/yourdomain.shop/fullchain.pem
```

**5. 에러 로그 확인**
```bash
# 백엔드 에러 확인
docker-compose -f docker-compose.prod.yml logs backend 2>&1 | grep -i error | head -10

# 프론트엔드 에러 확인
docker-compose -f docker-compose.prod.yml logs frontend 2>&1 | grep -i error | head -10

# Nginx 에러 확인
docker-compose -f docker-compose.prod.yml logs nginx 2>&1 | grep -i error | head -10
```

**6. API 응답 확인**
```bash
# Health check
curl -I https://yourdomain.shop/health
# → HTTP/2 200

# 백엔드 Health check
curl -I https://yourdomain.shop/api/actuator/health
# → HTTP/2 200

# 실제 API 테스트
curl https://yourdomain.shop/api/actuator/health
# → {"status":"UP"}
```

---

## 🎯 시연 중 트러블슈팅 (긴급 상황)

### 상황 1: 페이지가 안 열림

**증상**
- 브라우저에서 `https://yourdomain.shop` 접속 시 연결 실패
- "This site can't be reached" 오류

**즉시 실행할 명령어 (EC2 서버에서)**
```bash
# 1. 컨테이너 재시작
docker-compose -f docker-compose.prod.yml restart nginx

# 2. 로그 확인
docker-compose -f docker-compose.prod.yml logs -f nginx | head -50

# 3. 모든 컨테이너 재시작
docker-compose -f docker-compose.prod.yml restart

# 4. 컨테이너 상태 확인
docker-compose -f docker-compose.prod.yml ps
```

**추가 확인사항**
```bash
# Nginx 설정 파일 문법 확인
docker-compose -f docker-compose.prod.yml exec nginx nginx -t

# SSL 인증서 확인
sudo ls -la /etc/letsencrypt/live/yourdomain.shop/

# 포트 확인
sudo netstat -tlnp | grep -E '80|443'
```

### 상황 2: Google OAuth 로그인 안 됨

**증상**
- "Google로 로그인" 버튼 클릭 시 오류
- 리다이렉트 URI 오류 메시지

**해결 방법**

**1. Google Cloud Console 확인**
```
1. https://console.cloud.google.com 접속
2. "API 및 서비스" → "사용자 인증 정보"
3. OAuth 2.0 클라이언트 ID 선택
4. "승인된 리디렉션 URI" 확인:
   ✅ https://yourdomain.shop/login/oauth2/code/google
   ✅ https://yourdomain.shop/api/youtube/oauth/callback
5. 없으면 추가하고 저장
```

**2. .env 파일 확인**
```bash
# EC2 서버에서
grep "OAUTH_REDIRECT_URI" .env

# 올바른 형식:
# OAUTH_REDIRECT_URI=https://yourdomain.shop/login/oauth2/code/google

# 잘못된 형식:
# OAUTH_REDIRECT_URI=http://yourdomain.shop/... (HTTP는 안됨!)
# OAUTH_REDIRECT_URI=https://localhost/... (로컬은 안됨!)
```

**3. 백엔드 로그 확인**
```bash
docker-compose -f docker-compose.prod.yml logs backend | grep -i oauth

# 오류 메시지 확인:
# - "redirect_uri_mismatch" → Google Console URI 확인
# - "invalid_client" → 클라이언트 ID/Secret 확인
```

**4. 환경 변수 재적용**
```bash
# .env 파일 수정 후 컨테이너 재시작
docker-compose -f docker-compose.prod.yml restart backend
```

### 상황 3: 댓글 분석이 안 됨

**증상**
- 댓글 분석 버튼 클릭 시 오류
- "분석 중..." 상태에서 멈춤

**해결 방법**

**1. Redis 확인**
```bash
# Redis 컨테이너 접속
docker-compose -f docker-compose.prod.yml exec redis redis-cli

# Redis 내부에서:
PING
# 응답: PONG

# 연결 확인
INFO server
# → Redis 버전 정보 출력되면 OK

EXIT
```

**2. AI Agent 로그 확인**
```bash
docker-compose -f docker-compose.prod.yml logs ai-agent | head -50

# 확인할 내용:
# - Python 오류
# - 모델 로딩 오류
# - 메모리 부족 오류
```

**3. 백엔드 로그 확인**
```bash
docker-compose -f docker-compose.prod.yml logs backend | grep -i "analysis\|error\|exception"

# 확인할 내용:
# - AI Agent 연결 오류
# - Redis 연결 오류
# - 타임아웃 오류
```

**4. 네트워크 연결 확인**
```bash
# 백엔드에서 AI Agent 접속 테스트
docker-compose -f docker-compose.prod.yml exec backend curl http://ai-agent:8000/health

# 응답이 오면 OK
```

### 상황 4: 데이터베이스 연결 실패

**증상**
- 로그인은 되지만 데이터가 안 보임
- "Database connection failed" 오류

**해결 방법**
```bash
# 1. RDS 보안 그룹 확인
# AWS 콘솔 → RDS → 보안 그룹 → 인바운드 규칙
# → EC2 보안 그룹이 MySQL(3306) 허용되어 있는지 확인

# 2. RDS 엔드포인트 확인
grep "RDS_DATASOURCE_URL" .env

# 3. 직접 연결 테스트
mysql -h medi-db.abc123.us-east-1.rds.amazonaws.com -u admin -p

# 4. 백엔드 로그 확인
docker-compose -f docker-compose.prod.yml logs backend | grep -i "datasource\|mysql\|connection"
```

---

## 📱 시연 당일 준비물

### PC/노트북

- [ ] 배포된 서버에 SSH 접속 가능한 상태
  - `.pem` 키 파일 준비
  - PuTTY 또는 SSH 클라이언트 설치
- [ ] Docker, Git 설치 (비상용 로컬 빌드)
- [ ] VS Code 또는 텍스트 에디터 (빠른 수정용)
- [ ] 브라우저 (Chrome, Edge 등)
  - 시연용 계정 로그인 상태
  - 캐시 삭제 (Ctrl+Shift+Delete)

### 모바일

- [ ] WiFi 접속 테스트
  - 시연 장소 WiFi 정보 확인
  - 접속 테스트
- [ ] HTTPS 접속 테스트
  - `https://yourdomain.shop` 접속 확인
  - 자물쇠 아이콘 확인
- [ ] Google 로그인 테스트
  - 실제 YouTube 계정으로 로그인
  - OAuth 동의 화면 확인

### 샘플 데이터

- [ ] YouTube 영상 URL 2-3개 준비
  - 댓글이 많은 영상
  - 다양한 댓글 유형 포함
- [ ] 테스트 댓글 샘플 준비
  - 긍정적 댓글
  - 부정적 댓글
  - 중립적 댓글
- [ ] 예상되는 분석 결과 미리 테스트
  - 각 영상별 분석 결과 확인
  - 예상 소요 시간 확인

### 발표 자료

- [ ] 배포 아키텍처 설명
  - 5개 컨테이너 구조
  - AWS 인프라 구성
- [ ] 기술 스택 설명
  - Docker, AWS, Nginx 등
- [ ] 데모 시나리오 준비
  - 단계별 시연 순서
  - 예상 질문과 답변

---

## 💡 시연 팁

### 1. HTTPS 보안 강조

**발표 스크립트 예시**
```
"프로덕션 환경에서는 반드시 HTTPS를 사용합니다.
Let's Encrypt로 무료 SSL 인증서를 자동 발급받습니다.
[브라우저 자물쇠 아이콘 보여주기]

이를 통해 사용자 데이터와 통신 내용이 암호화되어
안전하게 전송됩니다."
```

### 2. 아키텍처 설명

**발표 스크립트 예시**
```
"5개 서비스가 Docker 컨테이너로 격리되어 있습니다:

1. Nginx: 외부 요청을 받아 적절한 서비스로 라우팅
2. Frontend: 사용자 인터페이스 (Next.js)
3. Backend: 비즈니스 로직 (Spring Boot)
4. AI Agent: 댓글 분석 (FastAPI)
5. Redis: 실시간 큐 처리

각 서비스는 독립적으로 실행되므로
한 서비스에 문제가 생겨도 다른 서비스는 정상 작동합니다."
```

### 3. 빠른 응답 강조

**발표 스크립트 예시**
```
"AWS 클라우드에서 실행되므로 낮은 지연시간과
높은 가용성을 보장합니다.

[API 응답 시간 보여주기: 100-300ms]

전 세계 어디서나 빠르게 접속할 수 있습니다."
```

### 4. 실시간 성능 모니터링

**시연 중 실시간 로그 보기**
```bash
# 별도 터미널 창에서 실행
ssh -i key.pem ubuntu@EC2_IP

# 실시간 로그 확인
docker-compose -f docker-compose.prod.yml logs -f backend

# 크리에이터가 댓글 분석하는 동안 실시간으로 로그 표시
# → "실시간으로 서버에서 처리되는 과정을 볼 수 있습니다"
```

---

## 🆘 최후의 수단

### 배포 롤백 (완전 초기화)

**모든 컨테이너 중지 및 삭제**
```bash
# EC2 서버에서 실행

# 1. 모든 컨테이너 중지
docker-compose -f docker-compose.prod.yml down

# 2. 이미지 삭제 (필요시)
docker rmi -f $(docker images -q)

# 3. 처음부터 다시 시작
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

### 긴급 로컬 빌드

**만약 이미지가 손상되었다면 로컬에서 다시 빌드/푸시**

```bash
# 로컬 PC에서 실행 (EC2에 SSH 접속하지 않아도 됨)

# 1. GitHub Container Registry 로그인
echo $GITHUB_TOKEN | docker login ghcr.io -u $GITHUB_USERNAME --password-stdin

# 2. 백엔드 이미지 재빌드 및 푸시
cd C:\medi\backend
docker build -t ghcr.io/USERNAME/medi-backend:latest .
docker push ghcr.io/USERNAME/medi-backend:latest

# 3. 프론트엔드 이미지 재빌드 및 푸시
cd C:\medi\frontend
docker build -t ghcr.io/USERNAME/medi-frontend:latest .
docker push ghcr.io/USERNAME/medi-frontend:latest

# 4. AI Agent 이미지 재빌드 및 푸시
cd C:\medi\AI
docker build -t ghcr.io/USERNAME/medi-ai-agent:latest .
docker push ghcr.io/USERNAME/medi-ai-agent:latest

# 5. EC2에서 최신 이미지 다운로드
# (EC2 서버에서)
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

### 빠른 재시작 스크립트

**EC2 서버에 저장해두면 유용**
```bash
# EC2 서버에서 실행
cat > restart.sh << 'EOF'
#!/bin/bash
echo "🔄 컨테이너 재시작 중..."
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d
echo "✅ 재시작 완료!"
docker-compose -f docker-compose.prod.yml ps
EOF

chmod +x restart.sh

# 사용법:
./restart.sh
```

---

## ✨ 최종 점검표

| 항목 | 확인 | 담당자 | 비고 |
|------|------|--------|------|
| Domain 구매 | ☐ | 본인 | yourdomain.shop |
| EC2 생성 | ☐ | 본인 | t3.large, 퍼블릭 IP 메모 |
| RDS 생성 | ☐ | 본인 | MySQL, 엔드포인트 메모 |
| 보안 그룹 설정 | ☐ | 본인 | EC2, RDS 모두 확인 |
| Docker 이미지 빌드/푸시 | ☐ | 본인 | 3개 서비스 모두 |
| EC2 SSH 접속 | ☐ | 본인 | 접속 가능 |
| 환경 변수 설정 | ☐ | 본인 | .env 파일 확인 |
| HTTPS 설정 | ☐ | 본인 | 인증서 발급됨 |
| 컨테이너 실행 | ☐ | 본인 | 모두 Up 상태 |
| 도메인 접속 | ☐ | 본인 | 자물쇠 표시 |
| Google OAuth | ☐ | 본인 | 로그인 성공 |
| 기본 기능 테스트 | ☐ | 본인 | 댓글 분석 작동 |
| 모바일 테스트 | ☐ | 본인 | WiFi에서 접속 |
| 시연 데이터 준비 | ☐ | 본인 | YouTube 영상 URL |
| 발표 자료 준비 | ☐ | 본인 | 아키텍처 설명 |

**모두 ✅ 되면 시연 성공률 99%! 🎉**

---

## 🎓 정리

이 **"EC2 배포 완전 가이드"**는:

- ✅ 초보자를 위해 매우 상세함
- ✅ 단계별로 따라하기 쉬움
- ✅ 트러블슈팅이 충실함
- ✅ 시연 준비에 최적화됨
- ✅ 긴급 상황 대응 방법 포함
- ✅ 최종 체크리스트 제공

**당신은 이 가이드를 충실히 따르면 반드시 성공합니다! 💪**

**화이팅! 시연 성공하세요! 🚀**

