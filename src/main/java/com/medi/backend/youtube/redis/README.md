# Redis 폴더 코드 완전 가이드 (초보자용)

> **이 문서는 코딩을 배운지 일주일도 안 된 사람도 이해할 수 있도록 매우 상세하게 작성되었습니다.**

---

## 📁 폴더 구조

```
backend/src/main/java/com/medi/backend/youtube/redis/
├── dto/                                    # 데이터 전송 객체 (Data Transfer Object)
│   ├── YoutubeComment.java                 # 댓글 데이터 구조 정의
│   └── YoutubeVideo.java                   # 영상 데이터 구조 정의
│
├── mapper/                                 # 데이터 변환 로직
│   ├── YoutubeCommentMapper.java           # YouTube API → YoutubeComment 변환
│   └── YoutubeVideoMapper.java             # YouTube API → YoutubeVideo 변환
│
├── service/                                # 비즈니스 로직 (실제 작업 수행)
│   ├── YoutubeCommentService.java          # 댓글 서비스 인터페이스 (계약서)
│   ├── YoutubeCommentServiceImpl.java     # 댓글 저장 구현체 ⭐ 메인!
│   ├── YoutubeVideoService.java            # 영상 서비스 인터페이스 (계약서)
│   └── YoutubeVideoServiceImpl.java        # 영상 조회 구현체
│
├── channel_comment_fetcher.py              # Python 참고 코드 (참고용)
└── README.md                               # 이 문서
```

### 📝 폴더 구조 설명

**dto (Data Transfer Object)**
- **의미**: 데이터를 담는 그릇 같은 것
- **역할**: YouTube API에서 받은 데이터를 우리가 사용하기 편한 형태로 만든 것
- **예시**: `YoutubeComment`는 댓글 정보를 담는 상자

**mapper (매퍼)**
- **의미**: 데이터를 변환하는 변환기
- **역할**: YouTube API에서 받은 복잡한 데이터를 우리가 만든 간단한 형태로 바꿔주는 것
- **예시**: `YoutubeCommentMapper`는 YouTube 댓글을 `YoutubeComment`로 바꿔줌

**service (서비스)**
- **의미**: 실제 작업을 수행하는 일꾼
- **역할**: 비즈니스 로직을 처리하는 곳 (예: 댓글 가져오기, 저장하기)
- **예시**: `YoutubeCommentServiceImpl`은 댓글을 가져와서 Redis에 저장함

---

## 🎯 전체 목적

이 폴더의 코드는 **YouTube API에서 영상과 댓글 데이터를 가져와서 Redis에 저장**하는 역할을 합니다.

**중요**: 이 코드는 **DB(MySQL)에 저장하지 않습니다**. Redis에만 저장합니다.

### 왜 Redis에 저장하나요?
- **빠른 속도**: Redis는 메모리에 저장되어 매우 빠름
- **임시 데이터**: 댓글은 자주 바뀌므로 임시로 저장
- **TTL (Time To Live)**: 3일 후 자동 삭제되어 오래된 데이터가 쌓이지 않음

---

## 📊 전체 데이터 흐름 (큰 그림)

```
사용자가 로그인하고 채널을 조회함
    ↓
YoutubeCommentServiceImpl.syncTop20VideoComments(userId) 호출
    ↓
1단계: 조회수 상위 20개 영상 가져오기
    YoutubeVideoService.getTop20VideosByChannel(userId)
    ↓
2단계: 각 영상마다 댓글 조회
    YouTube API 호출 → 댓글 데이터 받기
    ↓
3단계: 댓글 데이터 변환
    YouTube Comment 객체 → YoutubeComment DTO
    ↓
4단계: Redis에 저장
    YoutubeComment DTO → JSON 문자열 → Redis List에 저장
```

---

## 📝 각 파일의 역할 (상세 설명)

### 1. DTO (Data Transfer Object) - 데이터 그릇

#### `dto/YoutubeComment.java`
**역할**: 댓글 데이터를 담는 상자

**필드 설명**:
- `commentId` (String): 댓글 고유 ID (예: "Ugy123abc")
- `parentId` (String): 부모 댓글 ID 
  - `null`이면 최상위 댓글 (원댓글)
  - 값이 있으면 대댓글 (답글)
- `text` (String): 댓글 내용 (예: "좋은 영상이네요!")
- `author` (String): 작성자 이름 (예: "홍길동")
- `authorChannelId` (String, null 가능): 작성자 채널 ID (선택적)
- `likeCount` (Long, null 가능): 좋아요 수 (예: 100)
- `publishedAt` (LocalDateTime): 작성 시간 (예: 2024-01-01 12:00:00)
- `updatedAt` (LocalDateTime, null 가능): 수정 시간 (null이면 수정 안 함)

