# 전체 시스템 테스트 가이드

## 📋 사전 준비사항

### 필수 설치 항목
- **Java 17** (JDK)
- **MySQL 8.0+**
- **Redis 6.0+**
- **Node.js 18+** (프론트엔드용)
- **Git**

### 확인 사항
- MySQL 서버 실행 중
- Redis 서버 실행 중
- 포트 충돌 없음 (MySQL: 3306, Redis: 6379, 백엔드: 8080, 프론트: 3000/5173)

---

## 🚀 실행 순서 (중요!)

### 1단계: MySQL 데이터베이스 준비

#### 1-1. MySQL 서버 실행 확인
```bash
# Windows (서비스 확인)
services.msc
# MySQL 서비스가 "실행 중"인지 확인

# 또는 명령어로 확인
mysql --version
```

#### 1-2. 데이터베이스 및 사용자 생성
```bash
# MySQL 접속
mysql -u root -p

# 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS medi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 사용자 생성 및 권한 부여 (application.yml의 설정에 맞춤)
CREATE USER IF NOT EXISTS 'admin'@'localhost' IDENTIFIED BY '1234';
GRANT ALL PRIVILEGES ON medi.* TO 'admin'@'localhost';
FLUSH PRIVILEGES;

# 확인
SHOW DATABASES;
USE medi;
```

#### 1-3. 테이블 생성 (마이그레이션 실행)
```bash
# 프로젝트 루트에서
# MyBatis나 Flyway가 있다면 자동 생성되거나, 수동으로 SQL 실행
# migration_add_deleted_at_to_youtube_channels.sql 등 실행
```

---

### 2단계: Redis 서버 실행

#### 2-1. Redis 서버 실행 확인
```bash
# Windows (Redis 설치 경로로 이동)
cd C:\Program Files\Redis
redis-server.exe

# 또는 서비스로 실행 중인지 확인
# Redis가 설치되어 있으면 자동으로 서비스로 실행될 수 있음
```

#### 2-2. Redis 연결 테스트
```bash
# 새 터미널에서
redis-cli

# 연결 확인
PING
# 응답: PONG

# 포트 확인
CONFIG GET port
# 응답: 6379

# 종료
exit
```

#### 2-3. Redis 데이터 초기화 (선택사항)
```bash
# 기존 테스트 데이터 삭제 (주의: 프로덕션에서는 사용 금지!)
redis-cli
FLUSHALL
exit
```

---

### 3단계: 백엔드 (Spring Boot) 실행

#### 3-1. 프로젝트 디렉토리로 이동
```bash
cd C:\medi\backend
```

#### 3-2. 의존성 설치 확인
```bash
# Gradle Wrapper로 빌드
.\gradlew.bat build

# 또는 IntelliJ IDEA / VS Code에서 자동으로 빌드됨
```

#### 3-3. application.yml 설정 확인
```yaml
# src/main/resources/application.yml 확인
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/medi
    username: admin
    password: 1234
  data:
    redis:
      host: localhost
      port: 6379
server:
  port: 8080
```

#### 3-4. 백엔드 실행

**방법 1: IDE에서 실행 (IntelliJ IDEA / VS Code)**
1. `BackendApplication.java` 파일 열기
2. `main` 메서드에서 우클릭 → "Run 'BackendApplication'"
3. 또는 상단의 실행 버튼 클릭

**방법 2: 터미널에서 실행**
```bash
# Windows
.\gradlew.bat bootRun

# 또는
java -jar build\libs\backend-0.0.1-SNAPSHOT.jar
```

#### 3-5. 백엔드 실행 확인
```bash
# 로그 확인
# 다음과 같은 메시지가 보이면 성공:
# "Started BackendApplication in X.XXX seconds"

# 브라우저에서 확인
http://localhost:8080/actuator/health
# 응답: {"status":"UP"}

# 또는
http://localhost:8080/swagger-ui.html
# Swagger UI가 열리면 성공
```

#### 3-6. 백엔드 로그 확인 포인트
```
✅ 정상 실행 시:
- "Started BackendApplication"
- "Tomcat started on port(s): 8080"
- "Redis connection established"
- "MySQL connection established"

❌ 에러 발생 시:
- "Failed to connect to Redis" → Redis 서버 확인
- "Failed to connect to MySQL" → MySQL 서버 및 DB 확인
- "Port 8080 already in use" → 포트 충돌 해결
```

---

### 4단계: 프론트엔드 실행

