# MEDI Backend API Documentation

프론트엔드 개발자를 위한 API 엔드포인트 문서입니다.

## 목차
1. [인증](#인증)
2. [Billing API](#billing-api)
3. [YouTube API](#youtube-api)

---

## 인증

모든 API 요청은 인증이 필요합니다. 인증 토큰을 헤더에 포함해야 합니다.

```
Authorization: Bearer {token}
```

### 권한 레벨
- `isAuthenticated()`: 로그인한 모든 사용자
- `hasRole('ADMIN')`: 관리자만 접근 가능

---

## Billing API

### Base URL
```
/api/billing
```

---

### 1. 구독 요금제 (Subscription Plans)

#### 1.1 모든 요금제 조회
**GET** `/plans`

**권한**: 공개 (인증 불필요)

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "planName": "Basic",
    "price": 9.99,
    "channelLimit": 3,
    "description": "Basic plan description",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

**필드 설명**:
- `id`: 요금제 ID
- `planName`: 요금제 이름
- `price`: 가격 (BigDecimal)
- `channelLimit`: 최대 채널 수
- `description`: 요금제 설명
- `createdAt`: 생성일시
- `updatedAt`: 수정일시

---

#### 1.2 특정 요금제 조회
**GET** `/plans/{id}`

**권한**: 공개 (인증 불필요)

**Path Parameters**:
- `id` (Integer): 요금제 ID

**응답**: 
- `200 OK`: 요금제 정보
- `404 Not Found`: 요금제를 찾을 수 없음

**응답 예시**:
```json
{
  "id": 1,
  "planName": "Basic",
  "price": 9.99,
  "channelLimit": 3,
  "description": "Basic plan description",
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-01-01T00:00:00"
}
```

---

#### 1.3 요금제 생성 (관리자)
**POST** `/plans`

**권한**: `hasRole('ADMIN')`

**Request Body**:
```json
{
  "planName": "Premium",
  "price": 19.99,
  "channelLimit": 10,
  "description": "Premium plan description"
}
```

**응답**:
- `201 Created`: 생성 성공
- `409 Conflict`: 중복된 요금제
- `500 Internal Server Error`: 서버 오류

**응답 예시**:
```json
"Subscription plan created successfully"
```

---

#### 1.4 요금제 수정 (관리자)
**PUT** `/plans/{id}`

**권한**: `hasRole('ADMIN')`

**Path Parameters**:
- `id` (Integer): 요금제 ID

**Request Body**:
```json
{
  "planName": "Premium Updated",
  "price": 24.99,
  "channelLimit": 15,
  "description": "Updated description"
}
```

**응답**:
- `200 OK`: 수정 성공
- `404 Not Found`: 요금제를 찾을 수 없음

**응답 예시**:
```json
"Update Success"
```

---

#### 1.5 요금제 삭제 (관리자)
**DELETE** `/plans/{id}`

**권한**: `hasRole('ADMIN')`

**Path Parameters**:
- `id` (Integer): 요금제 ID

**응답**:
- `200 OK`: 삭제 성공
- `404 Not Found`: 요금제를 찾을 수 없음

**응답 예시**:
```json
"Delete Success"
```

---

### 2. 결제 수단 (Payment Methods)

#### 2.1 내 결제 수단 조회
**GET** `/payment-methods`

**권한**: `isAuthenticated()`

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    "pgBillingKey": "billing_key_123",
    "cardType": "VISA",
    "cardLastFour": "1234",
    "isDefault": true,
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

**필드 설명**:
- `id`: 결제 수단 ID
- `userId`: 사용자 ID
- `pgBillingKey`: PG사 빌링 키
- `cardType`: 카드 타입 (VISA, MASTER 등)
- `cardLastFour`: 카드 번호 마지막 4자리
- `isDefault`: 기본 결제 수단 여부
- `createdAt`: 생성일시
- `updatedAt`: 수정일시

---

#### 2.2 결제 수단 등록
**POST** `/payment-methods`

**권한**: `isAuthenticated()`

**Request Body**:
```json
{
  "pgBillingKey": "billing_key_123",
  "cardType": "VISA",
  "cardLastFour": "1234"
}
```

**응답**:
- `201 Created`: 등록 성공
- `409 Conflict`: 중복된 결제 수단
- `500 Internal Server Error`: 서버 오류

**응답 예시**:
```json
"Payment method created successfully"
```

---

#### 2.3 결제 수단 삭제
**DELETE** `/payment-methods/{id}`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `id` (Integer): 결제 수단 ID

**응답**:
- `200 OK`: 삭제 성공
- `404 Not Found`: 결제 수단을 찾을 수 없거나 본인 소유가 아님

**응답 예시**:
```json
"Delete Success"
```

---

#### 2.4 기본 결제 수단 설정
**PUT** `/payment-methods/{id}/set-default`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `id` (Integer): 결제 수단 ID

**응답**:
- `200 OK`: 설정 성공
- `404 Not Found`: 결제 수단을 찾을 수 없거나 권한 없음

**응답 예시**:
```json
"Default payment method has been set."
```

---

### 3. 구독 (Subscriptions)

#### 3.1 내 활성 구독 조회
**GET** `/subscriptions/my-active`

**권한**: `isAuthenticated()`

**응답**:
- `200 OK`: 활성 구독 정보
- `204 No Content`: 활성 구독 없음

**응답 예시**:
```json
{
  "id": 1,
  "userId": 123,
  "planId": 1,
  "startDate": "2025-01-01T00:00:00",
  "endDate": "2025-02-01T00:00:00",
  "status": "ACTIVE",
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-01-01T00:00:00"
}
```

**필드 설명**:
- `id`: 구독 ID
- `userId`: 사용자 ID
- `planId`: 요금제 ID
- `startDate`: 구독 시작일시
- `endDate`: 구독 종료일시
- `status`: 구독 상태 (`ACTIVE`, `CANCELLED`)
- `createdAt`: 생성일시
- `updatedAt`: 수정일시

---

#### 3.2 내 구독 이력 조회
**GET** `/subscriptions/my-history`

**권한**: `isAuthenticated()`

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    "planId": 1,
    "startDate": "2025-01-01T00:00:00",
    "endDate": "2025-02-01T00:00:00",
    "status": "ACTIVE",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

**정렬**: `startDate` 기준 내림차순 (최신순)

---

#### 3.3 모든 구독 조회 (관리자)
**GET** `/admin/subscriptions`

**권한**: `hasRole('ADMIN')`

**Query Parameters** (모두 선택사항):
- `status` (String): 구독 상태 필터 (`ACTIVE`, `CANCELLED`)
- `planId` (Integer): 요금제 ID 필터
- `after` (DateTime): 구독 시작일 이후 필터 (ISO 8601 형식)

**예시**:
```
GET /api/billing/admin/subscriptions?status=ACTIVE&planId=1&after=2025-01-01T00:00:00
```

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    "planId": 1,
    "startDate": "2025-01-01T00:00:00",
    "endDate": "2025-02-01T00:00:00",
    "status": "ACTIVE",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

**정렬**: `userId` 오름차순, `startDate` 내림차순

---

#### 3.4 구독 생성
**POST** `/subscriptions`

**권한**: `isAuthenticated()`

**Request Body**:
```json
{
  "planId": 1
}
```

**응답**:
- `201 Created`: 구독 생성 성공
- `409 Conflict`: 이미 활성 구독이 있음
- `500 Internal Server Error`: 서버 오류

**응답 예시**:
```json
"Subscription created successfully"
```

**참고**: 구독 기간은 자동으로 30일로 설정됩니다.

---

#### 3.5 구독 취소
**PUT** `/subscriptions/{subscriptionId}/cancel`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `subscriptionId` (Integer): 구독 ID

**응답**:
- `200 OK`: 취소 성공
- `404 Not Found`: 구독을 찾을 수 없거나 이미 비활성 상태

**응답 예시**:
```json
"Subscription cancelled successfully."
```

---

#### 3.6 구독 요금제 변경
**PUT** `/subscriptions/change-plan`

**권한**: `isAuthenticated()`

**Request Body**:
```json
{
  "newPlanId": 2
}
```

**응답**:
- `200 OK`: 변경 성공 또는 관련 메시지
  - `"No subscription plan."`: 활성 구독이 없음
  - `"Already using that plan."`: 이미 해당 요금제 사용 중
  - `"The plan has been successfully changed."`: 변경 성공
- `500 Internal Server Error`: 서버 오류

---

## YouTube API

### Base URL
```
/api/youtube
```

---

### 1. 채널 (Channels)

#### 1.1 내 채널 목록 조회
**GET** `/channels/my`

**권한**: `isAuthenticated()`

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    "oauthTokenId": 1,
    "youtubeChannelId": "UCxxxxx",
    "channelName": "My Channel",
    "channelHandle": "@mychannel",
    "thumbnailUrl": "https://...",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00",
    "lastSyncedAt": "2025-01-01T00:00:00",
    "lastVideoPublishedAt": "2025-01-01T00:00:00",
    "uploadsPlaylistId": "UUxxxxx"
  }
]
```

**필드 설명**:
- `id`: 채널 ID (내부)
- `userId`: 사용자 ID
- `oauthTokenId`: OAuth 토큰 ID
- `youtubeChannelId`: YouTube 채널 ID
- `channelName`: 채널 이름
- `channelHandle`: 채널 핸들 (@username)
- `thumbnailUrl`: 썸네일 URL
- `createdAt`: 생성일시
- `updatedAt`: 수정일시
- `lastSyncedAt`: 마지막 동기화 일시
- `lastVideoPublishedAt`: 마지막 비디오 발행 일시
- `uploadsPlaylistId`: 업로드 플레이리스트 ID

---

#### 1.2 특정 채널 조회
**GET** `/channels/{id}`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `id` (Integer): 채널 ID

**응답**:
- `200 OK`: 채널 정보
- `404 Not Found`: 채널을 찾을 수 없거나 본인 소유가 아님

**응답 예시**: 위와 동일

---

#### 1.3 채널 동기화
**POST** `/channels/sync`

**권한**: `isAuthenticated()`

**설명**: YouTube API에서 사용자의 채널 목록을 가져와서 동기화합니다.

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    ...
  }
]
```

**동기화된 채널 목록을 반환합니다.**

---

#### 1.4 모든 채널 조회 (관리자)
**GET** `/channels/all`

**권한**: `hasRole('ADMIN')`

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    ...
  }
]
```

---

#### 1.5 특정 사용자의 채널 조회 (관리자)
**GET** `/channels/user/{userId}`

**권한**: `hasRole('ADMIN')`

**Path Parameters**:
- `userId` (Integer): 사용자 ID

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "userId": 123,
    ...
  }
]
```

