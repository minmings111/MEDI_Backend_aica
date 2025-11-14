# Redis 동기화 코드 흐름 모식도

## 전체 진입점 분기

```
                    [Controller/Service]
                            │
                            │
        ┌───────────────────┴───────────────────┐
        │                                       │
        ▼                                       ▼
┌───────────────────┐                 ┌──────────────────────┐
│  syncToRedis()    │                 │ syncIncrementalTo   │
│  (초기 동기화)     │                 │ Redis()             │
│                   │                 │ (증분 동기화)        │
│  입력: userId     │                 │                     │
│  (채널은 API 조회)│                 │ 입력: userId,       │
│                   │                 │       videoIds      │
└─────────┬─────────┘                 └──────────┬──────────┘
          │                                       │
          │                                       │
          ▼                                       ▼
```

---

## 1. 초기 동기화 흐름 (syncToRedis)

```
┌─────────────────────────────────────────────────────────────┐
│ YoutubeRedisSyncServiceImpl.syncToRedis()                  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        │ 1. YouTube 클라이언트 생성
                        │
                        ▼
        ┌──────────────────────────────────────┐
        │ YoutubeApiClientUtil                 │
        │ .buildClientForUser()                │
        │ (OAuth 토큰 + 클라이언트 생성)        │
        └──────────────┬───────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────────┐
        │ YoutubeApiClientUtil                 │
        │ .fetchUserChannelIds()               │
        │ (YouTube API로 채널 목록 조회)        │
        └──────────────┬───────────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │ channelIds 빈가? │
              └────────┬────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
       YES                           NO
        │                             │
        ▼                             ▼
┌───────────────┐      ┌──────────────────────────────────────┐
│ 빈 결과 반환   │      │ videoService.getTop20VideosByChannel()│
└───────────────┘      └──────────────┬───────────────────────┘
                                      │
                                      │
                    ┌─────────────────┴─────────────────┐
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ 각 채널별 반복 처리     │          │ OAuth 토큰 가져오기      │
        │ for (channelId)       │          │ YouTube 클라이언트 생성  │
        └───────────┬───────────┘          └────────────┬─────────────┘
                    │                                   │
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ fetchChannelVideos()   │          │ YouTube Search API 호출  │
        │ (채널의 모든 영상 조회) │          │ - channelId로 검색       │
        └───────────┬───────────┘          │ - 페이지네이션 처리      │
                    │                       └────────────┬─────────────┘
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ fetchVideoDetails()    │          │ YouTube Videos API 호출   │
        │ (비디오 상세 정보)      │          │ - 50개씩 배치 처리        │
        └───────────┬───────────┘          │ - 조회수 포함             │
                    │                       └────────────┬─────────────┘
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ 조회수 기준 정렬       │          │ 상위 20개 선택            │
        │ (내림차순)             │          │ limit(20)                │
        └───────────┬───────────┘          └────────────┬─────────────┘
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ redisMapper.toRedis   │          │ RedisYoutubeVideo DTO     │
        │ Video()               │          │ (기본 메타데이터만)        │
        │ (기본 메타데이터만)    │          │ - video_id               │
        └───────────┬───────────┘          │ - video_title            │
                    │                       │ - channel_id             │
                    │                       │ - video_tags             │
                    ▼                       └────────────┬─────────────┘
        ┌───────────────────────┐                       │
        │ Redis 저장             │                       │
        │ 1. Set 저장            │                       │
        │    channel:{id}:top20  │                       │
        │ 2. String 저장         │                       │
        │    video:{id}:meta:json│                       │
        └───────────┬───────────┘                       │
                    │                                   │
                    └───────────┬───────────────────────┘
                                │
                                ▼
                    ┌───────────────────────────┐
                    │ videosByChannel 반환      │
                    │ Map<channelId, List>      │
                    └───────────┬───────────────┘
                                │
                                ▼
        ┌───────────────────────────────────────────────────────┐
        │ commentService.syncTop20VideoComments()                │
        │ 옵션: SyncOptions.initialSync()                        │
        │ - 댓글 100개 제한                                      │
        │ - 기본 메타데이터만 (RedisYoutubeComment)              │
        └───────────┬───────────────────────────────────────────┘
                    │
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
┌──────────────────┐   ┌──────────────────────┐
│ 각 채널별 반복    │   │ 각 비디오별 반복      │
│ for (channel)    │   │ for (video)          │
└────────┬─────────┘   └──────────┬───────────┘
         │                        │
         │                        ▼
         │            ┌──────────────────────┐
         │            │ fetchAndSaveComments()│
         │            │ 옵션: initialSync()  │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ useFullMetadata?     │
         │            │ = false             │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ YouTube CommentThreads│
         │            │ API 호출             │
         │            │ - 페이지네이션      │
         │            │ - 최대 100개 제한    │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ 최상위 댓글 처리      │
         │            │ redisMapper.toRedis  │
         │            │ Comment()            │
         │            │ (기본 메타데이터)     │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ 대댓글 처리           │
         │            │ redisMapper.toRedis  │
         │            │ Comment()            │
         │            │ (기본 메타데이터)     │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ Redis 저장            │
         │            │ video:{id}:comments: │
         │            │ json (JSON 배열)     │
         │            └──────────┬───────────┘
         │                       │
         └───────────────────────┘
                                 │
                                 ▼
                    ┌───────────────────────────┐
                    │ RedisSyncResult 반환       │
                    │ - channelCount            │
                    │ - videoCount              │
                    │ - commentCount            │
                    │ - success                │
                    └───────────────────────────┘
```