**사용 시점**: YouTube API의 Comment 객체를 변환한 후, Redis에 저장하기 전

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/dto/YoutubeComment.java`

---

#### `dto/YoutubeVideo.java`
**역할**: 영상 데이터를 담는 상자

**필드 설명**:
- `youtubeVideoId` (String): 영상 고유 ID (예: "dQw4w9WgXcQ")
- `title` (String): 영상 제목 (예: "Never Gonna Give You Up")
- `thumbnailUrl` (String): 썸네일 이미지 URL
- `publishedAt` (LocalDateTime): 발행 시간
- `viewCount` (Long, null 가능): 조회수 (예: 1000000)
- `likeCount` (Long, null 가능): 좋아요 수 (예: 50000)
- `commentCount` (Long, null 가능): 댓글 수 (예: 1000)
- `channelId` (String): 채널 ID (Python 코드의 channel_id) ⭐ 추가됨
- `tags` (List<String>, null 가능): 비디오 태그 리스트 (Python 코드의 video_tags) ⭐ 추가됨

**중요**: 
- `viewCount`, `likeCount`, `commentCount`는 `Long` 타입이고 null을 허용합니다.
  - **이유**: YouTube API에서 통계 정보가 없을 수 있음 (비공개 영상, 삭제된 영상 등)
  - **DB 스키마 변경**: 2024년에 `youtube_videos` 테이블의 통계 컬럼들이 NULL 허용으로 변경됨
- `channelId`와 `tags`는 Python 코드(`channel_comment_fetcher.py`)의 `cleaned_video_info` 구조를 참고하여 추가됨

**사용 시점**: YouTube API의 Video 객체를 변환한 후, 조회수 기준 정렬에 사용

**참고**: `YoutubeVideo` 객체는 **Redis에 저장되지 않습니다**. 메모리에서만 사용되며, Redis에 저장되는 것은 `YoutubeComment` 객체뿐입니다.

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/dto/YoutubeVideo.java`

---

### 2. Mapper (매퍼) - 데이터 변환기

#### `mapper/YoutubeCommentMapper.java`
**역할**: YouTube API의 Comment 객체를 우리가 만든 YoutubeComment DTO로 변환

**주요 메서드**:
- `toRedisComment(Comment comment, String parentId)`: Comment → YoutubeComment 변환

**변환 과정** (13-58줄):
1. **입력 검증** (14-16줄): `comment`가 null이거나 `snippet`이 null이면 null 반환
2. **기본 정보 추출** (18-20줄):
   - `commentId`: `comment.getId()` - 댓글 ID
   - `text`: `comment.getSnippet().getTextDisplay()` - 댓글 내용
   - `author`: `comment.getSnippet().getAuthorDisplayName()` - 작성자 이름
3. **선택적 정보 추출** (22-32줄):
   - `authorChannelId`: 작성자 채널 ID (null 가능)
   - `likeCount`: 좋아요 수 (null 가능)
4. **시간 정보 변환** (34-46줄):
   - `publishedAt`: 발행 시간 (RFC3339 형식 → LocalDateTime)
   - `updatedAt`: 수정 시간 (null 가능)
5. **DTO 생성** (48-57줄): `YoutubeComment.builder()`로 객체 생성

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/mapper/YoutubeCommentMapper.java`

**내부 호출 흐름**:
```
toRedisComment() 호출
    ↓
comment == null 체크
    ↓
commentId, text, author 추출
    ↓
authorChannelId, likeCount 추출 (null 체크)
    ↓
publishedAt, updatedAt 변환 (RFC3339 → LocalDateTime)
    ↓
YoutubeComment.builder()로 객체 생성
    ↓
YoutubeComment 객체 반환
```

**에러 처리**:
- `comment == null` 또는 `comment.getSnippet() == null`: null 반환 (에러 없이 처리)
- `publishedAt` 또는 `updatedAt` 파싱 실패: null로 설정 (에러 없이 처리)

---

#### `mapper/YoutubeVideoMapper.java`
**역할**: YouTube API의 Video 객체를 우리가 만든 YoutubeVideo DTO로 변환

**주요 메서드**:
- `toRedisVideo(Video video)`: Video → YoutubeVideo 변환

**변환 과정** (13-54줄):
1. **입력 검증** (14-16줄): `video`가 null이면 null 반환
2. **기본 정보 추출** (18-25줄):
   - `videoId`: `video.getId()` - 영상 ID
   - `title`: `video.getSnippet().getTitle()` - 영상 제목
   - `thumbnailUrl`: 썸네일 URL (null 체크)
3. **시간 정보 변환** (27-31줄):
   - `publishedAt`: 발행 시간 (RFC3339 → LocalDateTime)
4. **통계 정보 추출** (33-43줄):
   - `viewCount`, `likeCount`, `commentCount`: 모두 null 가능
   - `BigInteger` → `Long` 변환
5. **DTO 생성** (45-53줄): `YoutubeVideo.builder()`로 객체 생성

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/mapper/YoutubeVideoMapper.java`

**내부 호출 흐름**:
```
toRedisVideo() 호출
    ↓
video == null 체크
    ↓
videoId, title, thumbnailUrl 추출
    ↓
publishedAt 변환 (RFC3339 → LocalDateTime)
    ↓
viewCount, likeCount, commentCount 추출 (null 체크)
    ↓
YoutubeVideo.builder()로 객체 생성
    ↓
YoutubeVideo 객체 반환
```

**에러 처리**:
- `video == null`: null 반환
- `video.getSnippet() == null`: title, thumbnailUrl, publishedAt은 null로 설정
- `video.getStatistics() == null`: viewCount, likeCount, commentCount는 null로 설정

---

### 3. Service (서비스) - 실제 작업 수행

#### `service/YoutubeCommentService.java`
**역할**: 댓글 서비스 인터페이스 (계약서)

