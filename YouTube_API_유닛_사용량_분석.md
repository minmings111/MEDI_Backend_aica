# YouTube Data API v3 유닛 사용량 분석

## 📊 YouTube Data API v3 유닛 비용 (표준)

| API 엔드포인트 | 유닛 비용 | 비고 |
|--------------|---------|------|
| `channels.list` | **1 unit** | 채널 정보 조회 |
| `videos.list` | **1 unit** | 비디오 상세 정보 (50개씩 배치) |
| `commentThreads.list` | **1 unit** | 댓글 조회 (100개씩 페이지당) |
| `search.list` | **100 units** ⚠️ | **매우 비싸다!** 검색 API |
| `playlistItems.list` | **1 unit** | 플레이리스트 영상 목록 (50개씩 페이지당) |
| `captions.list` | **50 units** | 자막 목록 조회 |

---

## 🔑 1. OAuth2 Client ID/Secret (application.yml 26-33) 사용 부분

### 사용 위치 및 호출 시점

#### ✅ **반드시 OAuth 토큰만 사용 (API 키로 대체 불가능)**

1. **`channels.list` with `setMine(true)`** 
   - **파일**: `YoutubeService.syncChannels()` (라인 146-147)
   - **호출 시점**: 
     - 사용자가 채널 연결 시 (OAuth 콜백)
     - 채널 동기화 수동 요청 시
   - **유닛**: **1 unit** per call
   - **특징**: ⚠️ **API 키로는 대체 불가능** (setMine(true)는 OAuth 토큰 필수)

#### ✅ **API 키 우선, 실패 시 OAuth 토큰 fallback**

2. **`playlistItems.list`** 
   - **파일**: `YoutubeService.syncVideos()` → `fetchPlaylistSnapshotsWithApiKey()` 또는 `fetchPlaylistSnapshotsWithOAuth()`
   - **호출 시점**: 
     - 스케줄러 매 시간마다 실행
     - 사용자가 채널 등록 시 (초기 동기화)
   - **유닛**: **1 unit** per page (50개씩)
   - **fallback**: API 키 실패 시 OAuth 토큰 사용

3. **`videos.list`** 
   - **파일**: `YoutubeService.syncVideos()` → `fetchVideoStatisticsWithApiKey()` 또는 `fetchVideoStatisticsWithOAuth()`
   - **파일**: `YoutubeVideoServiceImpl.getTop20VideosByChannel()` → `fetchVideoDetails()`
   - **호출 시점**: 
     - 스케줄러 매 시간마다 실행
     - 초기 동기화 시
   - **유닛**: **1 unit** per batch (50개씩 배치)
   - **fallback**: API 키 실패 시 OAuth 토큰 사용

4. **`commentThreads.list`** 
   - **파일**: `YoutubeCommentServiceImpl.fetchAndSaveCommentsIncremental()` 또는 `fetchAndSaveCommentsSnapshot()`
   - **호출 시점**: 
     - 스케줄러 매 시간마다 실행 (증분 동기화)
     - 초기 동기화 시
   - **유닛**: **1 unit** per page (100개씩)
   - **fallback**: API 키 실패 시 OAuth 토큰 사용

5. **`search.list`** 
   - **파일**: `YoutubeVideoServiceImpl.getTop20VideosByChannel()` → `fetchChannelVideosWithApiKey()` 또는 `fetchChannelVideos()`
   - **호출 시점**: 
     - 초기 동기화 시 (채널당 1회)
   - **유닛**: **100 units** per call ⚠️ **매우 비싸다!**
   - **fallback**: API 키 실패 시 OAuth 토큰 사용

6. **`captions.list`** 
   - **파일**: `YoutubeTranscriptServiceImpl.saveTranscriptToRedisWithClient()`
   - **호출 시점**: 
     - 초기 동기화 시 (상위 20개 영상의 자막)
   - **유닛**: **50 units** per video
   - **fallback**: API 키 실패 시 OAuth 토큰 사용

---

## 🔑 2. API 키들 (application.yml 112-129) 사용 부분

### 사용 위치 및 호출 시점

#### **모든 조회 API에서 API 키 우선 사용, 실패 시 OAuth 토큰 fallback**

