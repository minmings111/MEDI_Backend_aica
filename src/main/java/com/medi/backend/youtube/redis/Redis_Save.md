# Redis 저장 로직 가이드

> 초보 개발자를 위한 YouTube 데이터 Redis 저장 로직 설명서

## 📋 목차

1. [전체 개요](#전체-개요)
2. [두 가지 동기화 방식](#두-가지-동기화-방식)
3. [초기 동기화 상세 설명](#초기-동기화-상세-설명)
4. [증분 동기화 상세 설명](#증분-동기화-상세-설명)
5. [코드 사용 예시](#코드-사용-예시)

---

## 전체 개요

### Redis에 저장되는 데이터

YouTube 데이터를 Redis에 저장하는 이유는 **AI 서버가 빠르게 데이터를 가져올 수 있도록** 하기 위함입니다.

Redis에 저장되는 데이터는 총 3가지입니다:

1. **채널별 Top20 비디오 ID 목록** (Set 타입)
   - Key: `channel:{channel_id}:top20_video_ids`
   - 예시: `channel:UCBA9XaL5wCdHnC5EmEzwrqw:top20_video_ids`

2. **비디오 메타데이터** (String 타입, JSON 형식)
   - Key: `video:{video_id}:meta:json`
   - 예시: `video:td7kfwpTDcA:meta:json`

3. **비디오 댓글** (String 타입, JSON 배열 형식)
   - Key: `video:{video_id}:comments:json`
   - 예시: `video:td7kfwpTDcA:comments:json`

### 전체 흐름도

```
사용자 로그인
    ↓
MySQL에 채널/비디오 저장 (1단계)
    ↓
Redis 동기화 시작
    ↓
┌─────────────────────────────────────┐
│  초기 동기화 (처음 한 번)           │
│  - Top20 비디오 ID Set 저장          │
│  - 기본 메타데이터 저장 (4개 필드)   │
│  - 댓글 100개 제한 저장              │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  증분 동기화 (새 영상 추가 시)       │
│  - 기본 메타데이터 저장 (4개 필드)   │
│  - 댓글 전체 저장 (제한 없음)        │
└─────────────────────────────────────┘
```

---

## 두 가지 동기화 방식

### 1. 초기 동기화 (Initial Sync)

**언제 사용하나요?**
- 사용자가 처음 로그인했을 때
- MySQL에 채널/비디오가 저장된 직후

**무엇을 저장하나요?**
- 각 채널의 조회수 상위 20개 비디오 ID
- 비디오 기본 메타데이터 (4개 필드만)
  - `video_id`
  - `video_title`
  - `channel_id`
  - `video_tags`
- 댓글 최대 100개만

**왜 100개만?**
- 초기 동기화는 빠르게 완료되어야 하므로
- 대량의 댓글을 모두 가져오면 시간이 오래 걸림

### 2. 증분 동기화 (Incremental Sync)

**언제 사용하나요?**
- 사용자가 새로운 영상을 업로드했을 때
- 기존에 저장되지 않은 영상이 추가되었을 때

**무엇을 저장하나요?**
- 비디오 기본 메타데이터 (4개 필드만)
  - `video_id`
  - `video_title`
  - `channel_id`
  - `video_tags`
- 댓글 전체 (제한 없음, 전체 메타데이터)

**왜 전체 댓글을 저장하나요?**
- 새로 추가된 영상은 상세 분석이 필요하므로
- 모든 댓글과 댓글의 전체 메타데이터가 필요함
- 비디오 메타데이터는 초기 동기화와 동일하게 기본 4개 필드만 저장

---

## 초기 동기화 상세 설명

### 실행 순서

```
1. MySQL 저장 완료 (다른 서비스에서 처리)
   ↓
2. YoutubeRedisSyncService.syncToRedis(userId) 호출
   ↓
3. YouTube API를 통해 사용자의 채널 목록 조회 (DB 독립적)
   ↓
4. 각 채널별로 조회수 상위 20개 비디오 찾기
   ↓
5. Redis에 저장
   ├─ 채널별 Top20 비디오 ID Set 저장
   ├─ 비디오 기본 메타데이터 저장
   └─ 비디오 댓글 저장 (100개 제한)
```

### 단계별 상세 설명

#### 1단계: 채널별 Top20 비디오 찾기

**파일**: `YoutubeVideoServiceImpl.java`  
**메서드**: `getTop20VideosByChannel()`

**동작 과정**:

```java
// 1. 각 채널의 모든 비디오 목록 조회
YouTube Search API 호출 → 비디오 ID 목록 얻기

// 2. 비디오 상세 정보 조회 (조회수 포함)
YouTube Videos API 호출 (50개씩 배치) → 비디오 상세 정보 얻기

// 3. 조회수 기준으로 정렬하여 상위 20개 선택
비디오 리스트 정렬 → 상위 20개만 선택

// 4. Redis에 저장
채널별 Top20 비디오 ID Set 저장
비디오 기본 메타데이터 저장
```

**코드 위치**:
- `YoutubeVideoServiceImpl.java` 54-147줄

#### 2단계: 비디오 메타데이터 저장

**파일**: `YoutubeVideoServiceImpl.java`  
**메서드**: `saveVideoMetadataToRedis()`

**저장 형식**:
```json
{
  "channel_id": "UCBA9XaL5wCdHnC5EmEzwrqw",
  "video_id": "td7kfwpTDcA",
  "video_title": "시작보다 어려운 끝 [츠예나, 이경민]",
  "video_tags": ["김민교", "츠예나", "이경민", "산본포차"]
}
```

**Redis 저장**:
- Key: `video:td7kfwpTDcA:meta:json`
- Value: 위 JSON 문자열
- TTL: 3일

**코드 위치**:
- `YoutubeVideoServiceImpl.java` 288-316줄

#### 3단계: 비디오 댓글 저장

**파일**: `YoutubeCommentServiceImpl.java`  
**메서드**: `syncTop20VideoComments()`

**동작 과정**:

```java
// 1. 각 비디오마다 댓글 조회
YouTube CommentThreads API 호출 → 댓글 목록 얻기

// 2. 댓글 100개 제한 적용
댓글 리스트에서 100개만 선택

// 3. JSON 배열로 변환하여 Redis에 저장
List<YoutubeComment> → JSON 배열 문자열 → Redis 저장
```

**저장 형식**:
```json
[
  {
    "comment_id": "UgyQnoD1JS_mILywmB94AaABAg",
    "text_original": "'이경민' 이라는 사람 다시본다...",
    "author_name": "@user123",
    "like_count": 105,
    "published_at": "2021-04-18T10:05:00Z"
  },
  {
    "comment_id": "UgwJ3MDVhziGCfGTVoV4AaABAg",
    "text_original": "경민이 밖에 나가있을 때...",
    "author_name": "@user456",
    "like_count": 230,
    "published_at": "2021-04-18T10:10:00Z"
  }
]
```

**Redis 저장**:
- Key: `video:td7kfwpTDcA:comments:json`
- Value: 위 JSON 배열 문자열
- TTL: 3일

**코드 위치**:
- `YoutubeCommentServiceImpl.java` 56-150줄
- `YoutubeCommentServiceImpl.java` 254-356줄 (fetchAndSaveComments)

### API 호출 최소화 전략

**문제**: 각 비디오마다 API를 호출하면 너무 많은 호출이 발생함

**해결책**:
1. 비디오 상세 정보는 50개씩 묶어서 한 번에 조회
2. 이미 조회한 비디오 정보를 재사용하여 중복 호출 방지

**예시**:
```
❌ 나쁜 방법: 비디오 20개 → API 20번 호출
✅ 좋은 방법: 비디오 20개 → API 1번 호출 (50개씩 배치)
```

---

## 증분 동기화 상세 설명

### 실행 순서

```
1. 새로 추가된 비디오 ID 리스트 받기
   ↓
2. YoutubeRedisSyncService.syncIncrementalToRedis() 호출
   ↓
3. 비디오 기본 메타데이터 조회 및 저장 (4개 필드)
   ↓
4. 비디오 댓글 전체 조회 및 저장 (제한 없음, 전체 메타데이터)
```

### 단계별 상세 설명

#### 1단계: 비디오 기본 메타데이터 저장

**파일**: `YoutubeVideoServiceImpl.java`  
**메서드**: `syncVideoMetadata()`

**동작 과정**:

```java
// 1. 비디오 ID 리스트를 50개씩 묶어서 한 번에 조회
YouTube Videos API 호출 (배치 처리) → 비디오 상세 정보 얻기

// 2. 기본 메타데이터 DTO로 변환
Video 객체 → RedisYoutubeVideo DTO (기본 4개 필드만)

// 3. Redis에 저장
기본 메타데이터 JSON → Redis 저장
```

**저장 형식** (기본 4개 필드만):
```json
{
  "channel_id": "UCBA9XaL5wCdHnC5EmEzwrqw",
  "video_id": "td7kfwpTDcA",
  "video_title": "시작보다 어려운 끝 [츠예나, 이경민]",
  "video_tags": ["김민교", "츠예나", "이경민", "산본포차"]
}
```

**코드 위치**:
- `YoutubeVideoServiceImpl.java` 230-284줄

#### 2단계: 비디오 댓글 전체 저장

**파일**: `YoutubeCommentServiceImpl.java`  
**메서드**: `syncVideoComments()`

**동작 과정**:

```java
// 1. 각 비디오마다 댓글 조회
YouTube CommentThreads API 호출 → 댓글 목록 얻기

// 2. 댓글 제한 없음 (전부 가져오기)
모든 댓글을 리스트에 추가

// 3. JSON 배열로 변환하여 Redis에 저장
List<YoutubeComment> → JSON 배열 문자열 → Redis 저장
```

**차이점**:
- 초기 동기화: 댓글 100개 제한
- 증분 동기화: 댓글 제한 없음 (전부 가져오기)

**코드 위치**:
- `YoutubeCommentServiceImpl.java` 152-223줄

### SyncOptions의 역할

**파일**: `SyncOptions.java`

**역할**: 초기 동기화와 증분 동기화를 구분하는 옵션 설정

**초기 동기화 옵션**:
```java
SyncOptions.initialSync()
// - maxCommentCount: 100
// - includeFullMetadata: false
```

**증분 동기화 옵션**:
```java
SyncOptions.incrementalSync()
// - maxCommentCount: null (제한 없음)
// - includeFullMetadata: true
```

**사용 예시**:
```java
// 초기 동기화: 댓글 100개 제한
commentService.syncTop20VideoComments(userId, videos, SyncOptions.initialSync());

// 증분 동기화: 댓글 제한 없음
commentService.syncVideoComments(userId, videoIds, SyncOptions.incrementalSync());
```

---

## 코드 사용 예시

### 초기 동기화 사용법

```java
@Autowired
private YoutubeRedisSyncService youtubeRedisSyncService;

// 초기 동기화 실행
// 채널 ID는 YouTube API를 통해 자동으로 조회됨 (DB 독립적)
RedisSyncResult result = youtubeRedisSyncService.syncToRedis(userId);

// 결과 확인
if (result.isSuccess()) {
    System.out.println("동기화 성공!");
    System.out.println("채널: " + result.getChannelCount() + "개");
    System.out.println("비디오: " + result.getVideoCount() + "개");
    System.out.println("댓글: " + result.getCommentCount() + "개");
} else {
    System.out.println("동기화 실패: " + result.getErrorMessage());
}
```

### 증분 동기화 사용법

```java
@Autowired
private YoutubeRedisSyncService youtubeRedisSyncService;

// 새로 추가된 비디오 ID 리스트
List<String> newVideoIds = Arrays.asList(
    "td7kfwpTDcA",
    "o6Ju5r82EwA",
    "UubUGelYJCU"
);

// 증분 동기화 실행
RedisSyncResult result = youtubeRedisSyncService.syncIncrementalToRedis(userId, newVideoIds);

// 결과 확인
if (result.isSuccess()) {
    System.out.println("증분 동기화 성공!");
    System.out.println("비디오: " + result.getVideoCount() + "개");
    System.out.println("댓글: " + result.getCommentCount() + "개");
} else {
    System.out.println("증분 동기화 실패: " + result.getErrorMessage());
}
```

### 실제 사용 시나리오

**시나리오 1: 사용자 로그인 시**

```java
// 1. MySQL에 채널/비디오 저장 (다른 서비스)
youtubeService.syncChannels(userId);
youtubeService.syncVideos(userId);

// 2. Redis 동기화 (초기 동기화)
// 채널 ID는 YouTube API를 통해 자동으로 조회됨 (DB 독립적)
youtubeRedisSyncService.syncToRedis(userId);
```

**시나리오 2: 새 영상 업로드 시**

```java
// 1. MySQL에 새 비디오 저장 (다른 서비스)
youtubeService.syncVideos(userId);

// 2. 새로 추가된 비디오 ID 리스트 가져오기
List<String> newVideoIds = getNewVideoIdsFromDB(userId);

// 3. Redis 증분 동기화
youtubeRedisSyncService.syncIncrementalToRedis(userId, newVideoIds);
```

---

## 주요 파일 위치

### 서비스 파일

- **통합 서비스**: `YoutubeRedisSyncServiceImpl.java`
  - 초기 동기화: `syncToRedis()`
  - 증분 동기화: `syncIncrementalToRedis()`

- **비디오 서비스**: `YoutubeVideoServiceImpl.java`
  - Top20 비디오 조회: `getTop20VideosByChannel()`
  - 메타데이터 저장: `syncVideoMetadata()`

- **댓글 서비스**: `YoutubeCommentServiceImpl.java`
  - Top20 댓글 저장: `syncTop20VideoComments()`
  - 댓글 저장: `syncVideoComments()`

### DTO 파일

- **옵션 설정**: `SyncOptions.java`
- **비디오 메타데이터**: `RedisYoutubeVideo.java` (기본 4개 필드)
- **댓글 기본**: `RedisYoutubeComment.java`
- **댓글 전체**: `RedisYoutubeCommentFull.java` (증분 동기화용)

### 매퍼 파일

- **비디오 매퍼**: `YoutubeVideoMapper.java`
- **댓글 매퍼**: `YoutubeCommentMapper.java`

---

## 요약

### 초기 동기화 vs 증분 동기화

| 항목 | 초기 동기화 | 증분 동기화 |
|------|------------|------------|
| **사용 시점** | 처음 로그인 시 | 새 영상 추가 시 |
| **비디오 메타데이터** | 기본 4개 필드 | 기본 4개 필드 |
| **댓글 메타데이터** | 기본 5개 필드 | 전체 11개 필드 |
| **댓글 개수** | 100개 제한 | 제한 없음 |
| **메서드** | `syncToRedis(userId)` | `syncIncrementalToRedis(userId, videoIds)` |
| **채널 조회** | YouTube API로 자동 조회 | - |

### 핵심 포인트

1. **초기 동기화**: 빠르게 완료하기 위해 최소한의 데이터만 저장
2. **증분 동기화**: 상세 분석을 위해 전체 데이터 저장
3. **API 호출 최소화**: 배치 처리로 효율성 향상
4. **옵션 기반 제어**: `SyncOptions`로 동기화 방식 구분

---

## 질문과 답변

**Q: 왜 초기 동기화는 댓글 100개만 가져오나요?**  
A: 초기 동기화는 빠르게 완료되어야 하므로, 대량의 댓글을 모두 가져오면 시간이 오래 걸립니다. 100개만 가져와도 대표적인 댓글을 확인할 수 있습니다.

**Q: 증분 동기화는 언제 호출하나요?**  
A: 사용자가 새로운 영상을 업로드했을 때, 또는 기존에 저장되지 않은 영상이 추가되었을 때 호출합니다.

**Q: Redis에 저장된 데이터는 언제 삭제되나요?**  
A: TTL(Time To Live)이 3일로 설정되어 있어, 3일 후 자동으로 삭제됩니다.

**Q: API 호출을 최소화하는 방법은?**  
A: 비디오 상세 정보는 50개씩 묶어서 한 번에 조회하고, 이미 조회한 데이터를 재사용합니다.

---

**마지막 업데이트**: 2025-01-XX  
**작성자**: 개발팀