**주요 메서드**:
- `syncTop20VideoComments(Integer userId)`: 각 채널별 조회수 상위 20개 영상의 댓글을 Redis에 저장

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeCommentService.java`

**설명**: 인터페이스는 "무엇을 할 것인가"만 정의하고, 실제 구현은 `YoutubeCommentServiceImpl`에서 함

---

#### `service/YoutubeCommentServiceImpl.java` ⭐ 메인 파일!
**역할**: 댓글을 가져와서 Redis에 저장하는 실제 구현체

**의존성 주입** (31-35줄):
- `YoutubeOAuthService`: OAuth 토큰 가져오기
- `YoutubeVideoService`: 조회수 상위 20개 영상 가져오기
- `YoutubeCommentMapper`: YouTube Comment → YoutubeComment 변환
- `StringRedisTemplate`: Redis에 데이터 저장
- `ObjectMapper`: Java 객체 → JSON 문자열 변환

**주요 메서드**:

##### 1. `syncTop20VideoComments(Integer userId)` (38-134줄)
**역할**: 메인 진입점 - 전체 댓글 동기화 프로세스 실행

**실행 흐름**:
```
1. 조회수 상위 20개 영상 가져오기 (40-42줄)
   videoService.getTop20VideosByChannel(userId)
   → Map<String, List<YoutubeVideo>> 반환
   → 키: 채널 ID, 값: 해당 채널의 상위 20개 영상 리스트

2. 영상이 없으면 종료 (44-47줄)
   if (videosByChannel.isEmpty()) → return 0

3. OAuth 토큰 가져오기 (49-51줄)
   youtubeOAuthService.getValidAccessToken(userId)
   → YouTube API 호출에 필요한 인증 토큰

4. YouTube 클라이언트 생성 (51줄)
   buildClient(token)
   → YouTube API를 호출할 수 있는 클라이언트 객체 생성

5. 각 채널별로 댓글 조회 및 저장 (55-124줄)
   for (Map.Entry<String, List<YoutubeVideo>> entry : videosByChannel.entrySet())
   → 각 채널마다 반복
   → 각 영상마다 반복
   → fetchAndSaveComments() 호출하여 댓글 저장

6. 결과 반환 (126-128줄)
   return totalCommentCount
```

**내부 호출 흐름**:
```
syncTop20VideoComments(userId) 호출
    ↓
videoService.getTop20VideosByChannel(userId) 호출
    ↓ (반환: Map<String, List<YoutubeVideo>>)
videosByChannel.isEmpty() 체크
    ↓ (비어있으면 return 0)
youtubeOAuthService.getValidAccessToken(userId) 호출
    ↓ (반환: String token)
buildClient(token) 호출
    ↓ (반환: YouTube yt)
for (채널별 반복) {
    for (영상별 반복) {
        videoId null 체크 (66-70줄)
        redisKey 생성 (72줄)
        기존 댓글 백업 (75줄)
        try {
            기존 댓글 삭제 (80줄)
            fetchAndSaveComments(yt, videoId, redisKey) 호출 (86줄)
            부분 실패 처리 (88-94줄)
        } catch (Exception) {
            기존 댓글 복구 (99-105줄)
        }
    }
}
return totalCommentCount
```

**보안 및 에러 처리**:
- **videoId null 체크** (66-70줄): 
  - `videoId == null || videoId.isBlank()` 체크
  - null이거나 빈 문자열이면 해당 영상 건너뜀 (`continue`)
  - **이유**: 잘못된 데이터로 인한 오류 방지
- **부분 실패 방지** (74-107줄):
  - 기존 댓글을 백업해두고, 새 댓글 저장 실패 시 복구
  - **이유**: 저장 실패 시 기존 데이터를 잃지 않도록 보호
- **예외 처리** (108-122줄):
  - `GoogleJsonResponseException`: YouTube API 에러 (댓글 비활성화 등)
  - `Exception`: 기타 예외
  - **중요**: 한 영상 실패해도 다른 영상은 계속 처리 (`continue`)

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeCommentServiceImpl.java`

---

##### 2. `buildClient(String accessToken)` (136-142줄)
**역할**: YouTube API 클라이언트 생성

**실행 흐름**:
```
1. GoogleNetHttpTransport.newTrustedTransport() 호출
   → HTTP 통신을 위한 전송 객체 생성

2. GsonFactory.getDefaultInstance() 호출
   → JSON 파싱을 위한 팩토리 생성

3. YouTube.Builder 생성
   → 인증 헤더 설정: "Bearer " + accessToken
   → 애플리케이션 이름 설정: "medi"

4. build() 호출하여 YouTube 객체 반환
```

**내부 호출 흐름**:
```
buildClient(accessToken) 호출
    ↓
GoogleNetHttpTransport.newTrustedTransport() 호출
    ↓ (반환: HttpTransport)
GsonFactory.getDefaultInstance() 호출
    ↓ (반환: JsonFactory)
new YouTube.Builder(transport, factory, credential) 호출
    ↓
setApplicationName("medi") 호출
    ↓
build() 호출
    ↓ (반환: YouTube)
YouTube 객체 반환
```

**에러 처리**:
- `Exception` 발생 시 상위로 전파 (throws Exception)

---

##### 3. `fetchAndSaveComments(YouTube yt, String videoId, String redisKey)` (163-239줄)
**역할**: 특정 영상의 댓글을 YouTube API에서 가져와서 Redis에 저장

**실행 흐름**:
```
1. 변수 초기화 (164-165줄)
   count = 0 (저장된 댓글 개수)
   nextPageToken = null (페이지네이션 토큰)

2. do-while 루프로 모든 댓글 수집 (168-230줄)
   do {
       a. YouTube CommentThreads API 요청 생성 (173-188줄)
          - part: "snippet", "replies"
          - videoId: 영상 ID
          - order: "time" (시간순 정렬)
          - maxResults: 100 (한 페이지당 최대 100개)
          - pageToken: nextPageToken (다음 페이지)
      
       b. API 호출 실행 (192줄)
          req.execute()
          → CommentThreadListResponse 반환
      
       c. 댓글 처리 (195-222줄)
          - 최상위 댓글 변환 및 저장
          - 대댓글 변환 및 저장
      
       d. 다음 페이지 토큰 확인 (226줄)
          nextPageToken = resp.getNextPageToken()
          → null이면 루프 종료
   } while (nextPageToken != null)

3. TTL 설정 및 리스트 크기 제한 (233-236줄)
   - expire(redisKey, Duration.ofDays(3)): 3일 후 자동 삭제
   - trim(redisKey, 0, 999): 최대 1000개만 유지

4. 저장된 댓글 개수 반환 (238줄)
   return count
```