1. **`YoutubeDataApiClient.fetchPlaylistItems()`** 
   - **실제 API**: `playlistItems.list`
   - **호출 위치**: `YoutubeService.syncVideos()` → `fetchPlaylistSnapshotsWithApiKey()`
   - **유닛**: **1 unit** per page

2. **`YoutubeDataApiClient.fetchVideoDetails()` / `fetchVideoStatistics()`** 
   - **실제 API**: `videos.list`
   - **호출 위치**: 
     - `YoutubeService.syncVideos()` → `fetchVideoStatisticsWithApiKey()`
     - `YoutubeVideoServiceImpl.syncVideoMetadata()` → `fetchVideoDetailsWithApiKey()`
   - **유닛**: **1 unit** per batch (50개씩)

3. **`YoutubeDataApiClient.fetchCommentThreads()`** 
   - **실제 API**: `commentThreads.list`
   - **호출 위치**: `YoutubeCommentServiceImpl.fetchAndSaveCommentsIncremental()` / `fetchAndSaveCommentsSnapshot()`
   - **유닛**: **1 unit** per page (100개씩)

4. **`YoutubeDataApiClient.fetchSearch()`** 
   - **실제 API**: `search.list`
   - **호출 위치**: `YoutubeVideoServiceImpl.getTop20VideosByChannel()` → `fetchChannelVideosWithApiKey()`
   - **유닛**: **100 units** per call ⚠️ **매우 비싸다!**

5. **`YoutubeDataApiClient.fetchCaptions()`** 
   - **실제 API**: `captions.list`
   - **호출 위치**: `YoutubeTranscriptServiceImpl.saveTranscriptToRedisWithClient()`
   - **유닛**: **50 units** per video

---

## 📈 스케줄러 한 번 실행 시 유닛 사용량 계산

### 가정
- **채널 수**: 1개
- **새 영상 수**: 50개 (max-videos-per-hour: 50)
- **기존 영상 수**: 5개 (초기 동기화 영상)
- **댓글 수**: 평균 1000개/비디오 (10페이지)
- **스케줄러 주기**: 1시간마다 (`@Scheduled(cron = "0 0 * * * *")`)

### 스케줄러 실행 흐름 (`YoutubeSyncScheduler.syncAllChannelsDaily()`)

#### 1단계: 새 영상 동기화 (`YoutubeService.syncVideos()`)
- **`playlistItems.list`**: 50개 영상 조회 = **1 unit** (50개씩 페이지당)
- **`videos.list`**: 50개 영상 상세 정보 = **1 unit** (50개씩 배치)
- **합계**: **2 units**

#### 2단계: 기존 영상의 새 댓글 동기화 (`YoutubeRedisSyncService.syncIncrementalToRedis()`)
- **영상 수**: 5개 (초기 동기화 영상) + 50개 (새 영상) = **55개**
- **`commentThreads.list`**: 55개 비디오 * 평균 10페이지 = **550 units** ⚠️
  - 각 비디오마다 새 댓글이 많으면 페이지 수가 증가할 수 있음
- **합계**: **550 units**

#### **총 스케줄러 한 번 실행 시**: **약 552 units** per 채널

---

## 🚀 초기 동기화 시 유닛 사용량 계산

### 가정
- **채널 수**: 1개
- **상위 영상 수**: 20개 (getTop20VideosByChannel)
- **댓글 수**: 초기 100개/비디오 (1페이지)
- **자막**: 20개 영상 모두 자막 존재

### 초기 동기화 흐름 (`YoutubeRedisSyncService.syncToRedis()`)

#### 1단계: 채널 동기화 (`YoutubeService.syncChannels()`)
- **`channels.list`**: 1 unit (setMine=true, **OAuth 토큰만 가능**) ⚠️

#### 2단계: 상위 20개 영상 조회 (`YoutubeVideoServiceImpl.getTop20VideosByChannel()`)
- **`search.list`**: 1회 = **100 units** ⚠️ **매우 비싸다!**
- **`videos.list`**: 20개 영상 = **1 unit** (50개씩 배치)

#### 3단계: 댓글 동기화 (`YoutubeCommentServiceImpl.syncTop20VideoComments()`)
- **`commentThreads.list`**: 20개 비디오 * 1페이지 = **20 units** (초기 100개 제한)