---

#### 1.6 채널 삭제
**DELETE** `/channels/{id}`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `id` (Integer): 채널 ID

**응답**:
- `200 OK`: 삭제 성공
- `404 Not Found`: 채널을 찾을 수 없거나 본인 소유가 아님

**응답 예시**:
```json
"Delete Success"
```

---

### 2. 비디오 (Videos)

#### 2.1 채널별 비디오 목록 조회
**GET** `/videos/channel/{channelId}`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `channelId` (Integer): 채널 ID

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "channelId": 1,
    "youtubeVideoId": "dQw4w9WgXcQ",
    "title": "Video Title",
    "viewCount": 1000,
    "likeCount": 100,
    "commentCount": 50,
    "publishedAt": "2025-01-01T00:00:00",
    "thumbnailUrl": "https://...",
    "createdAt": "2025-01-01T00:00:00",
    "updatedAt": "2025-01-01T00:00:00"
  }
]
```

**필드 설명**:
- `id`: 비디오 ID (내부)
- `channelId`: 채널 ID
- `youtubeVideoId`: YouTube 비디오 ID
- `title`: 비디오 제목
- `viewCount`: 조회수
- `likeCount`: 좋아요 수
- `commentCount`: 댓글 수
- `publishedAt`: 발행일시
- `thumbnailUrl`: 썸네일 URL
- `createdAt`: 생성일시
- `updatedAt`: 수정일시

**참고**: 본인이 소유한 채널의 비디오만 조회 가능합니다.

---

#### 2.2 특정 비디오 조회
**GET** `/videos/{id}`

**권한**: `isAuthenticated()`

**Path Parameters**:
- `id` (Integer): 비디오 ID

**응답**:
- `200 OK`: 비디오 정보
- `404 Not Found`: 비디오를 찾을 수 없거나 본인 소유가 아님

**응답 예시**: 위와 동일

---

#### 2.3 내 모든 비디오 조회
**GET** `/videos/my`

**권한**: `isAuthenticated()`

**설명**: 사용자가 소유한 모든 채널의 비디오를 조회합니다.

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "channelId": 1,
    ...
  }
]
```