**내부 호출 흐름**:
```
fetchAndSaveComments(yt, videoId, redisKey) 호출
    ↓
count = 0, nextPageToken = null 초기화
    ↓
do {
    yt.commentThreads().list(["snippet", "replies"]) 호출
        ↓ (반환: YouTube.CommentThreads.List req)
    req.setVideoId(videoId) 호출
    req.setOrder("time") 호출
    req.setMaxResults(100L) 호출
    if (nextPageToken != null) {
        req.setPageToken(nextPageToken) 호출
    }
        ↓
    req.execute() 호출 ⭐ 실제 API 호출
        ↓ (반환: CommentThreadListResponse resp)
    if (resp.getItems() != null) {
        for (CommentThread thread : resp.getItems()) {
            top = thread.getSnippet().getTopLevelComment()
            redisMapper.toRedisComment(top, null) 호출
                ↓ (반환: YoutubeComment topComment)
            saveCommentToRedis(redisKey, topComment) 호출
            count++
            
            if (thread.getReplies() != null) {
                for (Comment reply : thread.getReplies().getComments()) {
                    redisMapper.toRedisComment(reply, top.getId()) 호출
                        ↓ (반환: YoutubeComment replyComment)
                    saveCommentToRedis(redisKey, replyComment) 호출
                    count++
                }
            }
        }
    }
    nextPageToken = resp.getNextPageToken()
} while (nextPageToken != null)
    ↓
if (count > 0) {
    stringRedisTemplate.expire(redisKey, Duration.ofDays(3)) 호출
    stringRedisTemplate.opsForList().trim(redisKey, 0, 999) 호출
}
    ↓
return count
```

**보안 및 에러 처리**:
- **페이지네이션**: `do-while` 루프로 모든 댓글 수집 (무한 루프 방지)
- **최대 크기 제한**: `trim(redisKey, 0, 999)`로 최대 1000개만 유지
- **TTL 설정**: 3일 후 자동 삭제로 메모리 관리
- **예외 처리**: 상위로 전파 (throws Exception)

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeCommentServiceImpl.java` (163-239줄)

---

##### 4. `saveCommentToRedis(String redisKey, YoutubeComment comment)` (257-266줄)
**역할**: 댓글을 JSON 문자열로 변환하여 Redis List에 저장

**실행 흐름**:
```
1. YoutubeComment → JSON 문자열 변환 (260줄)
   objectMapper.writeValueAsString(comment)
   → 예: {"commentId":"abc123","text":"좋은 영상","author":"홍길동",...}

2. Redis List에 추가 (262줄)
   stringRedisTemplate.opsForList().rightPush(redisKey, json)
   → List의 오른쪽 끝에 추가 (FIFO: First In First Out)
```

**내부 호출 흐름**:
```
saveCommentToRedis(redisKey, comment) 호출
    ↓
objectMapper.writeValueAsString(comment) 호출
    ↓ (반환: String json)
stringRedisTemplate.opsForList().rightPush(redisKey, json) 호출
    ↓ (Redis에 저장 완료)
```

**에러 처리**:
- `JsonProcessingException`: JSON 변환 실패 시 로그만 출력하고 계속 진행
- **이유**: 한 댓글 변환 실패해도 다른 댓글은 계속 저장

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeCommentServiceImpl.java` (257-266줄)

---

##### 5. `extractErrorReason(GoogleJsonResponseException e)` (280-294줄)
**역할**: Google API 에러 응답에서 에러 원인(reason) 추출

**실행 흐름**:
```
1. 에러 상세 정보 가져오기 (282줄)
   e.getDetails()
   → GoogleJsonError 객체 반환

2. 에러 리스트 확인 (283줄)
   error.getErrors()
   → List<ErrorInfo> 반환

3. 첫 번째 에러의 reason 추출 (284-288줄)
   error.getErrors().get(0).getReason()
   → 예: "commentsDisabled", "disabledComments"

4. reason 반환 (287줄)
   return reason
```

**내부 호출 흐름**:
```
extractErrorReason(e) 호출
    ↓
e.getDetails() 호출
    ↓ (반환: GoogleJsonError error)
if (error != null && error.getErrors() != null && !error.getErrors().isEmpty()) {
    error.getErrors().get(0) 호출
        ↓ (반환: ErrorInfo firstError)
    if (firstError != null) {
        firstError.getReason() 호출
            ↓ (반환: String reason)
        return reason
    }
}
return ""
```

**에러 처리**:
- `Exception`: 에러 추출 실패 시 빈 문자열 반환 (로그만 출력)
- **이유**: 에러 추출 실패해도 전체 프로세스는 계속 진행

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeCommentServiceImpl.java` (280-294줄)

---

#### `service/YoutubeVideoService.java`
**역할**: 영상 서비스 인터페이스 (계약서)

**주요 메서드**:
- `getTop20VideosByChannel(Integer userId)`: 각 채널마다 조회수 상위 20개 영상 조회

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeVideoService.java`

---

#### `service/YoutubeVideoServiceImpl.java`
**역할**: 조회수 상위 20개 영상을 가져오는 실제 구현체