---

## 2. 증분 동기화 흐름 (syncIncrementalToRedis)

```
┌─────────────────────────────────────────────────────────────┐
│ YoutubeRedisSyncServiceImpl.syncIncrementalToRedis()       │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        │ 1. 비디오 ID 검증
                        │
                        ▼
              ┌─────────────────┐
              │ videoIds 빈가?  │
              └────────┬────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
       YES                           NO
        │                             │
        ▼                             ▼
┌───────────────┐      ┌──────────────────────────────────────┐
│ 빈 결과 반환   │      │ SyncOptions.incrementalSync()       │
└───────────────┘      │ - maxCommentCount: null (제한없음)  │
                       │ - includeFullMetadata: true         │
                       └──────────────┬───────────────────────┘
                                      │
                                      │
                    ┌─────────────────┴─────────────────┐
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ videoService.syncVideo │          │ YoutubeApiClientUtil     │
        │ Metadata()             │          │ .buildClientForUser()    │
        └───────────┬───────────┘          │ (OAuth 토큰 + 클라이언트)  │
                    │                       └────────────┬─────────────┘
                    │                                   │
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ fetchVideoDetails()    │          │ YouTube Videos API 호출  │
        │ (비디오 상세 정보)      │          │ - 50개씩 배치 처리        │
        └───────────┬───────────┘          └────────────┬─────────────┘
                    │                                   │
                    ▼                                   ▼
        ┌───────────────────────┐          ┌──────────────────────────┐
        │ redisMapper.toRedis    │          │ RedisYoutubeVideo DTO     │
        │ Video()                │          │ (기본 메타데이터만)       │
        │ (기본 메타데이터만)     │          │ - video_id               │
        └───────────┬───────────┘          │ - video_title            │
                    │                       │ - channel_id             │
                    │                       │ - video_tags             │
                    ▼                       └────────────┬─────────────┘
        ┌───────────────────────┐                       │
        │ Redis 저장             │                       │
        │ video:{id}:meta:json  │                       │
        └───────────┬───────────┘                       │
                    │                                   │
                    └───────────┬───────────────────────┘
                                │
                                ▼
        ┌───────────────────────────────────────────────────────┐
        │ commentService.syncVideoComments()                     │
        │ 옵션: SyncOptions.incrementalSync()                    │
        │ - 댓글 제한 없음                                       │
        │ - 전체 메타데이터 (RedisYoutubeCommentFull)            │
        └───────────┬───────────────────────────────────────────┘
                    │
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
┌──────────────────┐   ┌──────────────────────┐
│ 각 비디오별 반복  │   │ fetchAndSaveComments()│
│ for (videoId)    │   │ 옵션: incrementalSync│
└────────┬─────────┘   └──────────┬───────────┘
         │                        │
         │                        ▼
         │            ┌──────────────────────┐
         │            │ useFullMetadata?     │
         │            │ = true               │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ YouTube CommentThreads│
         │            │ API 호출             │
         │            │ - 페이지네이션      │
         │            │ - 제한 없음          │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ 최상위 댓글 처리      │
         │            │ CommentThread에서    │
         │            │ totalReplyCount 추출 │
         │            │ redisMapper.toRedis  │
         │            │ CommentFull()        │
         │            │ (전체 메타데이터)     │
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ 대댓글 처리           │
         │            │ redisMapper.toRedis  │
         │            │ CommentFull()        │
         │            │ (전체 메타데이터)     │
         │            │ totalReplyCount: null│
         │            └──────────┬───────────┘
         │                       │
         │                       ▼
         │            ┌──────────────────────┐
         │            │ Redis 저장            │
         │            │ video:{id}:comments: │
         │            │ json (JSON 배열)     │
         │            └──────────┬───────────┘
         │                       │
         └───────────────────────┘
                                 │
                                 ▼
                    ┌───────────────────────────┐
                    │ RedisSyncResult 반환       │
                    │ - channelCount: 0         │
                    │ - videoCount              │
                    │ - commentCount            │
                    │ - success                │
                    └───────────────────────────┘
```