#### 4-1. 프론트엔드 디렉토리 확인
```bash
# 프론트엔드 프로젝트 위치 확인
# 보통 backend와 같은 레벨에 frontend 폴더가 있음
cd C:\medi\frontend
# 또는
cd ..\frontend
```

#### 4-2. 의존성 설치
```bash
# npm 사용
npm install

# 또는 yarn 사용
yarn install

# 또는 pnpm 사용
pnpm install
```

#### 4-3. 환경 변수 설정 확인
```bash
# .env 파일 확인
# 백엔드 API URL이 설정되어 있는지 확인
VITE_API_URL=http://localhost:8080
# 또는
REACT_APP_API_URL=http://localhost:8080
```

#### 4-4. 프론트엔드 실행

**Vite 사용 시:**
```bash
npm run dev
# 또는
yarn dev
# 포트: 5173 (기본값)
```

**Create React App 사용 시:**
```bash
npm start
# 포트: 3000 (기본값)
```

#### 4-5. 프론트엔드 접속 확인
```
http://localhost:5173
# 또는
http://localhost:3000
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 전체 플로우 테스트 (초기 동기화)

#### 1. 시스템 준비
- ✅ MySQL 실행 중
- ✅ Redis 실행 중
- ✅ 백엔드 실행 중 (포트 8080)
- ✅ 프론트엔드 실행 중 (포트 5173/3000)

#### 2. OAuth 로그인 및 채널 연결
1. 프론트엔드에서 로그인
2. YouTube 채널 연결 버튼 클릭
3. Google OAuth 인증 완료
4. 채널 등록 완료

#### 3. 백엔드 로그 확인
```log
최초 채널 등록 감지 - Redis 초기 동기화 시작: userId=1
Redis 동기화 시작: userId=1
YouTube API를 통해 조회된 채널 개수: userId=1, 채널=1개
각 채널별 조회수 상위 20개 영상 조회 완료: 1개 채널
각 채널별 조회수 상위 20개 영상의 댓글 동기화 완료: userId=1, 총 댓글 수=xxx
Redis 동기화 완료: userId=1, 채널=1개, 비디오=20개, 댓글=xxx개
Redis 초기 동기화 완료: userId=1
```

#### 4. Redis 데이터 확인
```bash
redis-cli

# 채널 Top20 비디오 ID 확인
SMEMBERS channel:{channel_id}:top20_video_ids

# 비디오 메타데이터 확인
GET video:{video_id}:meta:json

# 초기 동기화 댓글 확인
GET video:{video_id}:comments:init

# 증분 동기화 댓글 확인 (Hash 타입)
HGETALL video:{video_id}:comments

# 커서 확인
GET video:{video_id}:last_sync_time

# TTL 확인
TTL channel:{channel_id}:top20_video_ids
TTL video:{video_id}:meta:json
```

---

### 시나리오 2: 증분 동기화 테스트

#### 1. API 호출로 영상 동기화
```bash
# Postman 또는 curl 사용
POST http://localhost:8080/api/youtube/videos/sync
Headers:
  Authorization: Bearer {token}
  Content-Type: application/json
Body:
{
  "videoIds": ["video_id_1", "video_id_2"]
}
```

#### 2. 백엔드 로그 확인
```log
MySQL 영상 동기화 완료 - Redis 증분 동기화 시작: userId=1, channelId=xxx, videoCount=2
증분 Redis 동기화 시작: userId=1, 비디오 개수=2
비디오 메타데이터 동기화 완료: userId=1, 비디오=2개
비디오 댓글 동기화 완료: userId=1, 비디오=2개, 총 댓글 수=xxx
증분 Redis 동기화 완료: userId=1, 비디오=2개, 댓글=xxx개
```

#### 3. Redis 데이터 확인
```bash
redis-cli

# 증분 동기화 댓글 확인 (Hash 타입)
HGETALL video:{video_id}:comments

# 특정 댓글 확인
HGET video:{video_id}:comments {comment_id}

# 커서 업데이트 확인
GET video:{video_id}:last_sync_time
```

---

### 시나리오 3: 댓글 증분 동기화 로직 테스트 (임계값 5)

#### 1. 테스트 영상 선택
- 새 댓글이 산발적으로 있는 영상
- 빈 페이지가 중간에 섞여 있는 영상

#### 2. 동기화 실행
```bash
POST http://localhost:8080/api/youtube/videos/sync
```

#### 3. 로그에서 확인할 내용
```log
# 연속 빈 페이지 발생 시
커서 이전 댓글 페이지가 연속 5회 발생하여 조회를 중단합니다. videoId=xxx