**의존성 주입** (35-37줄):
- `YoutubeOAuthService`: OAuth 토큰 가져오기
- `YoutubeChannelMapper`: DB에서 채널 목록 조회
- `YoutubeVideoMapper`: YouTube Video → YoutubeVideo 변환

**주요 메서드**:

##### 1. `getTop20VideosByChannel(Integer userId)` (40-118줄)
**역할**: 각 채널마다 조회수 상위 20개 영상을 조회

**실행 흐름**:
```
1. OAuth 토큰 가져오기 (42-44줄)
   youtubeOAuthService.getValidAccessToken(userId)
   → YouTube API 호출에 필요한 인증 토큰

2. YouTube 클라이언트 생성 (44줄)
   buildClient(token)
   → YouTube API를 호출할 수 있는 클라이언트 객체 생성

3. 사용자의 등록된 채널 목록 조회 (46-51줄)
   channelMapper.findByUserId(userId)
   → DB에서 이미 저장된 채널 목록 가져오기
   → 채널이 없으면 빈 Map 반환

4. 각 채널마다 조회수 상위 20개 영상 수집 (53-108줄)
   for (YoutubeChannelDto channel : channels) {
       a. 채널의 영상 목록 조회 (61줄)
          fetchChannelVideos(yt, channelId)
          → SearchResult 리스트 반환
      
       b. 비디오 ID 목록 추출 (69-72줄)
          searchResults.stream()
          → .map(result -> result.getId().getVideoId())
          → .filter(id -> id != null)
          → .collect(Collectors.toList())
      
       c. 비디오 상세 정보 가져오기 (80줄)
          fetchVideoDetails(yt, videoIds)
          → Video 리스트 반환 (조회수 포함)
      
       d. Redis DTO로 변환 (83-89줄)
          for (Video video : videos) {
              redisMapper.toRedisVideo(video)
              → YoutubeVideo 객체 생성
          }
      
       e. 조회수 기준으로 정렬하여 상위 20개 선택 (92-98줄)
          channelVideos.stream()
          → .sorted(Comparator.comparing(YoutubeVideo::getViewCount, ...))
          → .limit(20)
          → .collect(Collectors.toList())
      
       f. Map에 저장 (100줄)
          videosByChannel.put(channelId, top20Videos)
   }

5. 결과 반환 (112줄)
   return videosByChannel
```

**내부 호출 흐름**:
```
getTop20VideosByChannel(userId) 호출
    ↓
youtubeOAuthService.getValidAccessToken(userId) 호출
    ↓ (반환: String token)
buildClient(token) 호출
    ↓ (반환: YouTube yt)
channelMapper.findByUserId(userId) 호출
    ↓ (반환: List<YoutubeChannelDto> channels)
if (channels.isEmpty()) {
    return Collections.emptyMap()
}
    ↓
videosByChannel = new HashMap<>() 생성
    ↓
for (YoutubeChannelDto channel : channels) {
    channelId = channel.getYoutubeChannelId()
    fetchChannelVideos(yt, channelId) 호출
        ↓ (반환: List<SearchResult> searchResults)
    if (searchResults.isEmpty()) {
        videosByChannel.put(channelId, Collections.emptyList())
        continue
    }
        ↓
    videoIds = searchResults.stream()
        .map(result -> result.getId().getVideoId())
        .filter(id -> id != null)
        .collect(Collectors.toList())
        ↓ (반환: List<String> videoIds)
    if (videoIds.isEmpty()) {
        videosByChannel.put(channelId, Collections.emptyList())
        continue
    }
        ↓
    fetchVideoDetails(yt, videoIds) 호출
        ↓ (반환: List<Video> videos)
    channelVideos = new ArrayList<>()
    for (Video video : videos) {
        redisMapper.toRedisVideo(video) 호출
            ↓ (반환: YoutubeVideo redisVideo)
        if (redisVideo != null) {
            channelVideos.add(redisVideo)
        }
    }
        ↓
    top20Videos = channelVideos.stream()
        .sorted(Comparator.comparing(YoutubeVideo::getViewCount, ...))
        .limit(20)
        .collect(Collectors.toList())
        ↓ (반환: List<YoutubeVideo> top20Videos)
    videosByChannel.put(channelId, top20Videos)
}
    ↓
return videosByChannel
```

**보안 및 에러 처리**:
- **채널이 없을 때**: 빈 Map 반환 (에러 없이 처리)
- **영상이 없을 때**: 빈 리스트를 Map에 저장하고 계속 진행
- **예외 처리** (103-107줄):
  - `Exception`: 한 채널 실패해도 다른 채널은 계속 처리
  - 실패한 채널은 빈 리스트로 저장

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeVideoServiceImpl.java` (40-118줄)

---

##### 2. `buildClient(String accessToken)` (120-126줄)
**역할**: YouTube API 클라이언트 생성 (YoutubeCommentServiceImpl과 동일)

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeVideoServiceImpl.java` (120-126줄)

---

##### 3. `fetchChannelVideos(YouTube yt, String channelId)` (131-161줄)
**역할**: 채널의 영상 목록 조회 (비디오 ID만)

**실행 흐름**:
```
1. 변수 초기화 (132-133줄)
   allResults = new ArrayList<>()
   nextPageToken = null

2. do-while 루프로 모든 영상 수집 (135-158줄)
   do {
       a. YouTube Search API 요청 생성 (139-147줄)
          yt.search().list(["snippet"])
          → channelId: 채널 ID
          → maxResults: 50 (한 페이지당 최대 50개)
          → order: "date" (날짜순 정렬)
          → type: ["video"] (영상만)
          → pageToken: nextPageToken (다음 페이지)
      
       b. API 호출 실행 (151줄)
          searchReq.execute()
          → SearchListResponse 반환
      
       c. 결과 추가 (153-155줄)
          if (response.getItems() != null) {
              allResults.addAll(response.getItems())
          }
      
       d. 다음 페이지 토큰 확인 (157줄)
          nextPageToken = response.getNextPageToken()
          → null이면 루프 종료
   } while (nextPageToken != null)

3. 결과 반환 (160줄)
   return allResults
```