---

## 3. 댓글 메타데이터 분기 상세

```
                    fetchAndSaveComments()
                            │
                            │
                ┌───────────┴───────────┐
                │                       │
                ▼                       ▼
    ┌───────────────────┐   ┌──────────────────────┐
    │ useFullMetadata?  │   │ options.isInclude    │
    │ = false           │   │ FullMetadata()       │
    │ (초기 동기화)      │   │ = true               │
    │                    │   │ (증분 동기화)        │
    └───────────┬────────┘   └──────────┬───────────┘
                │                       │
                │                       │
                ▼                       ▼
    ┌───────────────────┐   ┌──────────────────────┐
    │ 최상위 댓글        │   │ 최상위 댓글           │
    │ toRedisComment()  │   │ CommentThread에서    │
    │                   │   │ totalReplyCount 추출 │
    │ RedisYoutube      │   │ toRedisCommentFull() │
    │ Comment           │   │                      │
    │ - comment_id      │   │ RedisYoutubeComment  │
    │ - text_original    │   │ Full                │
    │ - author_name     │   │ - comment_id        │
    │ - like_count      │   │ - text_original     │
    │ - published_at    │   │ - author_name        │
    └───────────┬────────┘   │ - author_channel_id │
                │            │ - like_count        │
                │            │ - published_at      │
                ▼            │ - updated_at        │
    ┌───────────────────┐   │ - parent_id         │
    │ 대댓글             │   │ - total_reply_count│
    │ toRedisComment()  │   │ - can_rate          │
    │                   │   │ - viewer_rating    │
    │ RedisYoutube      │   └──────────┬───────────┘
    │ Comment           │              │
    │ (동일한 필드)      │              │
    └───────────┬────────┘              ▼
                │            ┌──────────────────────┐
                │            │ 대댓글                │
                │            │ toRedisCommentFull() │
                │            │ totalReplyCount: null│
                │            │                      │
                │            │ RedisYoutubeComment  │
                │            │ Full                │
                │            │ (동일한 필드)         │
                │            └──────────┬───────────┘
                │                       │
                └───────────┬───────────┘
                            │
                            ▼
                ┌──────────────────────┐
                │ saveCommentsToRedis() │
                │ List<Object> 변환     │
                │ JSON 배열로 직렬화    │
                └──────────┬───────────┘
                            │
                            ▼
                ┌──────────────────────┐
                │ Redis 저장            │
                │ video:{id}:comments: │
                │ json = "[{...}, ...]"│
                │ TTL: 3일             │
                └──────────────────────┘
```

