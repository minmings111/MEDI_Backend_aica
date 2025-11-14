# Redis 동기화 테스트 가이드

## 📋 테스트 시나리오

### 1. 초기 동기화 테스트 (OAuth 콜백 후)

**테스트 방법:**
1. 애플리케이션 실행
2. OAuth 연결 (YouTube 채널 등록)
3. 로그 확인
4. Redis 데이터 확인

**예상 로그:**
```
최초 채널 등록 감지 - Redis 초기 동기화 시작: userId=xxx
Redis 동기화 시작: userId=xxx
YouTube API를 통해 조회된 채널 개수: userId=xxx, 채널=1개
Redis 동기화 완료: userId=xxx, 채널=1개, 비디오=20개, 댓글=xxx개
Redis 초기 동기화 완료: userId=xxx
```

**Redis 확인 명령어:**
```bash
# Redis CLI 접속
redis-cli

# 채널 Top20 비디오 ID Set 확인
SMEMBERS channel:{channel_id}:top20_video_ids

# 비디오 메타데이터 확인
GET video:{video_id}:meta:json

# 댓글 확인
GET video:{video_id}:comments:json

# 모든 키 확인
KEYS channel:*
KEYS video:*
```

---

### 2. 증분 동기화 테스트 (영상 수동 동기화)

**테스트 방법:**
1. API 호출: `POST /api/youtube/videos/sync`
2. 로그 확인
3. Redis 데이터 확인

**예상 로그:**
```
MySQL 영상 동기화 완료 - Redis 증분 동기화 시작: userId=xxx, channelId=xxx, videoCount=10
증분 Redis 동기화 시작: userId=xxx, 비디오 개수=10
비디오 메타데이터 동기화 완료: userId=xxx, 비디오=10개
증분 Redis 동기화 완료: userId=xxx, 비디오=10개, 댓글=xxx개
Redis 증분 동기화 완료: userId=xxx, videoCount=10
```

---

### 3. 스케줄러 테스트

**테스트 방법:**
1. 애플리케이션 실행
2. 오후 4시 대기 (또는 스케줄러 수동 실행)
3. 로그 확인
4. Redis 데이터 확인

---

## 🔍 Redis 데이터 확인 방법

### 방법 1: Redis CLI 사용

```bash
# Redis 서버 접속
redis-cli

# 채널별 Top20 비디오 ID 목록 확인
SMEMBERS channel:UCm5suozTR8bN1o_5xRf9JrQ:top20_video_ids

# 비디오 메타데이터 확인
GET video:td7kfwpTDcA:meta:json

# 댓글 확인
GET video:td7kfwpTDcA:comments:json

# TTL 확인 (만료 시간)
TTL channel:UCm5suozTR8bN1o_5xRf9JrQ:top20_video_ids
TTL video:td7kfwpTDcA:meta:json

# 모든 채널 키 확인
KEYS channel:*

# 모든 비디오 키 확인
KEYS video:*
```

### 방법 2: API로 확인 (만약 있다면)

Redis 조회 API가 있다면 사용 가능

---

## ✅ 검증 체크리스트

### 초기 동기화 검증
- [ ] OAuth 연결 후 로그에 "Redis 초기 동기화 시작" 메시지 확인
- [ ] Redis에 `channel:{channel_id}:top20_video_ids` Set 생성 확인
- [ ] Redis에 `video:{video_id}:meta:json` 생성 확인 (최소 1개)
- [ ] Redis에 `video:{video_id}:comments:json` 생성 확인 (최소 1개)
- [ ] TTL이 3일로 설정되어 있는지 확인

### 증분 동기화 검증
- [ ] 영상 동기화 후 로그에 "Redis 증분 동기화 시작" 메시지 확인
- [ ] 새로 동기화된 영상의 `video:{video_id}:meta:json` 생성 확인
- [ ] 새로 동기화된 영상의 `video:{video_id}:comments:json` 생성 확인

---

## 🐛 문제 해결

### Redis 연결 실패 시
- Redis 서버가 실행 중인지 확인
- `application.properties` 또는 `application.yml`에서 Redis 연결 설정 확인

### 데이터가 저장되지 않는 경우
- 로그에서 에러 메시지 확인
- `youtubeRedisSyncService`가 null인지 확인 (`@Autowired(required = false)`)
- YouTube API 호출이 성공했는지 확인