**내부 호출 흐름**:
```
fetchChannelVideos(yt, channelId) 호출
    ↓
allResults = new ArrayList<>(), nextPageToken = null 초기화
    ↓
do {
    yt.search().list(["snippet"]) 호출
        ↓ (반환: YouTube.Search.List searchReq)
    searchReq.setChannelId(channelId) 호출
    searchReq.setMaxResults(50L) 호출
    searchReq.setOrder("date") 호출
    searchReq.setType(["video"]) 호출
    if (nextPageToken != null) {
        searchReq.setPageToken(nextPageToken) 호출
    }
        ↓
    searchReq.execute() 호출 ⭐ 실제 API 호출
        ↓ (반환: SearchListResponse response)
    if (response.getItems() != null) {
        allResults.addAll(response.getItems()) 호출
    }
    nextPageToken = response.getNextPageToken()
} while (nextPageToken != null)
    ↓
return allResults
```

**보안 및 에러 처리**:
- **페이지네이션**: `do-while` 루프로 모든 영상 수집 (무한 루프 방지)
- **예외 처리**: 상위로 전파 (throws Exception)

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeVideoServiceImpl.java` (131-161줄)

---

##### 4. `fetchVideoDetails(YouTube yt, List<String> videoIds)` (166-192줄)
**역할**: 비디오 상세 정보 조회 (조회수 등 통계 포함)

**실행 흐름**:
```
1. 변수 초기화 (167줄)
   videos = new ArrayList<>()

2. 50개씩 배치로 나누어 처리 (170-189줄)
   for (int i = 0; i < videoIds.size(); i += 50) {
       a. 배치 추출 (171-172줄)
          end = Math.min(i + 50, videoIds.size())
          batch = videoIds.subList(i, end)
          → 최대 50개씩 나눔 (YouTube API 제한)
      
       b. YouTube Videos API 요청 생성 (177-180줄)
          yt.videos().list(["snippet", "statistics"])
          → id: batch (비디오 ID 리스트)
          → snippet: 제목, 썸네일 등
          → statistics: 조회수, 좋아요 수 등
      
       c. API 호출 실행 (184줄)
          req.execute()
          → VideoListResponse 반환
      
       d. 결과 추가 (186-188줄)
          if (resp.getItems() != null) {
              videos.addAll(resp.getItems())
          }
   }

3. 결과 반환 (191줄)
   return videos
```

**내부 호출 흐름**:
```
fetchVideoDetails(yt, videoIds) 호출
    ↓
videos = new ArrayList<>() 초기화
    ↓
for (int i = 0; i < videoIds.size(); i += 50) {
    end = Math.min(i + 50, videoIds.size())
    batch = videoIds.subList(i, end)
        ↓ (반환: List<String> batch)
    yt.videos().list(["snippet", "statistics"]) 호출
        ↓ (반환: YouTube.Videos.List req)
    req.setId(batch) 호출
        ↓
    req.execute() 호출 ⭐ 실제 API 호출
        ↓ (반환: VideoListResponse resp)
    if (resp.getItems() != null) {
        videos.addAll(resp.getItems()) 호출
    }
}
    ↓
return videos
```

**보안 및 에러 처리**:
- **배치 처리**: YouTube API는 한 번에 최대 50개까지만 조회 가능하므로 50개씩 나눠서 처리
- **예외 처리**: 상위로 전파 (throws Exception)

**코드 위치**: `backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeVideoServiceImpl.java` (166-192줄)

---

## 🔄 전체 코드 실행 흐름 (좌표별 상세 설명)

### 시나리오: 사용자가 로그인하고 채널을 조회함

```
[1] 사용자 요청
    위치: 외부 (컨트롤러 등)
    → YoutubeCommentServiceImpl.syncTop20VideoComments(userId) 호출

[2] YoutubeCommentServiceImpl.syncTop20VideoComments() (38줄)
    위치: backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeCommentServiceImpl.java:38
    → videoService.getTop20VideosByChannel(userId) 호출 (42줄)

[3] YoutubeVideoServiceImpl.getTop20VideosByChannel() (40줄)
    위치: backend/src/main/java/com/medi/backend/youtube/redis/service/YoutubeVideoServiceImpl.java:40
    → youtubeOAuthService.getValidAccessToken(userId) 호출 (43줄)
    → buildClient(token) 호출 (44줄)
    → channelMapper.findByUserId(userId) 호출 (47줄)
    → for (YoutubeChannelDto channel : channels) 루프 시작 (56줄)
        → fetchChannelVideos(yt, channelId) 호출 (61줄)
            → [4] YouTube Search API 호출 (151줄)
        → videoIds 추출 (69-72줄)
        → fetchVideoDetails(yt, videoIds) 호출 (80줄)
            → [5] YouTube Videos API 호출 (184줄)
        → redisMapper.toRedisVideo(video) 호출 (85줄)
        → 조회수 기준 정렬 및 상위 20개 선택 (92-98줄)
    → Map<String, List<YoutubeVideo>> 반환 (112줄)