---

## 4. 옵션별 비교표

```
┌─────────────────────┬──────────────────────┬──────────────────────┐
│ 구분                │ 초기 동기화          │ 증분 동기화           │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ 진입점              │ syncToRedis()        │ syncIncrementalTo    │
│                     │                      │ Redis()              │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ 입력                │ userId               │ userId, videoIds     │
│                     │ (채널은 API 조회)     │                      │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ 비디오 메타데이터    │ RedisYoutubeVideo    │ RedisYoutubeVideo    │
│                     │ (기본 4개 필드)      │ (기본 4개 필드)      │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ 댓글 메타데이터      │ RedisYoutubeComment  │ RedisYoutubeComment  │
│                     │ (기본 5개 필드)      │ Full (전체 11개 필드)│
├─────────────────────┼──────────────────────┼──────────────────────┤
│ 댓글 개수 제한       │ 100개                │ 제한 없음            │
├─────────────────────┼──────────────────────┼──────────────────────┤
│ SyncOptions         │ initialSync()        │ incrementalSync()    │
│                     │ - maxCommentCount:  │ - maxCommentCount:   │
│                     │   100               │   null               │
│                     │ - includeFullMeta:  │ - includeFullMeta:  │
│                     │   false             │   true               │
└─────────────────────┴──────────────────────┴──────────────────────┘
```

---

## 5. 주요 메서드 호출 체인

```
초기 동기화:
syncToRedis(userId)
  ├─> YoutubeApiClientUtil.buildClientForUser()  (OAuth + 클라이언트)
  ├─> YoutubeApiClientUtil.fetchUserChannelIds() (채널 목록 조회)
  └─> getTop20VideosByChannel()
      ├─> fetchChannelVideos()        (YouTube Search API)
      ├─> fetchVideoDetails()         (YouTube Videos API)
      ├─> toRedisVideo()              (기본 메타데이터)
      ├─> saveTop20VideoIdsToRedis()  (Redis Set)
      └─> saveVideoMetadataToRedis()  (Redis String)
  └─> syncTop20VideoComments()
      └─> fetchAndSaveComments()
          ├─> toRedisComment()         (기본 메타데이터)
          └─> saveCommentsToRedis()    (Redis String)

증분 동기화:
syncIncrementalToRedis(userId, videoIds)
  └─> syncVideoMetadata()
      ├─> YoutubeApiClientUtil.buildClientForUser()  (OAuth + 클라이언트)
      ├─> fetchVideoDetails()         (YouTube Videos API)
      ├─> toRedisVideo()              (기본 메타데이터)
      └─> saveVideoMetadataToRedis()  (Redis String)
  └─> syncVideoComments()
      └─> fetchAndSaveComments()
          ├─> toRedisCommentFull()    (전체 메타데이터)
          └─> saveCommentsToRedis()    (Redis String)
```

---

## 6. Redis 저장 구조

```
초기 동기화:
channel:{channel_id}:top20_video_ids  (Set)
  └─> ["video_id1", "video_id2", ...]

video:{video_id}:meta:json  (String)
  └─> {"video_id": "...", "video_title": "...", "channel_id": "...", "video_tags": [...]}

video:{video_id}:comments:json  (String)
  └─> [{"comment_id": "...", "text_original": "...", "author_name": "...", "like_count": 0, "published_at": "..."}, ...]

증분 동기화:
video:{video_id}:meta:json  (String)
  └─> {"video_id": "...", "video_title": "...", "channel_id": "...", "video_tags": [...]}
      (초기와 동일)

video:{video_id}:comments:json  (String)
  └─> [{"comment_id": "...", "text_original": "...", "author_name": "...", 
        "author_channel_id": "...", "like_count": 0, "published_at": "...", 
        "updated_at": "...", "parent_id": "...", "total_reply_count": 0, 
        "can_rate": true, "viewer_rating": "..."}, ...]
      (전체 메타데이터)
```

