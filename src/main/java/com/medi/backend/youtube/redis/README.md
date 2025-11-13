# Redis 폴더 완전 가이드 (초보자용) 🚀

> **이 문서는 코딩을 배운지 일주일도 안 된 사람도 이해할 수 있도록 매우 상세하게 작성되었습니다.**

---

## 📑 목차

1. [폴더 구조](#-폴더-구조)
2. [전체 목적과 흐름](#-전체-목적과-흐름)
3. [Redis 데이터 구조](#-redis-데이터-구조)
4. [파일별 역할과 상세 설명](#-파일별-역할과-상세-설명)
5. [코드 실행 흐름](#-코드-실행-흐름)
6. [보안과 에러 처리](#-보안과-에러-처리)
7. [변경 이력](#-변경-이력)

---

## 📁 폴더 구조

```
backend/src/main/java/com/medi/backend/youtube/redis/
├── dto/                                    # 데이터 구조 정의
│   ├── YoutubeComment.java                 # 댓글 데이터 구조 ⭐
│   └── YoutubeVideo.java                   # 영상 데이터 구조 ⭐
│
├── mapper/                                 # 데이터 변환기
│   ├── YoutubeCommentMapper.java           # YouTube API → YoutubeComment 변환
│   └── YoutubeVideoMapper.java             # YouTube API → YoutubeVideo 변환
│
├── service/                                # 실제 작업 수행
│   ├── YoutubeCommentService.java          # 댓글 서비스 인터페이스
│   ├── YoutubeCommentServiceImpl.java      # 댓글 저장 구현 ⭐⭐⭐ 핵심!
│   ├── YoutubeVideoService.java            # 영상 서비스 인터페이스
│   ├── YoutubeVideoServiceImpl.java        # 영상 조회 구현 ⭐⭐
│   └── YoutubeTranscriptService.java      # 스크립트 서비스 (인터페이스만)
│
├── channel_comment_fetcher.py              # Python 참고 코드
└── README.md                               # 이 문서 ✨
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
- **예시**: `YoutubeCommentServiceImpl`은 댓글을 YouTube에서 가져와 Redis에 저장

---

## 🎯 전체 목적과 흐름

### 목적
YouTube API에서 사용자의 채널별 조회수 상위 20개 영상의 댓글을 가져와 **AI 서버가 사용하기 편한 형태로 Redis에 저장**합니다.

### 전체 흐름 (간단 버전)

```
1. 사용자 로그인
   ↓
2. 사용자의 YouTube 채널 목록 조회 (DB에서)
   ↓
3. 각 채널마다 조회수 상위 20개 영상 찾기 (YouTube API)
   ↓
4. 상위 20개 영상의 댓글 수집 (YouTube API)
   ↓
5. Redis에 저장 (AI 서버가 읽을 수 있게)
   ↓
6. 완료!
```

---

## 💾 Redis 데이터 구조

Redis는 **키-값 저장소**입니다. 파일 시스템처럼 파일명(키)으로 데이터(값)를 저장하고 찾습니다.

### 저장되는 데이터 종류

#### 1. 채널의 비디오 ID 목록 (상위 20개)

**Redis 데이터 타입**: `Set` (집합)

```
Key: channel:{channel_id}:top20_video_ids
Type: Set
Value: ["td7kfwpTDcA", "o6Ju5r82EwA", "UubUGelYJCU", ...]

예시 (채널: 튜브김민교):
Key: channel:UCBA9XaL5wCdHnC5EmEzwrqw:top20_video_ids
Value: ["td7kfwpTDcA", "o6Ju5r82EwA", "UubUGelYJCU", ...]
```

**Set을 사용하는 이유**:
- 중복 제거
- 빠른 검색 (O(1) 시간 복잡도)
- AI 서버가 "이 비디오가 Top20에 있나?" 빠르게 확인 가능

---

#### 2. 개별 비디오 메타데이터

**Redis 데이터 타입**: `String` (JSON 형식)

```
Key: video:{video_id}:meta:json
Type: String
Value: JSON 객체

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

**사용 목적**:
- AI 서버가 비디오 정보를 빠르게 조회
- 채널 ID, 제목, 태그 등 메타데이터 제공
- YouTube API를 다시 호출하지 않아도 됨

---

#### 3. 개별 비디오 스크립트 원본 (선택적)

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

**현재 상태**: 인터페이스만 정의됨 (구현 예정)

---

#### 4. 개별 비디오 댓글 모음 ⭐⭐⭐ 핵심!

**Redis 데이터 타입**: `String` (JSON 배열)

```
Key: video:{video_id}:comments:json
Type: String
Value: JSON 배열 (댓글 리스트)

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

**저장 방식 변경 이력**:
- **이전**: List 타입, 각 댓글이 개별 요소
- **현재**: String 타입, 전체 댓글을 하나의 JSON 배열로 저장
- **변경 이유**: AI 서버(Python/TypeScript)와의 호환성

---

## 📄 파일별 역할과 상세 설명

### 1. YoutubeComment.java (DTO)

#### 역할
댓글 한 개의 정보를 담는 데이터 구조

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

### 2. YoutubeVideo.java (DTO)

#### 역할
영상 한 개의 정보를 담는 **내부용** 데이터 구조

**중요**: 이 DTO는 Redis에 직접 저장되지 않습니다!

#### 필드 설명

| 필드명 | 데이터 타입 | 설명 | 예시 |
|--------|------------|------|------|
| youtubeVideoId | String | 영상 ID | "td7kfwpTDcA" |
| title | String | 영상 제목 | "시작보다 어려운 끝" |
| thumbnailUrl | String | 썸네일 URL | "https://i.ytimg.com/vi/..." |
| publishedAt | LocalDateTime | 게시 시간 | 2021-04-18T10:00:00 |
| viewCount | Long | 조회수 | 1234567 |
| likeCount | Long | 좋아요 수 | 12345 |
| commentCount | Long | 댓글 수 | 1234 |
| channelId | String | 채널 ID | "UCBA9XaL5..." |
| tags | List\<String\> | 태그 리스트 | ["김민교", "츠예나"] |

#### 코드 설명 (초보자용)

```java
@Getter
@Builder
public class YoutubeVideo {
    private final String youtubeVideoId;
    private final String title;
    private final String channelId;
    private final List<String> tags;
    // ... 기타 필드
}
```

**용어 설명**:
- `@Getter`: Lombok 라이브러리. 자동으로 `getYoutubeVideoId()` 같은 메서드 생성
- `@Builder`: 객체 생성을 쉽게 해주는 패턴 (`YoutubeVideo.builder().youtubeVideoId("abc").build()`)
- `private final`: 한 번 설정하면 변경 불가능 (불변 객체)

**왜 `@JsonProperty`가 없나요?**
- 이 DTO는 Redis에 직접 저장되지 않음
- `YoutubeVideoServiceImpl.saveVideoMetadataToRedis()`에서 **Map을 직접 만들어서** 저장
- Map의 Key를 스네이크 케이스로 직접 지정하므로 `@JsonProperty` 불필요

**Redis 저장 방식**:
```java
// YoutubeVideoServiceImpl.saveVideoMetadataToRedis() 메서드
Map<String, Object> metadata = new HashMap<>();
metadata.put("channel_id", video.getChannelId());      // 직접 스네이크 케이스 지정
metadata.put("video_id", video.getYoutubeVideoId());
metadata.put("video_title", video.getTitle());
metadata.put("video_tags", video.getTags());
// 필요한 4개 필드만 선택하여 저장
```

**YoutubeComment와의 차이**:
- `YoutubeComment`: DTO 객체를 직접 JSON으로 변환 → `@JsonProperty` 필요
- `YoutubeVideo`: Map을 만들어서 저장 → `@JsonProperty` 불필요

---

### 3. YoutubeCommentMapper.java (매퍼)

#### 역할
YouTube API의 Comment 객체 → YoutubeComment DTO 변환

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

### 4. YoutubeVideoMapper.java (매퍼)

#### 역할
YouTube API의 Video 객체 → YoutubeVideo DTO 변환

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
3. 썸네일: `video.getSnippet().getThumbnails().getDefault().getUrl()`
4. 조회수: `video.getStatistics().getViewCount()`
5. 태그: `video.getSnippet().getTags()`

**Null 체크**:
- YouTube API는 많은 필드가 null일 수 있음
- 모든 필드에 대해 null 체크 필요
- null이면 기본값 사용 (예: 조회수 0, 태그 빈 리스트)

---

### 5. YoutubeCommentServiceImpl.java (서비스) ⭐⭐⭐ 핵심!

#### 역할
YouTube API에서 댓글을 가져와 Redis에 저장하는 **핵심 로직**

#### 주요 메서드

##### 1) syncTop20VideoComments(Integer userId)

**목적**: 사용자의 채널별 조회수 상위 20개 영상의 댓글 동기화

**실행 흐름 (단계별)**:

```
1. 각 채널마다 조회수 상위 20개 영상 조회
   ↓ (YoutubeVideoService 호출)
   
2. OAuth 토큰 가져오기
   ↓ (YoutubeOAuthService 호출)
   
3. YouTube API 클라이언트 생성
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
// 1. 각 채널마다 조회수 상위 20개 영상 조회
Map<String, List<YoutubeVideo>> videosByChannel = 
    videoService.getTop20VideosByChannel(userId);
```
- `Map<String, List<YoutubeVideo>>`: 맵(사전) 구조
- Key: 채널 ID (String)
- Value: 영상 리스트 (List<YoutubeVideo>)
- 예: `{"채널A": [영상1, 영상2, ...], "채널B": [영상1, 영상2, ...]}`

```java
// 2. OAuth 토큰 가져오기
String token = youtubeOAuthService.getValidAccessToken(userId);
YouTube yt = buildClient(token);
```
- OAuth: 사용자 권한 확인 (로그인 토큰)
- YouTube API 호출하려면 토큰 필요

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

### 6. YoutubeVideoServiceImpl.java (서비스) ⭐⭐

#### 역할
YouTube API에서 영상 정보를 가져와 Redis에 저장

#### 주요 메서드

##### 1) getTop20VideosByChannel(Integer userId)

**목적**: 사용자의 각 채널별 조회수 상위 20개 영상 조회

**실행 흐름 (단계별)**:

```
1. OAuth 토큰 가져오기
   ↓
2. 사용자의 채널 목록 조회 (DB에서)
   ↓
3. 각 채널마다 반복:
   3-1. 채널의 영상 목록 조회 (Search API)
   3-2. 비디오 ID 목록 추출
   3-3. 비디오 상세 정보 조회 (Videos API)
   3-4. Redis DTO로 변환
   3-5. 조회수 기준 정렬 → 상위 20개 선택
   3-6. Redis에 저장:
        - Top20 비디오 ID Set 저장
        - 개별 비디오 메타데이터 저장
   ↓
4. Map<채널ID, 영상리스트> 반환
```

**코드 설명 (핵심 부분)**:

```java
// 1. 사용자의 채널 목록 조회
List<YoutubeChannelDto> channels = channelMapper.findByUserId(userId);
```
- DB에서 사용자가 등록한 YouTube 채널 조회

```java
// 2. 각 채널마다 처리
Map<String, List<YoutubeVideo>> videosByChannel = new HashMap<>();
for (YoutubeChannelDto channel : channels) {
    // ...
}
```
- `HashMap`: 키-값 쌍 저장 (사전처럼)

```java
// 3. 채널의 영상 목록 조회
List<SearchResult> searchResults = fetchChannelVideos(yt, channelId);
```
- YouTube Search API 호출
- 채널의 모든 영상 목록 가져오기

```java
// 4. 비디오 ID 목록 추출
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
// 5. 비디오 상세 정보 조회
List<Video> videos = fetchVideoDetails(yt, videoIds);
```
- YouTube Videos API 호출
- 조회수, 좋아요 수 등 통계 정보 포함

```java
// 6. 조회수 기준 정렬 → 상위 20개
List<YoutubeVideo> top20Videos = channelVideos.stream()
    .sorted(Comparator.comparing(
        YoutubeVideo::getViewCount,
        Comparator.nullsLast(Comparator.reverseOrder())
    ))
    .limit(20)
    .collect(Collectors.toList());
```
- `.sorted()`: 정렬
- `Comparator.comparing()`: 정렬 기준 (조회수)
- `nullsLast()`: null 값을 마지막으로
- `reverseOrder()`: 내림차순 (높은 순)
- `.limit(20)`: 상위 20개만

```java
// 7. Redis에 저장
saveTop20VideoIdsToRedis(channelId, top20Videos);
saveVideoMetadataToRedis(top20Videos);
```

---

##### 2) fetchChannelVideos(YouTube yt, String channelId)

**목적**: 특정 채널의 모든 영상 목록 조회 (비디오 ID만)

**코드 설명**:

```java
// YouTube Search API 요청
YouTube.Search.List searchReq = yt.search().list(Arrays.asList("snippet"));
searchReq.setChannelId(channelId);
searchReq.setMaxResults(50L);
searchReq.setOrder("date");
searchReq.setType(Arrays.asList("video"));
```
- `search().list()`: 검색 API
- `channelId`: 특정 채널의 영상만
- `maxResults=50`: 한 페이지당 최대 50개
- `order="date"`: 최신순 정렬
- `type="video"`: 비디오만 (재생목록 제외)

```java
// ⭐ 실제 YouTube Search API 호출
SearchListResponse response = searchReq.execute();
```

---

##### 3) fetchVideoDetails(YouTube yt, List<String> videoIds)

**목적**: 비디오 ID 목록으로 상세 정보 조회 (조회수 포함)

**코드 설명**:

```java
// 50개씩 분할 (YouTube API 제한)
for (int i = 0; i < videoIds.size(); i += 50) {
    int end = Math.min(i + 50, videoIds.size());
    List<String> batch = videoIds.subList(i, end);
    // ...
}
```
- YouTube API는 한 번에 최대 50개까지만 조회 가능
- 50개씩 나눠서 여러 번 호출

```java
// YouTube Videos API 요청
YouTube.Videos.List req = yt.videos().list(
    Arrays.asList("snippet", "statistics")
);
req.setId(batch);
```
- `videos().list()`: 비디오 상세 정보 API
- `part="snippet,statistics"`: 기본 정보 + 통계
- `id`: 비디오 ID 목록

```java
// ⭐ 실제 YouTube Videos API 호출
VideoListResponse resp = req.execute();
```

---

##### 4) saveTop20VideoIdsToRedis(String channelId, List<YoutubeVideo> top20Videos)

**목적**: 채널별 Top20 비디오 ID Set을 Redis에 저장

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

##### 5) saveVideoMetadataToRedis(List<YoutubeVideo> videos)

**목적**: 개별 비디오 메타데이터를 Redis에 저장

**코드 설명**:

```java
for (YoutubeVideo video : videos) {
    String metaKey = "video:" + videoId + ":meta:json";
    
    // 메타데이터 JSON 생성
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("channel_id", video.getChannelId());
    metadata.put("video_id", video.getYoutubeVideoId());
    metadata.put("video_title", video.getTitle());
    metadata.put("video_tags", video.getTags() != null ? video.getTags() : Collections.emptyList());
    
    // JSON 문자열로 변환
    String metaJson = objectMapper.writeValueAsString(metadata);
    
    // Redis에 저장
    stringRedisTemplate.opsForValue().set(metaKey, metaJson);
    stringRedisTemplate.expire(metaKey, Duration.ofDays(3));
}
```
- `Map`: 키-값 쌍으로 데이터 저장
- `objectMapper.writeValueAsString()`: Map → JSON 문자열
- `opsForValue().set()`: String 타입으로 저장

---

## 🔄 코드 실행 흐름

### 전체 호출 순서

```
사용자가 로그인 및 채널 조회 요청
   ↓
YoutubeCommentServiceImpl.syncTop20VideoComments(userId) 호출
   ↓
   ├─→ YoutubeVideoServiceImpl.getTop20VideosByChannel(userId)
   │      ↓
   │      ├─→ YoutubeOAuthService.getValidAccessToken(userId) [OAuth 토큰 조회]
   │      ├─→ YoutubeChannelMapper.findByUserId(userId) [DB에서 채널 조회]
   │      ├─→ fetchChannelVideos(yt, channelId) [YouTube Search API 호출]
   │      ├─→ fetchVideoDetails(yt, videoIds) [YouTube Videos API 호출]
   │      ├─→ YoutubeVideoMapper.toRedisVideo(video, channelId) [DTO 변환]
   │      ├─→ saveTop20VideoIdsToRedis(channelId, top20Videos) [Redis Set 저장]
   │      └─→ saveVideoMetadataToRedis(top20Videos) [Redis String 저장]
   │
   ├─→ YoutubeOAuthService.getValidAccessToken(userId) [OAuth 토큰 재조회]
   └─→ 각 영상마다:
          ↓
          fetchAndSaveComments(yt, videoId, redisKey)
             ↓
             ├─→ YouTube CommentThreads API 호출 (페이지네이션)
             ├─→ YoutubeCommentMapper.toRedisComment(comment, parentId) [DTO 변환]
             └─→ saveCommentsToRedis(redisKey, allComments) [Redis String 저장]
```

### 데이터 흐름 (Data Flow)

```
1. DB에서 채널 목록 조회
   → List<YoutubeChannelDto>
   
2. YouTube API에서 영상 정보 조회
   → List<Video>
   
3. DTO로 변환
   → List<YoutubeVideo>
   
4. 조회수 정렬 및 Top20 선택
   → List<YoutubeVideo> (20개)
   
5. Redis에 저장
   → channel:{channelId}:top20_video_ids (Set)
   → video:{videoId}:meta:json (String, JSON)
   
6. YouTube API에서 댓글 조회
   → List<CommentThread>
   
7. DTO로 변환
   → List<YoutubeComment>
   
8. JSON 배열 문자열로 변환
   → String (JSON 배열)
   
9. Redis에 저장
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
- **위치**: `YoutubeCommentServiceImpl.java` (86-89줄)

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
- **위치**: `YoutubeCommentServiceImpl.java` (96-121줄)

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
- **위치**: `YoutubeCommentServiceImpl.java` (122-132줄)

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
for (YoutubeChannelDto channel : channels) {
    try {
        // 채널 영상 조회
    } catch (Exception e) {
        log.error("채널 {}의 영상 조회 실패: {}", channel.getYoutubeChannelId(), e.getMessage());
        videosByChannel.put(channel.getYoutubeChannelId(), Collections.emptyList());
        // 다음 채널로 계속
    }
}
```
- **위치**: `YoutubeVideoServiceImpl.java` (129-132줄)

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

### 2024년 변경사항

#### 1. Redis 데이터 구조 완전 재설계

**변경 전**:
```
Key: video:{videoId}:comments
Type: List
Value: [
  "{\"commentId\":\"...\",\"text\":\"...\",...}",
  "{\"commentId\":\"...\",\"text\":\"...\",...}"
]
```

**변경 후**:
```
Key: video:{video_id}:comments:json
Type: String
Value: "[{\"comment_id\":\"...\",\"text_original\":\"...\",...}, {...}]"
```

**변경 이유**:
- AI 서버(Python/TypeScript)와의 호환성
- 데이터 일관성 개선
- 저장 방식 통일

---

#### 2. YoutubeComment DTO 필드명 변경

**변경 전 (Java 카멜케이스)**:
```java
private final String commentId;
private final String text;
private final String author;
```

**변경 후 (JSON 스네이크케이스)**:
```java
@JsonProperty("comment_id")
private final String commentId;

@JsonProperty("text_original")
private final String textOriginal;

@JsonProperty("author_name")
private final String authorName;
```

**변경 이유**:
- Python/TypeScript 스네이크케이스 규칙 준수
- AI 서버와의 데이터 호환성

---

#### 3. 불필요한 필드 제거

**제거된 필드**:
- `parentId` (부모 댓글 ID)
- `authorChannelId` (작성자 채널 ID)
- `updatedAt` (수정 시간)

**유지된 필드**:
- `comment_id` (댓글 ID)
- `text_original` (댓글 원본 텍스트)
- `author_name` (작성자 이름)
- `like_count` (좋아요 수)
- `published_at` (작성 시간)

**변경 이유**:
- AI 분석에 불필요한 데이터 제거
- 데이터 크기 최소화

---

#### 4. 채널별 Top20 비디오 ID Set 추가

**새로 추가**:
```
Key: channel:{channel_id}:top20_video_ids
Type: Set
Value: ["video_id_1", "video_id_2", ...]
```

**목적**:
- AI 서버가 Top20 비디오 목록 빠르게 조회
- O(1) 시간 복잡도로 비디오 ID 존재 확인

**구현 위치**:
- `YoutubeVideoServiceImpl.saveTop20VideoIdsToRedis()`

---

#### 5. 비디오 메타데이터 별도 저장

**새로 추가**:
```
Key: video:{video_id}:meta:json
Type: String (JSON)
Value: {
  "channel_id": "...",
  "video_id": "...",
  "video_title": "...",
  "video_tags": [...]
}
```

**목적**:
- AI 서버가 비디오 정보 빠르게 조회
- YouTube API 재호출 불필요

**구현 위치**:
- `YoutubeVideoServiceImpl.saveVideoMetadataToRedis()`

---

#### 6. YoutubeVideo DTO에 필드 추가

**추가된 필드**:
```java
private final String channelId;        // 채널 ID
private final List<String> tags;      // 비디오 태그 리스트
```

**변경 이유**:
- Python 코드(`channel_comment_fetcher.py`) 참고
- AI 분석용 추가 정보 제공

---

#### 7. 비디오 스크립트 저장 구조 추가

**인터페이스 추가**:
- `YoutubeTranscriptService.java`

**Redis 저장 형식**:
```
Key: video:{video_id}:transcript
Type: String
Value: 스크립트 텍스트 원본
```

**현재 상태**: 인터페이스만 정의, 구현 예정

---

## 🎓 용어 사전 (초보자용)

### 프로그래밍 용어

| 용어 | 의미 | 예시 |
|------|------|------|
| DTO | Data Transfer Object, 데이터를 담는 그릇 | YoutubeComment.java |
| 매퍼 (Mapper) | 데이터를 변환하는 변환기 | YoutubeCommentMapper.java |
| 서비스 (Service) | 실제 작업을 수행하는 일꾼 | YoutubeCommentServiceImpl.java |
| 인터페이스 (Interface) | 계약서, 어떤 메서드를 구현해야 하는지 정의 | YoutubeCommentService.java |
| 구현체 (Implementation) | 인터페이스의 실제 구현 | YoutubeCommentServiceImpl.java |
| null | 값이 없음을 나타내는 특수 값 | `if (value == null)` |
| 빈 문자열 | 길이가 0인 문자열 | `""` |
| 페이지네이션 | 데이터를 여러 페이지로 나눠서 가져오기 | 100개씩 가져오기 |
| TTL | Time To Live, 데이터가 살아있는 시간 | 3일 후 자동 삭제 |
| OAuth | 사용자 권한 확인 프로토콜 | 로그인 토큰 |
| API | Application Programming Interface, 서버와 통신하는 방법 | YouTube API |

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
public class YoutubeCommentServiceImpl {
    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeVideoService videoService;
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

### Redis 데이터 구조
- [ ] 4가지 Redis 데이터 종류를 이해했습니다
- [ ] Set과 String 타입의 차이를 이해했습니다
- [ ] TTL이 무엇인지 이해했습니다

### 코드 흐름
- [ ] 전체 실행 흐름을 이해했습니다
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
**대상**: 코딩 초보자 (1주차)  
**난이도**: ⭐ 매우 쉬움 (초보자용)

---

끝까지 읽어주셔서 감사합니다! 🎉