[6] YoutubeCommentServiceImpl.syncTop20VideoComments() 계속 (44줄)
    → videosByChannel.isEmpty() 체크 (44줄)
    → youtubeOAuthService.getValidAccessToken(userId) 호출 (50줄)
    → buildClient(token) 호출 (51줄)
    → for (Map.Entry<String, List<YoutubeVideo>> entry : videosByChannel.entrySet()) 루프 시작 (56줄)
        → for (YoutubeVideo video : videos) 루프 시작 (62줄)
            → videoId null 체크 (67줄)
            → redisKey 생성 (72줄)
            → 기존 댓글 백업 (75줄)
            → fetchAndSaveComments(yt, videoId, redisKey) 호출 (86줄)
                → [7] YouTube CommentThreads API 호출 (192줄)
                → redisMapper.toRedisComment(top, null) 호출 (202줄)
                → saveCommentToRedis(redisKey, topComment) 호출 (204줄)
                    → objectMapper.writeValueAsString(comment) 호출 (260줄)
                    → stringRedisTemplate.opsForList().rightPush(redisKey, json) 호출 (262줄)
            → 부분 실패 처리 (88-94줄)
    → return totalCommentCount (128줄)
```

---

## 🔒 보안 및 에러 처리 상세 설명

### 1. 입력 검증 (Input Validation)

#### videoId null 체크
**위치**: `YoutubeCommentServiceImpl.java:67-70`
```java
if (videoId == null || videoId.isBlank()) {
    log.warn("영상 ID가 없습니다. 건너뜁니다: {}", video);
    continue;
}
```
**이유**: 
- null이거나 빈 문자열인 videoId로 Redis 키를 만들면 오류 발생 가능
- 잘못된 데이터로 인한 예외 방지

#### comment null 체크
**위치**: `YoutubeCommentMapper.java:14-16`
```java
if (comment == null || comment.getSnippet() == null) {
    return null;
}
```
**이유**: 
- null 객체에 접근하면 `NullPointerException` 발생
- 안전하게 null 반환하여 상위에서 처리

#### video null 체크
**위치**: `YoutubeVideoMapper.java:14-16`
```java
if (video == null) {
    return null;
}
```
**이유**: 
- null 객체에 접근하면 `NullPointerException` 발생
- 안전하게 null 반환하여 상위에서 처리

---

### 2. 부분 실패 방지 (Partial Failure Prevention)

#### 기존 댓글 백업 및 복구
**위치**: `YoutubeCommentServiceImpl.java:74-107`
```java
// 기존 댓글 백업
List<String> existingComments = stringRedisTemplate.opsForList().range(redisKey, 0, -1);