**정렬**: `publishedAt` 기준 내림차순 (최신순)

---

#### 2.4 비디오 동기화
**POST** `/videos/sync`

**권한**: `isAuthenticated()`

**Request Body**:
```json
{
  "channelId": 1,
  "maxResults": 50
}
```

**필드 설명**:
- `channelId` (Integer, 필수): 동기화할 채널 ID
- `maxResults` (Integer, 선택): 최대 동기화할 비디오 수

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "channelId": 1,
    ...
  }
]
```

**에러 응답**:
- `400 Bad Request`: `channelId`가 없음
- `404 Not Found`: 채널을 찾을 수 없음

**동기화된 비디오 목록을 반환합니다.**

---

#### 2.5 모든 비디오 조회 (관리자)
**GET** `/videos/all`

**권한**: `hasRole('ADMIN')`

**응답**: `200 OK`
```json
[
  {
    "id": 1,
    "channelId": 1,
    ...
  }
]
```

**정렬**: `publishedAt` 기준 내림차순 (최신순)

---

## 에러 응답 형식

모든 API는 표준 HTTP 상태 코드를 사용합니다:

- `200 OK`: 성공
- `201 Created`: 생성 성공
- `204 No Content`: 내용 없음 (활성 구독 없음 등)
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 필요
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스를 찾을 수 없음
- `409 Conflict`: 중복 또는 충돌
- `500 Internal Server Error`: 서버 오류

---

## 날짜/시간 형식

모든 날짜/시간 필드는 ISO 8601 형식을 사용합니다:
```
2025-01-01T00:00:00
```

---

## 인증 토큰

모든 인증이 필요한 API는 헤더에 토큰을 포함해야 합니다:
```
Authorization: Bearer {your_token}
```

---

## 참고사항

1. **사용자 ID 자동 추출**: `isAuthenticated()` 권한이 있는 API는 요청한 사용자의 ID를 자동으로 추출합니다. 별도로 `userId`를 전달할 필요가 없습니다.

2. **관리자 전용 API**: `hasRole('ADMIN')` 권한이 있는 API는 관리자만 접근 가능합니다.

3. **채널/비디오 소유권 확인**: YouTube API는 사용자가 소유한 채널/비디오만 조회할 수 있습니다. 다른 사용자의 리소스에 접근하면 `404 Not Found`를 반환합니다.

4. **구독 기간**: 새 구독을 생성하면 자동으로 30일 기간이 설정됩니다.

5. **구독 상태**: 
   - `ACTIVE`: 활성 구독
   - `CANCELLED`: 취소된 구독

---

## 문의

API 관련 문의사항이 있으시면 백엔드 팀에 문의해주세요.

