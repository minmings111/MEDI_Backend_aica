# Redis 폴더 완전 가이드 (1개월차 개발자용) 🚀

> **이 문서는 코딩을 배운지 1개월 정도 된 개발자가 처음 읽는다고 가정하고 매우 상세하게 작성되었습니다.**

---

## 📑 목차

1. [폴더 구조](#-폴더-구조)
2. [전체 목적과 흐름](#-전체-목적과-흐름)
3. [Redis 데이터 구조](#-redis-데이터-구조)
4. [파일별 역할과 상세 설명](#-파일별-역할과-상세-설명)
5. [코드 실행 흐름 (단계별)](#-코드-실행-흐름-단계별)
6. [코드 내부 동작 (매우 상세)](#-코드-내부-동작-매우-상세)
7. [보안과 에러 처리](#-보안과-에러-처리)
8. [변경 이력](#-변경-이력)

---

## 📁 폴더 구조

```
backend/src/main/java/com/medi/backend/youtube/redis/
├── dto/                                    # 데이터 구조 정의 (Data Transfer Object)
│   ├── RedisYoutubeComment.java            # 댓글 기본 데이터 구조 (초기 동기화용) ⭐
│   ├── RedisYoutubeCommentFull.java        # 댓글 전체 데이터 구조 (증분 동기화용) ⭐
│   ├── RedisYoutubeVideo.java              # 영상 기본 데이터 구조 (초기/증분 동기화 모두 사용) ⭐
│   ├── SyncOptions.java                    # 동기화 옵션 (초기/증분 구분)
│   └── RedisSyncResult.java                # 동기화 결과 DTO
│
├── mapper/                                 # 데이터 변환기
│   ├── YoutubeCommentMapper.java           # YouTube API → YoutubeComment 변환
│   └── YoutubeVideoMapper.java             # YouTube API → YoutubeVideo 변환
│
├── service/                                # 실제 작업 수행
│   ├── YoutubeRedisSyncService.java        # 통합 서비스 인터페이스 ⭐⭐⭐ 최상위!
│   ├── YoutubeRedisSyncServiceImpl.java   # 통합 서비스 구현체 ⭐⭐⭐ 핵심 진입점!
│   │
│   ├── YoutubeVideoService.java            # 영상 서비스 인터페이스
│   ├── YoutubeVideoServiceImpl.java        # 영상 조회 및 저장 (2, 3단계) ⭐⭐
│   │
│   ├── YoutubeCommentService.java          # 댓글 서비스 인터페이스
│   ├── YoutubeCommentServiceImpl.java      # 댓글 저장 (4단계) ⭐⭐
│   │
│   ├── YoutubeTranscriptService.java       # 스크립트 서비스 인터페이스
│   ├── YoutubeTranscriptServiceImpl.java   # 스크립트 저장 (5단계) ⭐
│   │
│   └── util/                               # 유틸리티 클래스
│       └── YoutubeApiClientUtil.java       # YouTube API 클라이언트 생성 유틸리티
│
├── channel_comment_fetcher.py              # Python 참고 코드
└── Youtube_Redis.md                        # 이 문서 ✨
```

### 폴더 구조 설명 (초보자용)

#### dto (Data Transfer Object) 📦
- **쉽게 말하면**: 데이터를 담는 상자
- **역할**: YouTube에서 받은 정보를 우리가 사용하기 편하게 정리한 것
- **예시**: `YoutubeComment`는 댓글 하나의 정보(작성자, 내용, 좋아요 수 등)를 담는 상자

#### mapper (매퍼) 🔄
- **쉽게 말하면**: 번역기 또는 변환기
- **역할**: YouTube API의 복잡한 데이터를 우리 DTO로 변환
- **예시**: YouTube API의 댓글 객체 → `YoutubeComment` 객체로 변환

#### service (서비스) ⚙️
- **쉽게 말하면**: 실제 일을 하는 일꾼
- **역할**: 비즈니스 로직 수행 (데이터 가져오기, 저장하기, 처리하기)
- **예시**: `YoutubeRedisSyncServiceImpl`은 전체 동기화 프로세스를 관리

---

## 🎯 전체 목적과 흐름

### 목적
YouTube API에서 사용자의 채널별 조회수 상위 20개 영상의 댓글을 가져와 **AI 서버가 사용하기 편한 형태로 Redis에 저장**합니다.

### 전체 흐름 (4단계)

```
[1단계: MySQL 저장] (유튜브 폴더의 YoutubeService에서 처리)
   ↓
   사용자 로그인 → 채널 등록 → MySQL에 채널/영상 저장
   ↓
   위치: YoutubeService.syncChannels() → channelMapper.upsert()
   ↓
   
[2단계: Redis Set 저장] (YoutubeVideoServiceImpl에서 처리)
   ↓
   YouTube API 호출 → 조회수 상위 20개 영상 찾기
   ↓
   Redis에 저장: channel:{channel_id}:top20_video_ids (Set 타입)
   ↓
   
[3단계: Redis 메타데이터 저장] (YoutubeVideoServiceImpl에서 처리)
   ↓
   각 영상의 메타데이터 추출 (channel_id, video_id, video_title, video_tags)
   ↓
   Redis에 저장: video:{video_id}:meta:json (String 타입, JSON)
   ↓
   
[4단계: Redis 댓글 저장] (YoutubeCommentServiceImpl에서 처리)
   ↓
   각 영상의 댓글 조회 (YouTube API)
   ↓
   Redis에 저장: video:{video_id}:comments:json (String 타입, JSON 배열)
   ↓
   
완료!
```

### 🔄 초기 동기화 vs 증분 동기화

시스템은 **초기 동기화**와 **증분 동기화** 두 가지 모드를 지원합니다.

#### 초기 동기화 (`syncToRedis`)

**용도**: 사용자가 처음 채널을 등록했을 때, 채널별 Top20 영상의 데이터를 빠르게 수집

**특징**:
- **입력**: 채널 ID 리스트
- **메타데이터**: 기본 필드만 저장 (`RedisYoutubeVideo`)
  - `video_id`, `video_title`, `channel_id`, `video_tags`
- **댓글**: 100개 제한 (`SyncOptions.initialSync()`)
- **댓글 메타데이터**: 기본 필드만 (`RedisYoutubeComment`)
  - `comment_id`, `text_original`, `author_name`, `like_count`, `published_at`

**호출 방법**:
```java
redisSyncService.syncToRedis(userId, channelIds);
```

#### 증분 동기화 (`syncIncrementalToRedis`)

**용도**: 새로 추가된 영상의 전체 데이터를 상세하게 수집

**특징**:
- **입력**: 비디오 ID 리스트
- **메타데이터**: 기본 필드만 저장 (`RedisYoutubeVideo`) - 초기 동기화와 동일
  - `video_id`, `video_title`, `channel_id`, `video_tags`
- **댓글**: 제한 없음 (전체 댓글 수집)
- **댓글 메타데이터**: 전체 필드 (`RedisYoutubeCommentFull`)
  - 기본 필드 + `author_channel_id`, `updated_at`, `parent_id`, `total_reply_count`, `can_rate`, `viewer_rating`

**호출 방법**:
```java
redisSyncService.syncIncrementalToRedis(userId, videoIds);
```

**비교표**:

| 구분 | 초기 동기화 | 증분 동기화 |
|------|-----------|------------|
| **진입점** | `syncToRedis(channelIds)` | `syncIncrementalToRedis(videoIds)` |
| **입력** | 채널 ID 리스트 | 비디오 ID 리스트 |
| **비디오 메타데이터** | 기본 필드만 | 기본 필드만 (동일) |
| **댓글 메타데이터** | 기본 필드만 | 전체 필드 |
| **댓글 개수** | 100개 제한 | 제한 없음 |
| **용도** | 최초 전체 동기화 | 새 영상 추가 시 |

---

### ⚠️ 중요: 1단계 → 2단계 연결 방법

**현재 상태**: 1단계(MySQL 저장)와 2단계(Redis 저장)가 **자동으로 연결되지 않습니다**.

**1단계 실행 위치**:
- `YoutubeService.syncChannels()`: MySQL에 채널 저장
- `YoutubeService.syncVideos()`: MySQL에 영상 저장
- 위치: `backend/src/main/java/com/medi/backend/youtube/service/YoutubeService.java`

**2단계 실행 방법**:

#### 방법 1: Controller에서 직접 호출 (권장)

```java
// ChannelController.java 예시
@PostMapping("/sync")
public ResponseEntity<?> syncChannels() {
    Integer userId = authUtil.getCurrentUserId();
    
    // 1단계: MySQL에 저장
    List<YoutubeChannelDto> channels = youtubeService.syncChannels(userId, false);
    
    // 2단계: Redis에 저장 (1단계 완료 후)
    List<String> channelIds = channels.stream()
        .map(YoutubeChannelDto::getYoutubeChannelId)
        .collect(Collectors.toList());
    
    RedisSyncResult result = youtubeRedisSyncService.syncToRedis(userId, channelIds);
    
    return ResponseEntity.ok(Map.of(
        "channels", channels,
        "redisSync", result
    ));
}
```

**실행 순서**:
1. `youtubeService.syncChannels()` 호출 → MySQL에 저장 (트랜잭션 커밋)
2. 트랜잭션 커밋 완료 후
3. `youtubeRedisSyncService.syncToRedis()` 호출 → Redis에 저장 (2, 3, 4단계)

#### 방법 2: YoutubeService 내부에서 호출

```java
// YoutubeService.java 수정 예시
@Transactional
public List<YoutubeChannelDto> syncChannels(Integer userId, boolean syncVideosEveryTime) {
    // ... 기존 코드 ...
    
    for (Channel ch : resp.getItems()) {
        // 1. MySQL에 저장
        channelMapper.upsert(dto);
        
        // ... 기존 코드 ...
    }
    
    // 1단계 완료 후 2단계 실행
    List<String> channelIds = out.stream()
        .map(YoutubeChannelDto::getYoutubeChannelId)
        .collect(Collectors.toList());
    
    try {
        youtubeRedisSyncService.syncToRedis(userId, channelIds);
    } catch (Exception e) {
        log.warn("Redis 동기화 실패: userId={}", userId, e);
        // Redis 실패해도 MySQL은 이미 저장되었으므로 계속 진행
    }
    
    return out;
}
```

**주의사항**:
- `@Transactional` 메서드 내에서 호출하면, 트랜잭션이 커밋되기 전에 Redis 저장이 실행될 수 있습니다.
- 트랜잭션 커밋 후 실행하려면 `@TransactionalEventListener`를 사용하거나, Controller에서 순차 호출하는 것이 안전합니다.

### 통합 서비스의 역할

**`YoutubeRedisSyncServiceImpl`**이 2, 3, 4단계를 순차적으로 실행합니다:

```java
// 호출 예시
YoutubeRedisSyncService syncService = ...;
List<String> channelIds = Arrays.asList("UCBA9XaL5wCdHnC5EmEzwrqw", ...);
RedisSyncResult result = syncService.syncToRedis(userId, channelIds);
```

**실행 순서**:
1. `videoService.getTop20VideosByChannel()` 호출 → 2, 3단계 동시 처리
2. `commentService.syncTop20VideoComments()` 호출 → 4단계 처리
3. 결과 반환 (`RedisSyncResult`)

---

## 💾 Redis 데이터 구조

Redis는 **키-값 저장소**입니다. 파일 시스템처럼 파일명(키)으로 데이터(값)를 저장하고 찾습니다.

### 저장되는 데이터 종류

#### 1. 채널의 비디오 ID 목록 (상위 20개) - 2단계

**Redis 데이터 타입**: `Set` (집합)

```
Key: channel:{channel_id}:top20_video_ids
Type: Set
Value: ["td7kfwpTDcA", "o6Ju5r82EwA", "UubUGelYJCU", ...]

예시 (채널: 튜브김민교):
Key: channel:UCBA9XaL5wCdHnC5EmEzwrqw:top20_video_ids
Value: ["td7kfwpTDcA", "o6Ju5r82EwA", "UubUGelYJCU", ...] (총 20개)
```

**Set을 사용하는 이유**:
- 중복 제거
- 빠른 검색 (O(1) 시간 복잡도)
- AI 서버가 "이 비디오가 Top20에 있나?" 빠르게 확인 가능

**저장 위치**: `YoutubeVideoServiceImpl.saveTop20VideoIdsToRedis()`

---

#### 2. 개별 비디오 메타데이터 - 3단계

**Redis 데이터 타입**: `String` (JSON 형식)

**초기/증분 동기화** (기본 메타데이터만, 동일):
```
Key: video:{video_id}:meta:json
Type: String
Value: JSON 객체 (RedisYoutubeVideo)

예시 (비디오: td7kfwpTDcA):
Key: video:td7kfwpTDcA:meta:json
Value: 
{
  "channel_id": "UCBA9XaL5wCdHnC5EmEzwrqw",
  "video_id": "td7kfwpTDcA",
  "video_title": "시작보다 어려운 끝 [츠예나, 이경민]",
  "video_tags": ["김민교", "츠예나", "이경민", "산본포차"]
}
```

**주의**: 초기 동기화와 증분 동기화 모두 기본 메타데이터만 저장합니다.
- 비디오 메타데이터는 4개 필드만 필요 (`video_id`, `video_title`, `channel_id`, `video_tags`)
- 추가 필드(`view_count`, `like_count` 등)는 저장하지 않음

**사용 목적**:
- AI 서버가 비디오 정보를 빠르게 조회
- 채널 ID, 제목, 태그 등 메타데이터 제공
- YouTube API를 다시 호출하지 않아도 됨

**저장 위치**: 
- 초기/증분 동기화 모두: `YoutubeVideoServiceImpl.saveVideoMetadataToRedis()` (기본 메타데이터만)

---

#### 3. 개별 비디오 스크립트 원본 (5단계) ⭐

**Redis 데이터 타입**: `String`

```
Key: video:{video_id}:transcript
Type: String
Value: 스크립트 텍스트

예시 (비디오: td7kfwpTDcA):
Key: video:td7kfwpTDcA:transcript
Value:
[음악]
경민이 밖에 나가있을 때 목소리 밖에서
다 들리는거 알고 경민이가 진심으로 용기내서
여태 못 했던말 하는거 보니까 가슴아프네
... (스크립트 전체 원본) ...
```

**Python 코드 참고**:
```python
from youtube_transcript_api import YouTubeTranscriptApi
client = YouTubeTranscriptApi()
fetched = client.fetch(video_id, languages=['ko'])
transcript_text = "\n".join([entry['text'] for entry in fetched.to_raw_data()])
```

**Java 구현**:
- YouTube Data API v3 Captions API 사용
- 한국어 자막 우선 조회 (ko → en → 기타 순서)
- Redis에 텍스트 형식으로 저장

**저장 위치**: `YoutubeTranscriptServiceImpl.saveTranscriptToRedis()`

**현재 상태**: ✅ 구현 완료 (`YoutubeTranscriptServiceImpl.java`)

---

#### 4. 개별 비디오 댓글 모음 - 4단계 ⭐⭐⭐ 핵심!

**Redis 데이터 타입**: `String` (JSON 배열)

**초기 동기화** (기본 메타데이터만):
```
Key: video:{video_id}:comments:json
Type: String
Value: JSON 배열 (RedisYoutubeComment 리스트)

예시 (비디오: td7kfwpTDcA):
Key: video:td7kfwpTDcA:comments:json
Value:
[
  {
    "comment_id": "UgyQnoD1JS_mILywmB94AaABAg",
    "text_original": "'이경민' 이라는 사람 다시본다 \n진솔한 사람 같다 응원한다",
    "author_name": "@user123",
    "like_count": 105,
    "published_at": "2021-04-18T10:05:00Z"
  },
  {
    "comment_id": "UgwJ3MDVhziGCfGTVoV4AaABAg",
    "text_original": "경민이 밖에 나가있을 때 목소리 밖에서...",
    "author_name": "@user456",
    "like_count": 230,
    "published_at": "2021-04-18T10:10:00Z"
  },
  ...
]
```

**증분 동기화** (전체 메타데이터):
```
Key: video:{video_id}:comments:json
Type: String
Value: JSON 배열 (RedisYoutubeCommentFull 리스트)

예시 (비디오: td7kfwpTDcA):
Key: video:td7kfwpTDcA:comments:json
Value:
[
  {
    "comment_id": "UgyQnoD1JS_mILywmB94AaABAg",
    "text_original": "'이경민' 이라는 사람 다시본다 \n진솔한 사람 같다 응원한다",
    "author_name": "@user123",
    "author_channel_id": "UC...",
    "like_count": 105,
    "published_at": "2021-04-18T10:05:00Z",
    "updated_at": "2021-04-19T10:05:00Z",
    "parent_id": null,
    "total_reply_count": 5,
    "can_rate": true,
    "viewer_rating": "like"
  },
  {
    "comment_id": "UgwJ3MDVhziGCfGTVoV4AaABAg",
    "text_original": "경민이 밖에 나가있을 때 목소리 밖에서...",
    "author_name": "@user456",
    "author_channel_id": "UC...",
    "like_count": 230,
    "published_at": "2021-04-18T10:10:00Z",
    "updated_at": null,
    "parent_id": "UgyQnoD1JS_mILywmB94AaABAg",
    "total_reply_count": 0,
    "can_rate": true,
    "viewer_rating": "none"
  },
  ...
]
```

**저장 방식**:
- 전체 댓글을 하나의 JSON 배열 문자열로 저장
- AI 서버(Python/TypeScript)가 직접 파싱 가능
- 초기 동기화: 기본 필드만 저장 (빠른 수집)
- 증분 동기화: 전체 메타데이터 저장 (상세 정보)

**저장 위치**: `YoutubeCommentServiceImpl.saveCommentsToRedis()`
- 옵션에 따라 `RedisYoutubeComment` 또는 `RedisYoutubeCommentFull` 사용

---

## 📄 파일별 역할과 상세 설명

### 1. YoutubeRedisSyncService.java (인터페이스) ⭐⭐⭐ 최상위!

#### 역할
전체 Redis 동기화 프로세스를 관리하는 통합 서비스의 계약서(인터페이스)

#### 위치
`service/YoutubeRedisSyncService.java`

#### 정의된 메서드

```java
public interface YoutubeRedisSyncService {
    RedisSyncResult syncToRedis(Integer userId, List<String> channelIds);
}
```

**매개변수 설명**:
- `userId`: 사용자 ID (OAuth 토큰 조회용)
- `channelIds`: 채널 ID 리스트 (DB에서 조회된 채널 ID들)

**반환값**:
- `RedisSyncResult`: 동기화 결과 정보 (채널 개수, 비디오 개수, 댓글 개수, 성공 여부)

**역할**:
- 2, 3, 4단계를 순차적으로 실행하는 통합 진입점
- 외부에서 호출하는 메인 서비스

---

### 2. YoutubeRedisSyncServiceImpl.java (구현체) ⭐⭐⭐ 핵심 진입점!

#### 역할
전체 Redis 동기화 프로세스를 실행하는 실제 구현체

#### 위치
`service/YoutubeRedisSyncServiceImpl.java`

#### 클래스 구조

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeRedisSyncServiceImpl implements YoutubeRedisSyncService {
    
    private final YoutubeVideoService videoService;      // 2, 3단계 담당
    private final YoutubeCommentService commentService;  // 4단계 담당
    
    @Override
    @Transactional
    public RedisSyncResult syncToRedis(Integer userId, List<String> channelIds) {
        // ... 구현 내용
    }
}
```

**의존성 설명**:
- `@Service`: Spring이 이 클래스를 서비스로 인식하고 관리
- `@RequiredArgsConstructor`: Lombok이 생성자를 자동 생성 (의존성 주입용)
- `@Transactional`: 트랜잭션 보장 (각 단계가 순차적으로 실행)

**의존성 주입**:
- `videoService`: 영상 조회 및 저장 (2, 3단계)
- `commentService`: 댓글 저장 (4단계)

#### 핵심 메서드: syncToRedis()

**실행 흐름 (단계별)**:

```
1. 채널 ID 리스트 검증
   ↓
2. videoService.getTop20VideosByChannel() 호출
   → 2단계: 채널별 Top20 비디오 ID Set 저장
   → 3단계: 비디오 메타데이터 저장
   ↓
3. 비디오 개수 계산
   ↓
4. commentService.syncTop20VideoComments() 호출
   → 4단계: 비디오 댓글 저장
   ↓
5. 결과 반환 (RedisSyncResult)
```

**코드 설명 (매우 상세)**:

```java
@Override
@Transactional
public RedisSyncResult syncToRedis(Integer userId, List<String> channelIds) {
```

**용어 설명**:
- `@Override`: 부모 인터페이스의 메서드를 구현한다는 의미
- `@Transactional`: 트랜잭션 보장 (한 단계 실패 시 롤백 가능)

```java
// 1. 채널 ID 리스트 검증
if (channelIds == null || channelIds.isEmpty()) {
    log.warn("채널 ID 리스트가 비어있습니다: userId={}", userId);
    return RedisSyncResult.builder()
        .channelCount(0)
        .videoCount(0)
        .commentCount(0)
        .success(false)
        .errorMessage("채널 ID 리스트가 비어있습니다")
        .build();
}
```

**동작 설명**:
- `channelIds == null`: 채널 ID 리스트가 null인지 확인
- `channelIds.isEmpty()`: 채널 ID 리스트가 비어있는지 확인
- 둘 중 하나라도 true면 → 빈 결과 반환

**왜 검증이 필요한가?**
- null이나 빈 리스트로 API를 호출하면 불필요한 작업 수행
- 에러를 미리 방지

```java
// 2. 2, 3단계 실행: 채널별 Top20 비디오 ID Set 저장 및 비디오 메타데이터 저장
Map<String, List<YoutubeVideo>> videosByChannel = 
    videoService.getTop20VideosByChannel(userId, channelIds);
```

**동작 설명**:
- `videoService.getTop20VideosByChannel()` 호출
- 이 메서드 내부에서:
  - YouTube API 호출하여 영상 조회
  - 조회수 기준 정렬 → 상위 20개 선택
  - Redis에 Set 저장 (2단계)
  - Redis에 메타데이터 저장 (3단계)
- 반환값: `Map<String, List<YoutubeVideo>>`
  - Key: 채널 ID (String)
  - Value: 해당 채널의 Top20 영상 리스트 (List<YoutubeVideo>)

**예시**:
```java
{
  "UCBA9XaL5wCdHnC5EmEzwrqw": [영상1, 영상2, ..., 영상20],
  "UC다른채널ID": [영상1, 영상2, ..., 영상20]
}
```

```java
// 3. 비디오 개수 계산
int totalVideoCount = videosByChannel.values().stream()
    .mapToInt(List::size)
    .sum();
```

**동작 설명**:
- `videosByChannel.values()`: Map의 모든 값(영상 리스트들) 가져오기
- `.stream()`: 스트림으로 변환 (데이터 흐름 처리)
- `.mapToInt(List::size)`: 각 리스트의 크기(영상 개수)를 정수로 변환
- `.sum()`: 모든 개수를 합산

**예시**:
- 채널1: 20개 영상
- 채널2: 15개 영상
- 채널3: 20개 영상
- → `totalVideoCount = 55`

```java
// 4. 4단계 실행: 비디오 댓글 저장
long totalCommentCount = commentService.syncTop20VideoComments(userId, channelIds);
```

**동작 설명**:
- `commentService.syncTop20VideoComments()` 호출
- 이 메서드 내부에서:
  - 각 채널의 Top20 영상 조회
  - 각 영상의 댓글 조회 (YouTube API)
  - Redis에 댓글 저장 (4단계)
- 반환값: 저장된 댓글 개수 (long)

```java
// 5. 결과 반환
return RedisSyncResult.builder()
    .channelCount(videosByChannel.size())
    .videoCount(totalVideoCount)
    .commentCount(totalCommentCount)
    .success(true)
    .build();
```

**동작 설명**:
- `RedisSyncResult.builder()`: Builder 패턴으로 객체 생성
- `.channelCount()`: 처리된 채널 개수
- `.videoCount()`: 처리된 비디오 개수
- `.commentCount()`: 저장된 댓글 개수
- `.success(true)`: 성공 여부
- `.build()`: 최종 객체 생성

---

### 3. RedisSyncResult.java (결과 DTO)

#### 역할
동기화 결과 정보를 담는 데이터 구조

#### 위치
`dto/RedisSyncResult.java`

#### 필드 설명

```java
@Getter
@Builder
public class RedisSyncResult {
    private final int channelCount;      // 처리된 채널 개수
    private final int videoCount;        // 처리된 비디오 개수
    private final long commentCount;     // 저장된 댓글 개수
    private final boolean success;       // 성공 여부
    private final String errorMessage;    // 에러 메시지 (실패 시)
}
```

**용어 설명**:
- `@Getter`: Lombok이 자동으로 `getChannelCount()` 같은 메서드 생성
- `@Builder`: Builder 패턴 지원 (`.builder().channelCount(5).build()`)
- `private final`: 한 번 설정하면 변경 불가능 (불변 객체)

**사용 예시**:
```java
RedisSyncResult result = syncService.syncToRedis(userId, channelIds);
System.out.println("처리된 채널: " + result.getChannelCount() + "개");
System.out.println("처리된 비디오: " + result.getVideoCount() + "개");
System.out.println("저장된 댓글: " + result.getCommentCount() + "개");
```

---

### 4. YoutubeComment.java (DTO)

#### 역할
댓글 한 개의 정보를 담는 데이터 구조

#### 위치
`dto/YoutubeComment.java`

#### 필드 (변수) 설명

| 필드명 (Java) | JSON 필드명 | 데이터 타입 | 설명 | 예시 |
|--------------|-------------|------------|------|------|
| commentId | comment_id | String | 댓글 고유 ID | "UgyQnoD1JS_..." |
| textOriginal | text_original | String | 댓글 원본 텍스트 | "좋은 영상입니다" |
| authorName | author_name | String | 작성자 이름 | "@user123" |
| likeCount | like_count | Long | 좋아요 수 | 105 |
| publishedAt | published_at | String | 작성 시간 (ISO 8601) | "2021-04-18T10:05:00Z" |

#### 코드 설명 (초보자용)

```java
@Getter
@Builder
public class YoutubeComment {
    @JsonProperty("comment_id")
    private final String commentId;
    
    @JsonProperty("text_original")
    private final String textOriginal;
    
    // ... 기타 필드
}
```

**용어 설명**:
- `@Getter`: Lombok 라이브러리. 자동으로 `getCommentId()` 같은 메서드 생성
- `@Builder`: 객체 생성을 쉽게 해주는 패턴 (`YoutubeComment.builder().commentId("abc").build()`)
- `@JsonProperty("comment_id")`: JSON 변환 시 필드명을 `comment_id`로 설정
- `private final`: 한 번 설정하면 변경 불가능 (불변 객체)

**왜 스네이크 케이스(`comment_id`)를 사용하나요?**
- Java는 카멜케이스(`commentId`) 선호
- Python/TypeScript는 스네이크케이스(`comment_id`) 선호
- AI 서버(Python/TypeScript)와 호환을 위해 JSON에서는 스네이크케이스 사용

---

### 5. YoutubeVideo.java (DTO)

#### 역할
영상 한 개의 정보를 담는 데이터 구조

#### 위치
`dto/YoutubeVideo.java`

#### 필드 (변수) 설명

| 필드명 (Java) | JSON 필드명 | 데이터 타입 | 설명 | 예시 |
|--------------|-------------|------------|------|------|
| youtubeVideoId | video_id | String | 영상 ID | "td7kfwpTDcA" |
| title | video_title | String | 영상 제목 | "시작보다 어려운 끝" |
| channelId | channel_id | String | 채널 ID | "UCBA9XaL5..." |
| tags | video_tags | List\<String\> | 태그 리스트 | ["김민교", "츠예나"] |

#### 코드 설명 (초보자용)

```java
@Getter
@Builder
public class YoutubeVideo {
    @JsonProperty("video_id")
    private final String youtubeVideoId;
    
    @JsonProperty("video_title")
    private final String title;
    
    @JsonProperty("channel_id")
    private final String channelId;
    
    @JsonProperty("video_tags")
    private final List<String> tags;
    
    // @JsonCreator 생성자...
}
```

**용어 설명**:
- `@Getter`: Lombok 라이브러리. 자동으로 `getYoutubeVideoId()` 같은 메서드 생성
- `@Builder`: 객체 생성을 쉽게 해주는 패턴 (`YoutubeVideo.builder().youtubeVideoId("abc").build()`)
- `@JsonProperty("video_id")`: JSON 변환 시 필드명을 `video_id`로 설정
- `private final`: 한 번 설정하면 변경 불가능 (불변 객체)

**왜 스네이크 케이스(`video_id`)를 사용하나요?**
- Java는 카멜케이스(`youtubeVideoId`) 선호
- Python/TypeScript는 스네이크케이스(`video_id`) 선호
- AI 서버(Python/TypeScript)와 호환을 위해 JSON에서는 스네이크케이스 사용

**Redis 저장 방식**:
```java
// YoutubeVideoServiceImpl.saveVideoMetadataToRedis() 메서드
// DTO 객체를 직접 JSON으로 변환 (YoutubeComment와 동일한 방식)
String metaJson = objectMapper.writeValueAsString(video);
// @JsonProperty가 자동으로 필드명을 스네이크 케이스로 변환
// youtubeVideoId → video_id
// title → video_title
// channelId → channel_id
// tags → video_tags
```

**YoutubeComment와의 일관성**:
- `YoutubeComment`: DTO 객체를 직접 JSON으로 변환 → `@JsonProperty` 사용
- `YoutubeVideo`: DTO 객체를 직접 JSON으로 변환 → `@JsonProperty` 사용
- **둘 다 동일한 방식으로 일관성 유지**

---

### 6. YoutubeCommentMapper.java (매퍼)

#### 역할
YouTube API의 Comment 객체 → YoutubeComment DTO 변환

#### 위치
`mapper/YoutubeCommentMapper.java`

#### 핵심 메서드

```java
public YoutubeComment toRedisComment(Comment comment, String parentId)
```

**매개변수 설명**:
- `Comment comment`: YouTube API에서 받은 댓글 객체 (Google 라이브러리)
- `String parentId`: 부모 댓글 ID (대댓글이면 값 존재, 최상위 댓글이면 null)

**반환값**:
- `YoutubeComment`: 우리가 정의한 DTO 객체

#### 내부 동작 (단계별)

**1단계: 댓글 ID 추출**
```java
String commentId = comment.getId();
```
- YouTube API의 Comment 객체에서 ID 가져오기

**2단계: 댓글 원본 텍스트 추출**
```java
String textOriginal = comment.getSnippet().getTextDisplay();
if (comment.getSnippet().getTextOriginal() != null) {
    textOriginal = comment.getSnippet().getTextOriginal();
}
```
- `textDisplay`: HTML 형식 (예: `좋은<br>영상`)
- `textOriginal`: 순수 텍스트 (예: `좋은\n영상`)
- 순수 텍스트가 있으면 우선 사용

**3단계: 작성자 이름 추출**
```java
String authorName = comment.getSnippet().getAuthorDisplayName();
```

**4단계: 좋아요 수 추출**
```java
Long likeCount = null;
if (comment.getSnippet().getLikeCount() != null) {
    likeCount = comment.getSnippet().getLikeCount().longValue();
}
```
- YouTube API는 `getLikeCount()`가 null일 수 있음
- null 체크 후 Long 타입으로 변환

**5단계: 발행 시간 변환**
```java
String publishedAt = null;
if (comment.getSnippet().getPublishedAt() != null) {
    publishedAt = comment.getSnippet().getPublishedAt().toStringRfc3339();
}
```
- YouTube API의 DateTime 객체 → ISO 8601 형식 문자열
- 예: `"2021-04-18T10:05:00Z"`

**6단계: YoutubeComment 객체 생성**
```java
return YoutubeComment.builder()
    .commentId(commentId)
    .textOriginal(textOriginal)
    .authorName(authorName)
    .likeCount(likeCount)
    .publishedAt(publishedAt)
    .build();
```
- Builder 패턴으로 객체 생성

---

### 7. YoutubeVideoMapper.java (매퍼)

#### 역할
YouTube API의 Video 객체 → YoutubeVideo DTO 변환

#### 위치
`mapper/YoutubeVideoMapper.java`

#### 핵심 메서드

```java
public YoutubeVideo toRedisVideo(Video video, String channelId)
```

**매개변수 설명**:
- `Video video`: YouTube API에서 받은 영상 객체
- `String channelId`: 채널 ID (추가 정보)

#### 내부 동작

**주요 필드 추출**:
1. 영상 ID: `video.getId()`
2. 제목: `video.getSnippet().getTitle()`
3. 태그: `video.getSnippet().getTags()`

**Null 체크**:
- YouTube API는 많은 필드가 null일 수 있음
- 모든 필드에 대해 null 체크 필요
- null이면 기본값 사용 (예: 태그 빈 리스트)

---

### 8. YoutubeVideoServiceImpl.java (서비스) ⭐⭐

#### 역할
YouTube API에서 영상 정보를 가져와 Redis에 저장 (2, 3단계 담당)

#### 위치
`service/YoutubeVideoServiceImpl.java`

#### 주요 메서드

##### 1) getTop20VideosByChannel(Integer userId, List<String> channelIds)

**목적**: 사용자의 각 채널별 조회수 상위 20개 영상 조회 및 Redis 저장

**실행 흐름 (단계별)**:

```
1. 채널 ID 리스트 검증
   ↓
2. OAuth 토큰 가져오기
   ↓
3. YouTube API 클라이언트 생성
   ↓
4. 각 채널마다 반복:
   4-1. 채널의 영상 목록 조회 (Search API)
   4-2. 비디오 ID 목록 추출
   4-3. 비디오 상세 정보 조회 (Videos API)
   4-4. 조회수 기준 정렬 → 상위 20개 선택
   4-5. Redis DTO로 변환
   4-6. Redis에 저장:
       - Top20 비디오 ID Set 저장 (2단계)
       - 개별 비디오 메타데이터 저장 (3단계)
   ↓
5. Map<채널ID, 영상리스트> 반환
```

**코드 설명 (핵심 부분)**:

```java
// 1. 채널 ID 리스트 검증
if (channelIds == null || channelIds.isEmpty()) {
    log.warn("채널 ID 리스트가 비어있습니다: userId={}", userId);
    return Collections.emptyMap();
}
```

```java
// 2. OAuth 토큰 가져오기
String token = youtubeOAuthService.getValidAccessToken(userId);
YouTube yt = YoutubeApiClientUtil.buildClient(token);  // 공통 유틸리티 사용
```
- OAuth: 사용자 권한 확인 (로그인 토큰)
- YouTube API 호출하려면 토큰 필요
- **변경**: `buildClient()` → `YoutubeApiClientUtil.buildClient()` (공통 유틸리티)

```java
// 3. 각 채널마다 처리
Map<String, List<YoutubeVideo>> videosByChannel = new HashMap<>();
for (String channelId : channelIds) {
    // ...
}
```
- `HashMap`: 키-값 쌍 저장 (사전처럼)
- 각 채널마다 반복 처리

```java
// 4. 채널의 영상 목록 조회
List<SearchResult> searchResults = fetchChannelVideos(yt, channelId);
```
- YouTube Search API 호출
- 채널의 모든 영상 목록 가져오기

```java
// 5. 비디오 ID 목록 추출
List<String> videoIds = searchResults.stream()
    .map(result -> result.getId().getVideoId())
    .filter(id -> id != null)
    .collect(Collectors.toList());
```
- `stream()`: 데이터 흐름 처리 (함수형 프로그래밍)
- `.map()`: 각 요소를 변환 (SearchResult → 비디오 ID)
- `.filter()`: 조건에 맞는 것만 선택 (null 제외)
- `.collect()`: 결과를 List로 수집

```java
// 6. 비디오 상세 정보 조회
List<Video> videos = fetchVideoDetails(yt, videoIds);
```
- YouTube Videos API 호출
- 조회수, 좋아요 수 등 통계 정보 포함

```java
// 7. 조회수 기준 정렬 → 상위 20개 (DTO 변환 전)
List<Video> top20Videos = videos.stream()
    .sorted(Comparator.comparing(
        video -> {
            if (video.getStatistics() != null && video.getStatistics().getViewCount() != null) {
                return video.getStatistics().getViewCount().longValue();
            }
            return 0L;
        },
        Comparator.reverseOrder()
    ))
    .limit(20)
    .collect(Collectors.toList());
```
- `.sorted()`: 정렬
- `Comparator.comparing()`: 정렬 기준 (조회수)
- `reverseOrder()`: 내림차순 (높은 순)
- `.limit(20)`: 상위 20개만

```java
// 8. Redis DTO로 변환
List<YoutubeVideo> channelVideos = new ArrayList<>();
for (Video video : top20Videos) {
    YoutubeVideo redisVideo = redisMapper.toRedisVideo(video, channelId);
    if (redisVideo != null) {
        channelVideos.add(redisVideo);
    }
}
```

```java
// 9. Redis에 저장
saveTop20VideoIdsToRedis(channelId, channelVideos);  // 2단계
saveVideoMetadataToRedis(channelVideos);              // 3단계
```

---

##### 2) saveTop20VideoIdsToRedis(String channelId, List<YoutubeVideo> top20Videos)

**목적**: 채널별 Top20 비디오 ID Set을 Redis에 저장 (2단계)

**코드 설명**:

```java
String setKey = "channel:" + channelId + ":top20_video_ids";

// 1. 기존 Set 삭제
stringRedisTemplate.delete(setKey);

// 2. 새로운 비디오 ID 추가
for (YoutubeVideo video : top20Videos) {
    if (video.getYoutubeVideoId() != null) {
        stringRedisTemplate.opsForSet().add(setKey, video.getYoutubeVideoId());
    }
}

// 3. TTL 설정
stringRedisTemplate.expire(setKey, Duration.ofDays(3));
```
- `opsForSet()`: Set 타입 연산
- `.add(key, value)`: Set에 요소 추가
- Redis 명령어: `SADD channel:{channelId}:top20_video_ids "videoId"`

---

##### 3) saveVideoMetadataToRedis(List<YoutubeVideo> videos)

**목적**: 개별 비디오 메타데이터를 Redis에 저장 (3단계)

**코드 설명**:

```java
for (YoutubeVideo video : videos) {
    String metaKey = "video:" + videoId + ":meta:json";
    
    // DTO 객체를 직접 JSON으로 변환 (YoutubeComment와 동일한 방식)
    // @JsonProperty가 자동으로 필드명을 스네이크 케이스로 변환
    // youtubeVideoId → video_id
    // title → video_title
    // channelId → channel_id
    // tags → video_tags
    String metaJson = objectMapper.writeValueAsString(video);
    
    // Redis에 저장
    stringRedisTemplate.opsForValue().set(metaKey, metaJson);
    stringRedisTemplate.expire(metaKey, Duration.ofDays(3));
}
```
- `objectMapper.writeValueAsString()`: DTO 객체 → JSON 문자열
- `@JsonProperty`가 자동으로 필드명 변환 (카멜케이스 → 스네이크 케이스)
- `opsForValue().set()`: String 타입으로 저장
- **YoutubeComment와 동일한 방식으로 일관성 유지**

---

### 9. YoutubeCommentServiceImpl.java (서비스) ⭐⭐

#### 역할
YouTube API에서 댓글을 가져와 Redis에 저장하는 핵심 로직 (4단계 담당)

#### 위치
`service/YoutubeCommentServiceImpl.java`

#### 주요 메서드

##### 1) syncTop20VideoComments(Integer userId, Map<String, List<YoutubeVideo>> videosByChannel)

**목적**: 사용자의 채널별 조회수 상위 20개 영상의 댓글 동기화

**⚠️ 변경사항 (중복 호출 제거)**:
- **변경 전**: `List<String> channelIds`를 받아서 내부에서 `videoService.getTop20VideosByChannel()` 호출
- **변경 후**: `Map<String, List<YoutubeVideo>> videosByChannel`을 파라미터로 받아서 재사용
- **효과**: 중복 API 호출 방지, 성능 향상

**실행 흐름 (단계별)**:

```
1. videosByChannel 검증 (이미 조회된 결과 재사용) ⭐
   ↓
2. OAuth 토큰 가져오기
   ↓ (YoutubeOAuthService 호출)
   
3. YouTube API 클라이언트 생성 (YoutubeApiClientUtil 사용) ⭐
   ↓
   
4. 각 채널의 각 영상 반복:
   4-1. videoId 유효성 검사
   4-2. Redis Key 생성: "video:{videoId}:comments:json"
   4-3. 기존 댓글 백업 (실패 시 복구용)
   4-4. fetchAndSaveComments() 호출
   4-5. 저장 실패 시 기존 댓글 복구
   ↓
   
5. 총 댓글 개수 반환
```

**코드 설명 (핵심 부분)**:

```java
// 1. videosByChannel 검증 (이미 조회된 결과를 재사용하여 중복 API 호출 방지)
if (videosByChannel == null || videosByChannel.isEmpty()) {
    log.warn("비디오 리스트가 비어있습니다: userId={}", userId);
    return 0;
}
```
- **중요**: `videosByChannel`은 `YoutubeRedisSyncServiceImpl`에서 이미 조회한 결과
- 중복 API 호출을 방지하기 위해 파라미터로 받아서 재사용

```java
// 2. OAuth 토큰 가져오기
String token = youtubeOAuthService.getValidAccessToken(userId);
YouTube yt = YoutubeApiClientUtil.buildClient(token);  // 공통 유틸리티 사용
```
- **변경**: `buildClient()` → `YoutubeApiClientUtil.buildClient()` (공통 유틸리티)

```java
// 3. 각 채널의 상위 20개 영상의 댓글 조회
for (Map.Entry<String, List<YoutubeVideo>> entry : videosByChannel.entrySet()) {
    String channelId = entry.getKey();
    List<YoutubeVideo> videos = entry.getValue();
    // ...
}
```
- `for (... : ...)`: 향상된 for문 (각 항목 반복)
- `entry.getKey()`: 채널 ID
- `entry.getValue()`: 영상 리스트

```java
// 4. videoId 유효성 검사 (보안)
if (videoId == null || videoId.isBlank()) {
    log.warn("영상 ID가 없습니다. 건너뜁니다: {}", video);
    continue;
}
```
- Null 또는 빈 문자열 체크
- `continue`: 다음 반복으로 건너뛰기

```java
// 5. Redis Key 생성
String redisKey = "video:" + videoId + ":comments:json";
```
- 예: `"video:td7kfwpTDcA:comments:json"`

```java
// 6. 기존 댓글 백업
String existingComments = stringRedisTemplate.opsForValue().get(redisKey);
```
- 실패 시 복구를 위해 기존 데이터 백업

```java
// 7. 댓글 조회 및 저장
commentCount = fetchAndSaveComments(yt, videoId, redisKey);
```
- 실제 YouTube API 호출하여 댓글 수집

```java
// 8. 실패 시 복구
if (commentCount == 0 && existingComments != null && !existingComments.isEmpty()) {
    log.warn("댓글 조회 실패 또는 댓글 없음. 기존 댓글 복구: {}", videoId);
    stringRedisTemplate.opsForValue().set(redisKey, existingComments);
}
```
- 댓글 조회 실패 시 기존 데이터 복구

---

##### 2) fetchAndSaveComments(YouTube yt, String videoId, String redisKey)

**목적**: 특정 영상의 모든 댓글을 YouTube API에서 가져와 Redis에 저장

**실행 흐름 (단계별)**:

```
1. 댓글 수집용 List 생성 (빈 리스트)
   ↓
   
2. 페이지네이션 반복 (do-while):
   2-1. YouTube CommentThreads API 요청 생성
   2-2. API 호출 (⭐ 실제 네트워크 요청)
   2-3. 응답에서 댓글 추출
   2-4. 최상위 댓글 변환 → List에 추가
   2-5. 대댓글 변환 → List에 추가
   2-6. nextPageToken 확인
   ↓
   
3. 전체 댓글 List → JSON 배열 문자열로 변환
   ↓
   
4. Redis에 String 타입으로 저장
   ↓
   
5. TTL 설정 (3일)
   ↓
   
6. 댓글 개수 반환
```

**코드 설명 (핵심 부분)**:

```java
// 1. 댓글 수집용 List 생성
List<YoutubeComment> allComments = new ArrayList<>();
String nextPageToken = null;
```
- `ArrayList`: 크기가 자동으로 늘어나는 리스트
- `nextPageToken`: 다음 페이지 토큰 (페이지네이션용)

```java
// 2. 페이지네이션 반복
do {
    // ... API 호출
} while (nextPageToken != null);
```
- `do-while`: 최소 1번은 실행, 조건이 참이면 계속 반복
- YouTube API는 한 번에 최대 100개 댓글만 반환
- 더 많은 댓글을 가져오려면 여러 번 호출 필요

```java
// 3. YouTube CommentThreads API 요청 생성
YouTube.CommentThreads.List req = yt.commentThreads()
    .list(Arrays.asList("snippet", "replies"));
req.setVideoId(videoId);
req.setOrder("time");
req.setMaxResults(100L);
```
- `commentThreads().list()`: 댓글 목록 조회 API
- `part="snippet,replies"`: 댓글 정보 + 대댓글
- `order="time"`: 시간순 정렬
- `maxResults=100`: 한 페이지당 최대 100개

```java
// 4. 실제 YouTube API 호출
CommentThreadListResponse resp = req.execute();
```
- **⭐ 이 시점에서 YouTube 서버로 HTTP 요청 전송**
- 네트워크를 통해 데이터 받아옴

```java
// 5. 응답에서 댓글 추출
if (resp.getItems() != null) {
    for (CommentThread thread : resp.getItems()) {
        Comment top = thread.getSnippet().getTopLevelComment();
        
        // 최상위 댓글 변환
        YoutubeComment topComment = redisMapper.toRedisComment(top, null);
        if (topComment != null) {
            allComments.add(topComment);
        }
        
        // 대댓글 처리
        if (thread.getReplies() != null && thread.getReplies().getComments() != null) {
            for (Comment reply : thread.getReplies().getComments()) {
                YoutubeComment replyComment = redisMapper.toRedisComment(reply, top.getId());
                if (replyComment != null) {
                    allComments.add(replyComment);
                }
            }
        }
    }
}
```
- `for`: 각 댓글 스레드 반복
- 최상위 댓글 + 대댓글 모두 처리
- `redisMapper.toRedisComment()`: YouTube API 객체 → DTO 변환

```java
// 6. 다음 페이지 토큰 확인
nextPageToken = resp.getNextPageToken();
```
- 다음 페이지가 있으면 토큰 값 존재
- 없으면 null (반복 종료)

```java
// 7. 전체 댓글을 하나의 JSON 배열로 저장
if (!allComments.isEmpty()) {
    saveCommentsToRedis(redisKey, allComments);
}
```

---

### 10. YoutubeTranscriptServiceImpl.java (서비스) ⭐

#### 역할
YouTube API에서 비디오 스크립트(자막)를 가져와 Redis에 저장 (5단계 담당)

#### 위치
`service/YoutubeTranscriptServiceImpl.java`

#### 주요 메서드

##### 1) saveTranscriptToRedis(String videoId, Integer userId)

**목적**: 특정 비디오의 스크립트(자막)를 Redis에 저장

**Python 코드 참고**:
```python
from youtube_transcript_api import YouTubeTranscriptApi
client = YouTubeTranscriptApi()
fetched = client.fetch(video_id, languages=['ko'])
transcript_text = "\n".join([entry['text'] for entry in fetched.to_raw_data()])
```

**실행 흐름 (단계별)**:

```
1. videoId 검증
   ↓
2. OAuth 토큰 가져오기
   ↓
3. YouTube API 클라이언트 생성 (YoutubeApiClientUtil 사용)
   ↓
4. 자막 목록 조회 (YouTube Captions API)
   ⭐ API 엔드포인트: youtube.captions.list
   ↓
5. 언어별 우선순위 선택 (한국어 → 영어 → 기타)
   ↓
6. 자막 다운로드 (YouTube Captions API)
   ⭐ API 엔드포인트: youtube.captions.download
   ↓
7. 텍스트 정리 (XML 태그 제거 등)
   ↓
8. Redis에 저장: video:{video_id}:transcript
   ↓
9. TTL 설정 (3일)
```

**코드 설명 (핵심 부분)**:

```java
// 1. 자막 목록 조회
YouTube.Captions.List captionsRequest = yt.captions()
    .list("snippet", videoId);
CaptionListResponse captionsResponse = captionsRequest.execute();
```
- **⭐ YouTube Captions API 호출**: `youtube.captions.list`
- 자막 목록 조회 (언어 정보 포함)

```java
// 2. 한국어 자막 우선 선택 (Python: languages=['ko'])
Caption koreanCaption = null;
Caption englishCaption = null;
Caption fallbackCaption = null;

for (Caption caption : captionsResponse.getItems()) {
    String language = caption.getSnippet().getLanguage();
    if ("ko".equals(language)) {
        koreanCaption = caption;
        break;  // 한국어 자막 찾으면 즉시 종료
    } else if ("en".equals(language) && englishCaption == null) {
        englishCaption = caption;
    } else if (fallbackCaption == null) {
        fallbackCaption = caption;
    }
}
```
- **언어 우선순위**: 한국어(ko) → 영어(en) → 기타
- Python 코드의 `languages=['ko']`와 동일한 동작

```java
// 3. 자막 다운로드
YouTube.Captions.Download downloadRequest = yt.captions()
    .download(selectedCaption.getId());
String transcriptText = downloadRequest.executeAsString();
```
- **⭐ YouTube Captions API 호출**: `youtube.captions.download`
- 실제 자막 텍스트 다운로드

```java
// 4. 텍스트 정리 (Python: entry['text']를 join하는 부분)
String cleanedTranscript = cleanTranscriptText(transcriptText);
```
- XML 태그 제거 등 텍스트 정리
- Python 코드의 `"\n".join([entry['text'] for entry in fetched.to_raw_data()])`와 유사

```java
// 5. Redis에 저장
String redisKey = "video:" + videoId + ":transcript";
stringRedisTemplate.opsForValue().set(redisKey, cleanedTranscript);
stringRedisTemplate.expire(redisKey, Duration.ofDays(3));
```

**에러 처리**:
- 자막이 없는 경우: `captionNotFound` 에러 처리
- 한 비디오 실패해도 다른 비디오는 계속 처리

##### 2) saveTranscriptsToRedis(List<String> videoIds, Integer userId)

**목적**: 여러 비디오의 스크립트를 일괄 저장

**동작**:
- 각 비디오마다 `saveTranscriptToRedis()` 호출
- 성공한 비디오 개수 반환

##### 3) getTranscriptFromRedis(String videoId)

**목적**: Redis에서 자막 조회

**동작**:
- Redis Key: `video:{videoId}:transcript`
- String 타입으로 저장된 텍스트 반환

---

##### 3) saveCommentsToRedis(String redisKey, List<YoutubeComment> comments)

**목적**: 댓글 List를 JSON 배열 문자열로 변환하여 Redis에 저장

**실행 흐름**:

```
1. List<YoutubeComment> → JSON 배열 문자열 변환
   ↓
2. Redis에 String 타입으로 저장
   ↓
3. TTL 설정 (3일)
```

**코드 설명**:

```java
// 1. JSON 배열 문자열로 변환
String jsonArray = objectMapper.writeValueAsString(comments);
```
- `objectMapper`: Jackson 라이브러리 (JSON 변환 도구)
- `writeValueAsString()`: 객체 → JSON 문자열
- 예: `[{"comment_id":"Ugy123","text_original":"좋은 영상",...}, {...}]`

```java
// 2. Redis에 저장
stringRedisTemplate.opsForValue().set(redisKey, jsonArray);
```
- `opsForValue()`: String 타입 연산
- `.set(key, value)`: 키-값 저장
- 예: `set("video:td7kfwpTDcA:comments:json", "[{...}, {...}]")`

```java
// 3. TTL 설정
stringRedisTemplate.expire(redisKey, Duration.ofDays(3));
```
- TTL (Time To Live): 데이터 만료 시간
- 3일 후 자동 삭제
- 오래된 데이터 자동 정리

---

## 🔄 코드 실행 흐름 (단계별)

### ⚠️ 중요: 현재 1→2 단계 연결 상태

**현재 구현 상태**:
- ✅ 1단계(MySQL 저장): `YoutubeService.syncChannels()`에서 완료
- ❌ 2단계(Redis 저장): **자동으로 실행되지 않음**
- ✅ 2, 3, 4단계 통합: `YoutubeRedisSyncService.syncToRedis()`로 구현됨

**연결 방법**:
- Controller에서 1단계 완료 후 2단계를 수동 호출해야 합니다.
- 또는 `YoutubeService` 내부에서 트랜잭션 커밋 후 호출해야 합니다.

### 전체 호출 순서 (이상적인 흐름)

```
[1단계: MySQL 저장]
YoutubeService.syncChannels(userId, syncVideosEveryTime)
   ↓
   ├─→ YouTube API 호출 (채널 정보)
   ├─→ channelMapper.upsert(dto) [MySQL 저장] ⭐
   └─→ 트랜잭션 커밋 완료
   ↓
   
[1→2 단계 연결점] ⚠️ 현재 구현 필요
   ↓
   방법 1: Controller에서 순차 호출
   방법 2: YoutubeService 내부에서 트랜잭션 커밋 후 호출
   ↓
   
[2, 3, 4단계: Redis 저장]
YoutubeRedisSyncServiceImpl.syncToRedis(userId, channelIds)
   ↓
   
[2, 3단계 실행]
YoutubeVideoServiceImpl.getTop20VideosByChannel(userId, channelIds)
   ↓
   ├─→ YoutubeOAuthService.getValidAccessToken(userId) [OAuth 토큰 조회]
   ├─→ buildClient(token) [YouTube API 클라이언트 생성]
   ├─→ fetchChannelVideos(yt, channelId) [YouTube Search API 호출] ⭐
   ├─→ fetchVideoDetails(yt, videoIds) [YouTube Videos API 호출] ⭐
   ├─→ YoutubeVideoMapper.toRedisVideo(video, channelId) [DTO 변환]
   ├─→ saveTop20VideoIdsToRedis(channelId, top20Videos) [Redis Set 저장] ⭐ 2단계
   └─→ saveVideoMetadataToRedis(top20Videos) [Redis String 저장] ⭐ 3단계
   │
   └─→ 반환: Map<String, List<YoutubeVideo>>
   ↓
   
[4단계 실행]
YoutubeCommentServiceImpl.syncTop20VideoComments(userId, videosByChannel)
   ↓
   ├─→ videosByChannel 파라미터로 받음 (중복 API 호출 방지) ⭐
   ├─→ YoutubeOAuthService.getValidAccessToken(userId) [OAuth 토큰 조회]
   ├─→ YoutubeApiClientUtil.buildClient(token) [YouTube API 클라이언트 생성] ⭐
   └─→ 각 영상마다:
          ↓
          fetchAndSaveComments(yt, videoId, redisKey)
             ↓
             ├─→ YouTube CommentThreads API 호출 (페이지네이션) ⭐
             ├─→ YoutubeCommentMapper.toRedisComment(comment, parentId) [DTO 변환]
             └─→ saveCommentsToRedis(redisKey, allComments) [Redis String 저장] ⭐ 4단계
   │
   └─→ 반환: long (댓글 개수)
   ↓
   
[결과 반환]
RedisSyncResult.builder()
   .channelCount(...)
   .videoCount(...)
   .commentCount(...)
   .success(true)
   .build()
```

### 1→2 단계 연결 구현 예시

#### 예시 1: Controller에서 연결

```java
// ChannelController.java
@PostMapping("/sync")
public ResponseEntity<?> syncChannels() {
    Integer userId = authUtil.getCurrentUserId();
    
    // 1단계: MySQL에 저장
    List<YoutubeChannelDto> channels = youtubeService.syncChannels(userId, false);
    
    // 채널 ID 추출
    List<String> channelIds = channels.stream()
        .map(YoutubeChannelDto::getYoutubeChannelId)
        .collect(Collectors.toList());
    
    // 2단계: Redis에 저장 (1단계 완료 후)
    RedisSyncResult redisResult = youtubeRedisSyncService.syncToRedis(userId, channelIds);
    
    return ResponseEntity.ok(Map.of(
        "channels", channels,
        "redisSync", redisResult
    ));
}
```

**실행 순서**:
1. `youtubeService.syncChannels()` 호출
2. MySQL 트랜잭션 커밋 완료
3. `youtubeRedisSyncService.syncToRedis()` 호출
4. Redis에 저장 (2, 3, 4단계)

#### 예시 2: YoutubeService 내부에서 연결

```java
// YoutubeService.java
@Autowired
private YoutubeRedisSyncService youtubeRedisSyncService;

@Transactional
public List<YoutubeChannelDto> syncChannels(Integer userId, boolean syncVideosEveryTime) {
    // ... 기존 코드 ...
    
    List<YoutubeChannelDto> out = new ArrayList<>();
    for (Channel ch : resp.getItems()) {
        // 1. MySQL에 저장
        channelMapper.upsert(dto);
        out.add(dto);
    }
    
    // 트랜잭션 커밋 후 Redis 저장
    // 주의: @Transactional 메서드 내에서는 트랜잭션이 커밋되기 전에 실행될 수 있음
    // 더 안전한 방법은 Controller에서 순차 호출하는 것
    
    return out;
}

// 별도 메서드로 분리 (트랜잭션 외부에서 호출)
public void syncChannelsWithRedis(Integer userId, boolean syncVideosEveryTime) {
    // 1단계: MySQL 저장
    List<YoutubeChannelDto> channels = syncChannels(userId, syncVideosEveryTime);
    
    // 2단계: Redis 저장
    List<String> channelIds = channels.stream()
        .map(YoutubeChannelDto::getYoutubeChannelId)
        .collect(Collectors.toList());
    
    youtubeRedisSyncService.syncToRedis(userId, channelIds);
}
```

### 데이터 흐름 (Data Flow)

```
1. 채널 ID 리스트 입력
   → List<String> channelIds
   
2. YouTube API에서 영상 정보 조회
   → List<Video> (Google 라이브러리)
   
3. DTO로 변환
   → List<YoutubeVideo>
   
4. 조회수 정렬 및 Top20 선택
   → List<YoutubeVideo> (20개)
   
5. Redis에 저장 (2단계)
   → channel:{channelId}:top20_video_ids (Set)
   
6. Redis에 저장 (3단계)
   → video:{videoId}:meta:json (String, JSON)
   
7. YouTube API에서 댓글 조회
   → List<CommentThread> (Google 라이브러리)
   
8. DTO로 변환
   → List<YoutubeComment>
   
9. JSON 배열 문자열로 변환
   → String (JSON 배열)
   
10. Redis에 저장 (4단계)
    → video:{videoId}:comments:json (String, JSON 배열)
```

---

## 🛡️ 보안과 에러 처리

### 보안 조치

#### 1. videoId Null/빈 문자열 검증

```java
if (videoId == null || videoId.isBlank()) {
    log.warn("영상 ID가 없습니다. 건너뜁니다: {}", video);
    continue;
}
```
- **목적**: Null Pointer Exception 방지
- **위치**: `YoutubeCommentServiceImpl.java`

#### 2. 부분 실패 방지

```java
// 기존 댓글 백업
String existingComments = stringRedisTemplate.opsForValue().get(redisKey);

try {
    // 댓글 조회 및 저장
    commentCount = fetchAndSaveComments(yt, videoId, redisKey);
    
    // 실패 시 복구
    if (commentCount == 0 && existingComments != null && !existingComments.isEmpty()) {
        stringRedisTemplate.opsForValue().set(redisKey, existingComments);
    }
} catch (Exception saveException) {
    // 저장 실패 시 기존 댓글 복구
    if (existingComments != null && !existingComments.isEmpty()) {
        stringRedisTemplate.opsForValue().set(redisKey, existingComments);
    }
    throw saveException;
}
```
- **목적**: 저장 실패 시 기존 데이터 보존
- **방법**: 저장 전 기존 데이터 백업, 실패 시 복구
- **위치**: `YoutubeCommentServiceImpl.java`

#### 3. 댓글 비활성화 에러 처리

```java
try {
    // 댓글 조회
} catch (GoogleJsonResponseException e) {
    String errorReason = extractErrorReason(e);
    if ("commentsDisabled".equals(errorReason) || "disabledComments".equals(errorReason)) {
        log.info("영상 {}의 댓글이 비활성화되어 있습니다", videoId);
    } else {
        log.error("영상 {}의 댓글 조회 실패: {} (reason: {})", videoId, e.getMessage(), errorReason);
    }
}
```
- **목적**: 댓글 비활성화 영상 처리
- **방법**: YouTube API 에러 reason 확인
- **위치**: `YoutubeCommentServiceImpl.java`

---

### 에러 처리 전략

#### 1. 한 영상 실패해도 다른 영상 계속 처리

```java
for (YoutubeVideo video : videos) {
    try {
        // 댓글 조회 및 저장
    } catch (Exception e) {
        log.error("영상 {}의 댓글 조회 실패: {}", video.getYoutubeVideoId(), e.getMessage());
        // 다음 영상으로 계속
    }
}
```
- **목적**: 부분 실패가 전체 실패로 이어지지 않게
- **방법**: try-catch로 각 영상 개별 처리

#### 2. 한 채널 실패해도 다른 채널 계속 처리

```java
for (String channelId : channelIds) {
    try {
        // 채널 영상 조회
    } catch (Exception e) {
        log.error("채널 {}의 영상 조회 실패: {}", channelId, e.getMessage());
        videosByChannel.put(channelId, Collections.emptyList());
        // 다음 채널로 계속
    }
}
```
- **위치**: `YoutubeVideoServiceImpl.java`

#### 3. 로깅 (Logging)

**로그 레벨**:
- `log.debug()`: 개발/디버깅용 상세 정보
- `log.info()`: 일반 정보 (정상 흐름)
- `log.warn()`: 경고 (문제는 아니지만 주의)
- `log.error()`: 에러 (처리 실패)

**예시**:
```java
log.debug("채널 {}의 {}개 영상 댓글 조회 시작", channelId, videos.size());
log.info("각 채널별 조회수 상위 20개 영상의 댓글 동기화 완료: userId={}, 총 댓글 수={}", userId, totalCommentCount);
log.warn("댓글 조회 실패 또는 댓글 없음. 기존 댓글 복구: {}", videoId);
log.error("영상 {}의 댓글 조회 실패: {}", videoId, e.getMessage());
```

---

## 📝 변경 이력

### 2024년 최신 변경사항

#### 1. 중복 코드 제거 및 성능 최적화 (최신)

**변경 내용**:
- **중복 API 호출 제거**: `YoutubeCommentServiceImpl`에서 `videoService.getTop20VideosByChannel()` 중복 호출 제거
- **공통 유틸리티 클래스 생성**: `buildClient()` 메서드를 `YoutubeApiClientUtil`로 분리

**변경 전**:
```java
// YoutubeCommentServiceImpl.java
Map<String, List<YoutubeVideo>> videosByChannel = 
    videoService.getTop20VideosByChannel(userId, channelIds);  // 중복 호출!

// YoutubeRedisSyncServiceImpl.java
Map<String, List<YoutubeVideo>> videosByChannel = 
    videoService.getTop20VideosByChannel(userId, channelIds);  // 동일한 호출!
```

**변경 후**:
```java
// YoutubeCommentService.java (인터페이스)
long syncTop20VideoComments(Integer userId, Map<String, List<YoutubeVideo>> videosByChannel);
// videosByChannel을 파라미터로 받아서 중복 호출 방지

// YoutubeCommentServiceImpl.java
@Override
public long syncTop20VideoComments(Integer userId, Map<String, List<YoutubeVideo>> videosByChannel) {
    // videosByChannel을 파라미터로 받아서 재사용
    // videoService 의존성 제거됨
}

// YoutubeRedisSyncServiceImpl.java
Map<String, List<YoutubeVideo>> videosByChannel = 
    videoService.getTop20VideosByChannel(userId, channelIds);  // 한 번만 호출
long totalCommentCount = commentService.syncTop20VideoComments(userId, videosByChannel);
// videosByChannel을 전달하여 중복 호출 방지
```

**buildClient() 중복 제거**:
```java
// 변경 전: YoutubeVideoServiceImpl과 YoutubeCommentServiceImpl에 각각 존재
private YouTube buildClient(String accessToken) throws Exception {
    return new YouTube.Builder(...).build();
}

// 변경 후: 공통 유틸리티 클래스로 분리
// YoutubeApiClientUtil.java
public static YouTube buildClient(String accessToken) throws Exception {
    return new YouTube.Builder(...).build();
}

// 사용: YoutubeApiClientUtil.buildClient(token)
```

**효과**:
- ✅ YouTube API 호출 횟수 감소 (성능 향상)
- ✅ 코드 중복 제거 (유지보수성 향상)
- ✅ 일관성 있는 API 클라이언트 생성

---

#### 2. YouTube 스크립트(자막) 기능 구현 (최신)

**추가된 파일**:
- `YoutubeTranscriptServiceImpl.java` (구현체)

**Python 코드 참고**:
```python
from youtube_transcript_api import YouTubeTranscriptApi
client = YouTubeTranscriptApi()
fetched = client.fetch(video_id, languages=['ko'])
transcript_text = "\n".join([entry['text'] for entry in fetched.to_raw_data()])
```

**Java 구현**:
- YouTube Data API v3 Captions API 사용
- 한국어 자막 우선 조회 (ko → en → 기타 순서)
- Redis에 텍스트 형식으로 저장

**Redis 저장 형식**:
```
Key: video:{video_id}:transcript
Type: String
Value: 스크립트 텍스트 원본
```

**주요 메서드**:
- `saveTranscriptToRedis(String videoId, Integer userId)`: 단일 비디오 자막 저장
- `saveTranscriptsToRedis(List<String> videoIds, Integer userId)`: 일괄 자막 저장
- `getTranscriptFromRedis(String videoId)`: Redis에서 자막 조회

**에러 처리**:
- 자막이 없는 경우: `captionNotFound` 에러 처리
- 언어별 우선순위: 한국어 → 영어 → 기타

---

#### 3. 이벤트 기반 캐시 제거

**삭제된 파일**:
- `CacheEventListener.java` (이벤트 리스너)
- `ChannelCacheEvent.java` (채널 캐시 이벤트)
- `VideoCacheEvent.java` (영상 캐시 이벤트)

**변경 내용**:
- `YoutubeService`에서 이벤트 발행 코드 제거
- `ApplicationEventPublisher` 의존성 제거
- `StringRedisTemplate` 필드 제거 (사용하지 않음)

**변경 이유**:
- 이전 이벤트 기반 캐시(`channel:{id}`, `video:{id}`, `user:{userId}:channels`)는 더 이상 사용하지 않음
- 현재 설계는 Top20 영상과 댓글만 Redis에 저장 (AI 서버용)

---

#### 2. 통합 서비스 추가 (YoutubeRedisSyncService)

**추가된 파일**:
- `YoutubeRedisSyncService.java` (인터페이스)
- `YoutubeRedisSyncServiceImpl.java` (구현체)
- `RedisSyncResult.java` (결과 DTO) - `dto/` 폴더에 위치

**목적**:
- 2, 3, 4단계를 순차적으로 실행하는 통합 진입점 제공
- 외부에서 호출하기 쉬운 단일 인터페이스 제공

**사용법**:
```java
YoutubeRedisSyncService syncService = ...;
List<String> channelIds = Arrays.asList("UCBA9XaL5wCdHnC5EmEzwrqw", ...);
RedisSyncResult result = syncService.syncToRedis(userId, channelIds);
```

---

#### 2. YoutubeVideo DTO 개선 (필드 정리 + 저장 방식 변경)

**필드 정리 (변경 전 → 변경 후)**:
```java
// 변경 전: 불필요한 필드 포함
private final String youtubeVideoId;
private final String title;
private final String thumbnailUrl;      // 제거됨
private final LocalDateTime publishedAt; // 제거됨
private final Long viewCount;           // 제거됨
private final Long likeCount;           // 제거됨
private final Long commentCount;        // 제거됨
private final String channelId;
private final List<String> tags;

// 변경 후: 필요한 4개 필드만
@JsonProperty("video_id")
private final String youtubeVideoId;
@JsonProperty("video_title")
private final String title;
@JsonProperty("channel_id")
private final String channelId;
@JsonProperty("video_tags")
private final List<String> tags;
```

**저장 방식 변경 (Map → DTO 직접 직렬화)**:
```java
// 변경 전: Map을 만들어서 저장
Map<String, Object> metadata = new HashMap<>();
metadata.put("channel_id", video.getChannelId());
metadata.put("video_id", video.getYoutubeVideoId());
metadata.put("video_title", video.getTitle());
metadata.put("video_tags", video.getTags());
String metaJson = objectMapper.writeValueAsString(metadata);

// 변경 후: DTO 객체를 직접 JSON으로 변환
String metaJson = objectMapper.writeValueAsString(video);
// @JsonProperty가 자동으로 필드명 변환
```

**변경 이유**:
- **일관성**: YoutubeComment와 동일한 방식으로 통일
- **코드 간소화**: Map 생성 코드 제거, 필드 매핑 코드 제거
- **유지보수성**: 필드 추가/변경 시 DTO만 수정하면 됨
- **타입 안정성**: Map의 `put()`은 타입 체크가 약함, DTO 필드는 컴파일 타임에 타입 체크

**YoutubeVideo.java 변경**:
- `@JsonProperty` 추가 (모든 필드)
- `@JsonCreator` 생성자 추가
- YoutubeComment와 동일한 구조로 통일

---

#### 3. 조회수 정렬 로직 변경

**변경 전**:
- DTO 변환 후 조회수 기준 정렬

**변경 후**:
- DTO 변환 전 Video 객체에서 조회수 기준 정렬

**변경 이유**:
- `YoutubeVideo` DTO에서 `viewCount` 필드 제거
- 정렬은 YouTube API의 `Video` 객체에서 직접 수행

---

## 🎓 용어 사전 (초보자용)

### 프로그래밍 용어

| 용어 | 의미 | 예시 |
|------|------|------|
| DTO | Data Transfer Object, 데이터를 담는 그릇 | YoutubeComment.java |
| 매퍼 (Mapper) | 데이터를 변환하는 변환기 | YoutubeCommentMapper.java |
| 서비스 (Service) | 실제 작업을 수행하는 일꾼 | YoutubeRedisSyncServiceImpl.java |
| 인터페이스 (Interface) | 계약서, 어떤 메서드를 구현해야 하는지 정의 | YoutubeRedisSyncService.java |
| 구현체 (Implementation) | 인터페이스의 실제 구현 | YoutubeRedisSyncServiceImpl.java |
| null | 값이 없음을 나타내는 특수 값 | `if (value == null)` |
| 빈 문자열 | 길이가 0인 문자열 | `""` |
| 페이지네이션 | 데이터를 여러 페이지로 나눠서 가져오기 | 100개씩 가져오기 |
| TTL | Time To Live, 데이터가 살아있는 시간 | 3일 후 자동 삭제 |
| OAuth | 사용자 권한 확인 프로토콜 | 로그인 토큰 |
| API | Application Programming Interface, 서버와 통신하는 방법 | YouTube API |
| 트랜잭션 | 여러 작업을 하나의 단위로 묶어서 실행 | `@Transactional` |

---

### Redis 용어

| 용어 | 의미 | 예시 |
|------|------|------|
| Key | 데이터를 찾기 위한 이름 (파일명 같은 것) | `video:td7kfwpTDcA:comments:json` |
| Value | 실제 저장되는 데이터 | `[{...}, {...}]` |
| String | 문자열 타입 | `"안녕하세요"` 또는 JSON |
| List | 순서가 있는 리스트 타입 | `["a", "b", "c"]` |
| Set | 중복 없는 집합 타입 | `{"a", "b", "c"}` |
| Hash | 필드-값 쌍으로 저장하는 타입 | `{name: "철수", age: 20}` |
| TTL | 데이터 만료 시간 | 3일 |
| expire | 데이터에 만료 시간 설정 | `expire key 259200` (3일) |

---

### Java 용어

| 용어 | 의미 | 예시 |
|------|------|------|
| @Annotation | 코드에 메타데이터 추가 | `@Service`, `@Getter` |
| Lombok | 보일러플레이트 코드 자동 생성 라이브러리 | `@Getter`, `@Builder` |
| Stream | 데이터 흐름 처리 (함수형 프로그래밍) | `.stream().map().filter()` |
| Lambda | 익명 함수 (화살표 함수) | `() -> value` |
| Builder | 객체 생성 패턴 | `.builder().field(value).build()` |
| final | 한 번 설정하면 변경 불가 | `private final String name;` |
| static | 클래스 레벨 (인스턴스 없이 사용 가능) | `static final int MAX = 100;` |

---

## 🤔 자주 묻는 질문 (FAQ)

### Q1: Redis와 MySQL의 차이는?

**MySQL (데이터베이스)**:
- 하드디스크에 저장 (영구 저장)
- 복잡한 쿼리 지원
- 느림 (상대적)
- 용도: 중요한 데이터 영구 보관

**Redis (캐시/임시 저장소)**:
- 메모리에 저장 (빠름)
- 단순한 키-값 저장
- 매우 빠름
- 용도: 임시 데이터, 캐시, AI 서버와 데이터 교환

---

### Q2: 왜 댓글을 List가 아닌 String으로 저장하나요?

**이전 (List 타입)**:
```
Key: video:abc:comments
Type: List
Value: [
  "{...}",  ← 각 요소가 개별 JSON 문자열
  "{...}",
  "{...}"
]
```

**현재 (String 타입)**:
```
Key: video:abc:comments:json
Type: String
Value: "[{...}, {...}, {...}]"  ← 전체가 하나의 JSON 배열 문자열
```

**이유**:
1. **AI 서버 호환**: Python/TypeScript는 JSON 배열을 직접 파싱
2. **데이터 일관성**: 전체를 한 번에 읽고 씀
3. **성능**: 네트워크 요청 1회로 전체 데이터 전송

---

### Q3: 왜 스네이크케이스(`comment_id`)를 사용하나요?

**Java 관행**: 카멜케이스 (`commentId`)
**Python/TypeScript 관행**: 스네이크케이스 (`comment_id`)

**우리 프로젝트**:
- Java 코드 내부: 카멜케이스 (`commentId`)
- JSON (AI 서버와 통신): 스네이크케이스 (`comment_id`)

**방법**:
```java
@JsonProperty("comment_id")
private final String commentId;
```
- Java에서는 `commentId`로 사용
- JSON 변환 시 `comment_id`로 변환

---

### Q4: TTL이 3일인 이유는?

**TTL (Time To Live)**: 데이터가 살아있는 시간

**3일로 설정한 이유**:
1. **데이터 신선도**: 댓글은 시간이 지나면 오래됨
2. **저장 공간 절약**: 오래된 데이터 자동 삭제
3. **재동기화**: 주기적으로 새로운 데이터로 업데이트

**동작**:
- 저장 시점부터 3일 카운트
- 3일 후 자동 삭제
- 재동기화 시 새로운 데이터로 덮어쓰기

---

### Q5: YouTube API를 여러 번 호출하는 이유는?

**YouTube API 제한**:
1. **댓글 API**: 한 번에 최대 100개
2. **비디오 API**: 한 번에 최대 50개

**해결 방법**:
- **페이지네이션**: 여러 번 호출하여 모든 데이터 수집
- **배치 처리**: 50개씩 묶어서 호출

**예시 (댓글 150개)**:
```
1차 호출: 1~100번 댓글
2차 호출: 101~150번 댓글
```

---

### Q6: 왜 의존성 주입을 사용하나요?

**의존성 주입 (Dependency Injection)**:
```java
@RequiredArgsConstructor
public class YoutubeRedisSyncServiceImpl {
    private final YoutubeVideoService videoService;
    private final YoutubeCommentService commentService;
    // ...
}
```

**장점**:
1. **테스트 용이**: Mock 객체로 테스트 가능
2. **코드 재사용**: 다른 곳에서도 같은 서비스 사용
3. **유지보수**: 구현체 교체 쉬움

**Spring이 자동으로**:
- 필요한 객체 생성
- 생성자에 주입
- 싱글톤으로 관리

---

### Q7: 통합 서비스(YoutubeRedisSyncService)가 왜 필요한가요?

**이전 방식**:
- 각 서비스를 개별적으로 호출해야 함
- 순서를 직접 관리해야 함

**현재 방식**:
- 단일 진입점 제공
- 순서 자동 관리 (2 → 3 → 4)
- 트랜잭션 보장

**사용 예시**:
```java
// 이전: 각각 호출
videoService.getTop20VideosByChannel(...);  // 2, 3단계
commentService.syncTop20VideoComments(...); // 4단계

// 현재: 한 번에 호출
syncService.syncToRedis(userId, channelIds); // 2, 3, 4단계 모두
```

---

### Q8: 1단계(MySQL 저장) 완료 후 2단계(Redis 저장)가 자동으로 실행되나요?

**답변**: **아니요, 현재는 자동으로 실행되지 않습니다.**

**현재 상태**:
- ✅ 1단계: `YoutubeService.syncChannels()` → MySQL에 저장
- ❌ 2단계: 자동 실행되지 않음
- ✅ 2, 3, 4단계: `YoutubeRedisSyncService.syncToRedis()`로 통합 구현됨

**연결 방법**:

**방법 1: Controller에서 순차 호출 (권장)**
```java
@PostMapping("/sync")
public ResponseEntity<?> syncChannels() {
    // 1단계: MySQL 저장
    List<YoutubeChannelDto> channels = youtubeService.syncChannels(userId, false);
    
    // 2단계: Redis 저장 (1단계 완료 후)
    List<String> channelIds = channels.stream()
        .map(YoutubeChannelDto::getYoutubeChannelId)
        .collect(Collectors.toList());
    
    youtubeRedisSyncService.syncToRedis(userId, channelIds);
    
    return ResponseEntity.ok(channels);
}
```

**방법 2: YoutubeService 내부에서 호출**
```java
@Transactional
public List<YoutubeChannelDto> syncChannels(Integer userId, boolean syncVideosEveryTime) {
    // ... MySQL 저장 ...
    
    // 트랜잭션 커밋 후 Redis 저장
    // 주의: @Transactional 메서드 내에서는 트랜잭션이 커밋되기 전에 실행될 수 있음
    List<String> channelIds = out.stream()
        .map(YoutubeChannelDto::getYoutubeChannelId)
        .collect(Collectors.toList());
    
    youtubeRedisSyncService.syncToRedis(userId, channelIds);
    
    return out;
}
```

**주의사항**:
- `@Transactional` 메서드 내에서 호출하면 트랜잭션이 커밋되기 전에 실행될 수 있습니다.
- 트랜잭션 커밋 후 실행하려면 Controller에서 순차 호출하는 것이 더 안전합니다.

---

## 🔗 참고 자료

### 프로젝트 내부

- **Python 참고 코드**: `channel_comment_fetcher.py`
- **API 문서**: `backend/API_DOCUMENTATION.md`
- **Frontend 가이드**: `backend/docs/frontend_guide.md`

### 외부 문서

- **YouTube API 공식 문서**: https://developers.google.com/youtube/v3
- **Redis 공식 문서**: https://redis.io/docs/
- **Spring Boot 공식 문서**: https://spring.io/projects/spring-boot
- **Jackson (JSON) 문서**: https://github.com/FasterXML/jackson-docs

---

## ✅ 체크리스트

코드를 이해했는지 확인하는 체크리스트입니다.

### 기본 개념
- [ ] DTO가 무엇인지 이해했습니다
- [ ] 매퍼가 무엇을 하는지 이해했습니다
- [ ] 서비스가 무엇을 하는지 이해했습니다
- [ ] Redis가 무엇인지 이해했습니다
- [ ] 통합 서비스의 역할을 이해했습니다

### Redis 데이터 구조
- [ ] 4가지 Redis 데이터 종류를 이해했습니다
- [ ] Set과 String 타입의 차이를 이해했습니다
- [ ] TTL이 무엇인지 이해했습니다

### 코드 흐름
- [ ] 전체 실행 흐름(1→2→3→4단계)을 이해했습니다
- [ ] 통합 서비스의 역할을 이해했습니다
- [ ] YouTube API 호출 시점을 알고 있습니다
- [ ] 댓글이 Redis에 저장되는 과정을 이해했습니다
- [ ] 영상 정보가 Redis에 저장되는 과정을 이해했습니다

### 보안과 에러
- [ ] null 체크가 왜 필요한지 이해했습니다
- [ ] 부분 실패 처리 방법을 이해했습니다
- [ ] 에러 로깅의 중요성을 이해했습니다

---

## 📞 도움이 필요하면?

- **코드 질문**: 팀 리더에게 문의
- **버그 발견**: GitHub Issues에 등록
- **개선 제안**: Pull Request 생성

---

**작성일**: 2024년  
**작성자**: AI Assistant  
**대상**: 1개월차 개발자  
**난이도**: ⭐⭐ 보통 (1개월차 개발자용)

---

끝까지 읽어주셔서 감사합니다! 🎉