#### 4단계: 자막 저장 (`YoutubeTranscriptServiceImpl.saveTranscriptsToRedis()`)
- **`captions.list`**: 20개 영상 * 50 units = **1000 units** ⚠️ **매우 비싸다!**

#### **총 초기 동기화 시**: **약 1,122 units** per 채널

---

## 📊 비교 정리

| 구분 | OAuth2 Client ID (26-33) | API 키들 (112-129) |
|------|-------------------------|-------------------|
| **사용 목적** | 사용자 인증 + API 호출 | API 호출만 |
| **대체 가능 여부** | `channels.list` (setMine=true)는 **대체 불가능** ⚠️ | 대부분 조회 API에서 사용 |
| **유닛 공유** | ✅ **같은 프로젝트 쿼터 공유** | ✅ **같은 프로젝트 쿼터 공유** |
| **기본 할당량** | 프로젝트 전체 일일 **10,000 units** (무료) | 프로젝트 전체 일일 **10,000 units** (무료) |
| **스케줄러 실행** | fallback으로 사용 (API 키 실패 시) | 우선 사용 |
| **초기 동기화** | `channels.list` (1 unit) 필수 | 나머지 모두 |

---

## ❌ 현재 오류 원인 분석

### 오류 발생 위치
**`YoutubeService.syncChannels()`** (라인 146-148)
```java
YouTube.Channels.List req = yt.channels().list(...);
req.setMine(true);  // ⚠️ OAuth 토큰 필수
resp = req.execute();  // ← 여기서 403 quotaExceeded 발생
```

### 오류 원인

1. **프로젝트 전체 쿼터 소진**
   - `channels.list`는 **OAuth 토큰만 사용 가능** (API 키로 대체 불가능)
   - 프로젝트 전체 일일 할당량 (10,000 units)이 모두 소진됨
   - 다른 계정으로 로그인해도 **같은 프로젝트 쿼터를 공유**하므로 같은 오류 발생

2. **트랜잭션 롤백**
   - `@Transactional`로 선언된 `syncChannels()` 메서드
   - 예외 발생 시 **트랜잭션이 자동으로 롤백**됨
   - 하지만 현재 코드는 **쿼터 초과 시 기존 DB 채널 반환** 로직이 있음 (라인 178-185)
   - **문제**: 기존 채널이 없으면 예외를 던져서 **트랜잭션이 롤백**됨

3. **트랜잭션 롤백의 영향**
   - 라인 106에서 `findByUserIdIncludingDeleted()` 조회는 성공
   - 하지만 API 호출 실패 시 **예외 발생 → 트랜잭션 롤백**
   - 채널 저장 (`channelMapper.upsert()`, 라인 222)이 **실행되지 않음**
   - 따라서 DB에 채널이 저장되지 않음

### 해결 방법

1. **즉시 해결**: 24시간 대기 (일일 할당량 자동 갱신)
2. **장기 해결**: Google Cloud Console에서 할당량 증가 요청
3. **코드 개선**: 이미 구현되어 있음 (쿼터 초과 시 기존 DB 채널 반환)

---

## 💡 권장 사항

1. **유닛 사용량 최적화**
   - `search.list` (100 units) 사용 최소화
   - `captions.list` (50 units/video) 선택적 사용
   - 댓글 조회 페이지 수 제한

2. **스케줄러 최적화**
   - 현재: 1시간마다 실행
   - 제안: 채널당 댓글 동기화 주기 조정

3. **모니터링**
   - Google Cloud Console에서 할당량 사용량 모니터링
   - 일일 할당량 소진 전 알림 설정

---

## 📌 결론

- **OAuth2 Client ID**와 **API 키들** 모두 **같은 프로젝트 쿼터를 공유**
- `channels.list` (setMine=true)는 **OAuth 토큰만 사용 가능**하여 대체 불가능
- **초기 동기화가 가장 많은 유닛 사용** (약 1,122 units/채널)
- **스케줄러 실행도 상당한 유닛 사용** (약 552 units/채널)
- 현재 오류는 **프로젝트 전체 쿼터 소진** + **트랜잭션 롤백** 때문