try {
    // 기존 댓글 삭제
    stringRedisTemplate.delete(redisKey);
    
    // 새 댓글 저장
    commentCount = fetchAndSaveComments(yt, videoId, redisKey);
    
    // 부분 실패 처리: 새 댓글이 없고 기존 댓글이 있었으면 복구
    if (commentCount == 0 && existingComments != null && !existingComments.isEmpty()) {
        log.warn("댓글 조회 실패 또는 댓글 없음. 기존 댓글 복구: {}", videoId);
        for (String comment : existingComments) {
            stringRedisTemplate.opsForList().rightPush(redisKey, comment);
        }
    }
} catch (Exception saveException) {
    // 저장 실패 시 기존 댓글 복구
    if (existingComments != null && !existingComments.isEmpty()) {
        log.warn("댓글 저장 실패. 기존 댓글 복구: {}", videoId);
        for (String comment : existingComments) {
            stringRedisTemplate.opsForList().rightPush(redisKey, comment);
        }
    }
    throw saveException;
}
```
**이유**: 
- 새 댓글 저장 실패 시 기존 데이터를 잃지 않도록 보호
- 사용자 경험 향상 (데이터 손실 방지)

---

### 3. 예외 처리 (Exception Handling)

#### GoogleJsonResponseException 처리
**위치**: `YoutubeCommentServiceImpl.java:108-118`
```java
catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
    String errorReason = extractErrorReason(e);
    if ("commentsDisabled".equals(errorReason) || "disabledComments".equals(errorReason)) {
        log.info("영상 {}의 댓글이 비활성화되어 있습니다", video.getYoutubeVideoId());
    } else {
        log.error("영상 {}의 댓글 조회 실패: {} (reason: {})", 
            video.getYoutubeVideoId(), e.getMessage(), errorReason);
    }
    // 한 영상 실패해도 다른 영상은 계속 처리
}
```
**이유**: 
- 댓글이 비활성화된 영상은 정상적인 상황이므로 에러로 처리하지 않음
- 다른 에러는 로그만 남기고 계속 진행 (부분 실패 허용)

#### 일반 Exception 처리
**위치**: `YoutubeCommentServiceImpl.java:119-122`
```java
catch (Exception e) {
    log.error("영상 {}의 댓글 조회 실패: {}", video.getYoutubeVideoId(), e.getMessage());
    // 한 영상 실패해도 다른 영상은 계속 처리
}
```
**이유**: 
- 예상치 못한 에러도 로그만 남기고 계속 진행
- 한 영상 실패해도 전체 프로세스는 계속 진행

#### JsonProcessingException 처리
**위치**: `YoutubeCommentServiceImpl.java:263-265`
```java
catch (JsonProcessingException e) {
    log.error("댓글 직렬화 실패: {}", comment, e);
}
```
**이유**: 
- JSON 변환 실패해도 다른 댓글은 계속 저장
- 에러 로그만 남기고 계속 진행

---

### 4. 리소스 관리 (Resource Management)

#### TTL (Time To Live) 설정
**위치**: `YoutubeCommentServiceImpl.java:234`
```java
stringRedisTemplate.expire(redisKey, Duration.ofDays(3));
```
**이유**: 
- 3일 후 자동 삭제되어 오래된 데이터가 쌓이지 않음
- Redis 메모리 관리

#### 리스트 크기 제한
**위치**: `YoutubeCommentServiceImpl.java:235`
```java
stringRedisTemplate.opsForList().trim(redisKey, 0, 999);
```
**이유**: 
- 최대 1000개만 유지하여 메모리 사용량 제한
- 무한 증가 방지

#### 배치 처리 (YouTube API 제한)
**위치**: `YoutubeVideoServiceImpl.java:170-189`
```java
for (int i = 0; i < videoIds.size(); i += 50) {
    int end = Math.min(i + 50, videoIds.size());
    List<String> batch = videoIds.subList(i, end);
    // API 호출
}
```
**이유**: 
- YouTube API는 한 번에 최대 50개까지만 조회 가능
- 50개씩 나눠서 처리하여 API 제한 준수

---

## 🔧 Redis 저장 구조 상세 설명

### Redis Key 형식
```
"video:{videoId}:comments"
```
**예시**: `"video:dQw4w9WgXcQ:comments"`

### Redis Value 형식
- **타입**: List (리스트)
- **요소**: JSON 문자열
- **예시**:
```json
[
  "{\"commentId\":\"abc123\",\"text\":\"좋은 영상\",\"author\":\"홍길동\",\"publishedAt\":\"2024-01-01T12:00:00\"}",
  "{\"commentId\":\"def456\",\"text\":\"대댓글\",\"author\":\"김철수\",\"parentId\":\"abc123\",\"publishedAt\":\"2024-01-01T13:00:00\"}"
]
```

### 저장 과정
1. `YoutubeComment` DTO 생성
2. `ObjectMapper.writeValueAsString()` 호출 → JSON 문자열 변환
3. `StringRedisTemplate.opsForList().rightPush()` 호출 → Redis List에 추가
4. `expire()` 호출 → TTL 설정 (3일)
5. `trim()` 호출 → 최대 1000개만 유지

---

## 📚 관련 파일 및 의존성

### 외부 의존성
- **YoutubeOAuthService**: OAuth 토큰 관리
  - 위치: `backend/src/main/java/com/medi/backend/youtube/service/YoutubeOAuthService.java`
  - 역할: YouTube API 호출에 필요한 인증 토큰 가져오기
- **YoutubeChannelMapper**: DB에서 채널 조회
  - 위치: `backend/src/main/java/com/medi/backend/youtube/mapper/YoutubeChannelMapper.java`
  - 역할: 사용자의 등록된 채널 목록 조회
- **StringRedisTemplate**: Redis 조작
  - 위치: Spring Data Redis 제공
  - 역할: Redis에 데이터 저장/조회
- **ObjectMapper**: JSON 변환
  - 위치: Jackson 라이브러리 제공
  - 역할: Java 객체 ↔ JSON 문자열 변환

### 설정 파일
- **RedisConfig.java**: Redis 설정
  - 위치: `backend/src/main/java/com/medi/backend/global/config/RedisConfig.java`
  - 역할: `StringRedisTemplate` 빈 생성
- **application.yml**: Redis 연결 정보
  - 위치: `backend/src/main/resources/application.yml`
  - 역할: Redis 서버 주소, 포트 등 설정

---

## ⚠️ 주의사항

1. **DB 저장 안 함**: 이 코드는 Redis에만 저장합니다
2. **데이터 일시성**: Redis 데이터는 TTL 3일 후 자동 삭제됩니다
3. **최대 크기**: 영상당 최대 1000개 댓글만 저장됩니다
4. **API 할당량**: YouTube API 할당량을 고려해야 합니다
5. **부분 실패 허용**: 한 영상 실패해도 다른 영상은 계속 처리됩니다

---

## 🔧 유지보수 가이드

### 새로운 필드 추가 시
1. `YoutubeComment` 또는 `YoutubeVideo` DTO에 필드 추가
2. 해당 Mapper에서 변환 로직 추가
3. JSON 직렬화는 자동으로 처리됨

### Redis 저장 형식 변경 시
1. `saveCommentToRedis()` 메서드 수정
2. TTL 및 크기 제한 조정 가능

### 에러 처리 추가 시
1. `extractErrorReason()` 메서드 활용
2. `catch` 블록에 새로운 케이스 추가

---

## 📝 변경 이력

- **2024년**: `YoutubeVideo` DTO 필드 추가 ⭐
  - `channelId`: 채널 ID 추가 (Python 코드의 `channel_id`)
  - `tags`: 비디오 태그 리스트 추가 (Python 코드의 `video_tags`)
  - Python 코드(`channel_comment_fetcher.py`)의 `cleaned_video_info` 구조 참고
- **2024년**: `YoutubeComment` DTO 필드 추가 ⭐
  - `authorChannelId`: 작성자 채널 ID 추가 (선택적)
  - `likeCount`: 좋아요 수 추가 (null 가능)
  - `updatedAt`: 수정 시간 추가 (null 가능)
  - Python 코드(`channel_comment_fetcher.py`) 및 다른 프로젝트 코드 참고
- **2024년**: DB 스키마 변경 (통계 컬럼 NULL 허용)
  - 코드는 이미 null 처리가 되어 있어 수정 불필요
- **2024년**: Python 코드 참고 주석 추가
- **2024년**: 에러 처리 개선 (commentsDisabled 케이스)
- **2024년**: 보안 개선
  - videoId null 체크 추가
  - 부분 실패 방지 (기존 댓글 백업 및 복구) 추가

---

## 📞 문의

코드 관련 문의사항이 있으면 개발팀에 문의하세요.