# 페이지 제한 도달 시
페이지 한도(50)에 도달하여 조회를 중단합니다. videoId=xxx

# 새 댓글 저장 시
영상 xxx의 새 댓글 150개 저장 완료
```

#### 4. Redis에서 확인
```bash
# 조회된 댓글 수 확인
HLEN video:{video_id}:comments

# 댓글 ID 목록 확인
HKEYS video:{video_id}:comments
```

---

## 🔍 문제 해결 가이드

### 문제 1: Redis 연결 실패
```
에러: Unable to connect to Redis
```

**해결 방법:**
1. Redis 서버 실행 확인
   ```bash
   redis-cli PING
   # PONG이 나와야 함
   ```
2. application.yml 확인
   ```yaml
   spring:
     data:
       redis:
         host: localhost
         port: 6379
   ```
3. 방화벽 확인 (Windows)
   - Windows Defender 방화벽에서 Redis 포트 허용

---

### 문제 2: MySQL 연결 실패
```
에러: Communications link failure
```

**해결 방법:**
1. MySQL 서버 실행 확인
   ```bash
   mysql -u admin -p
   ```
2. 데이터베이스 존재 확인
   ```sql
   SHOW DATABASES;
   USE medi;
   ```
3. application.yml 확인
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/medi
       username: admin
       password: 1234
   ```

---

### 문제 3: 포트 충돌
```
에러: Port 8080 is already in use
```

**해결 방법:**
1. 포트 사용 중인 프로세스 확인
   ```bash
   # Windows
   netstat -ano | findstr :8080
   ```
2. 프로세스 종료
   ```bash
   taskkill /PID {프로세스ID} /F
   ```
3. 또는 application.yml에서 포트 변경
   ```yaml
   server:
     port: 8081
   ```

---

### 문제 4: 프론트엔드에서 백엔드 API 호출 실패
```
에러: CORS policy blocked
```

**해결 방법:**
1. 백엔드 CORS 설정 확인
   ```yaml
   cors:
     allowed-origins: http://localhost:3000,http://localhost:5173
   ```
2. SecurityConfig에서 CORS 설정 확인
3. 프론트엔드 API URL 확인

---

## 📊 모니터링 체크리스트

### 백엔드 로그 모니터링
- [ ] Redis 연결 성공 로그
- [ ] MySQL 연결 성공 로그
- [ ] YouTube API 호출 성공 로그
- [ ] 댓글 동기화 완료 로그
- [ ] 연속 빈 페이지 중단 로그 (임계값 5)
- [ ] 페이지 제한 도달 로그 (50페이지)

### Redis 데이터 모니터링
- [ ] 채널 Top20 비디오 ID Set 생성
- [ ] 비디오 메타데이터 저장
- [ ] 초기 동기화 댓글 저장 (String 타입)
- [ ] 증분 동기화 댓글 저장 (Hash 타입)
- [ ] 커서 시간 저장
- [ ] TTL 설정 확인 (3일/30일)

### API 할당량 모니터링
- [ ] 영상당 평균 API 호출 수
- [ ] 빈 페이지 발생 횟수
- [ ] 연속 빈 페이지 패턴
- [ ] 일일 할당량 소모량

---

## 🎯 빠른 테스트 체크리스트

### 시스템 준비 (5분)
- [ ] MySQL 실행 및 DB 생성
- [ ] Redis 실행 및 연결 확인
- [ ] 백엔드 실행 (포트 8080)
- [ ] 프론트엔드 실행 (포트 5173/3000)

### 기본 기능 테스트 (10분)
- [ ] 로그인/회원가입
- [ ] YouTube 채널 연결
- [ ] 초기 동기화 완료 확인
- [ ] Redis 데이터 확인

### 고급 기능 테스트 (15분)
- [ ] 영상 수동 동기화
- [ ] 증분 동기화 확인
- [ ] 댓글 임계값 로직 확인 (연속 5회 빈 페이지)
- [ ] API 할당량 모니터링

---

## 📝 참고 자료

- [Redis 테스트 가이드](./REDIS_TEST_GUIDE.md)
- [API 문서](./API_DOCUMENTATION.md)
- YouTube API 할당량: https://developers.google.com/youtube/v3/getting-started

