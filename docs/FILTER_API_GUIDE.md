# 필터링 설정 API 가이드 (프론트엔드용)

## 📋 개요
댓글 필터링 설정을 위한 3단계 폼 프로세스 API 가이드입니다.

---

## 🔄 사용자 플로우

1. **카테고리 선택** → 프론트엔드 상태 관리
2. **키워드 입력** → 프론트엔드 상태 관리
3. **예시 댓글 조회** → API 호출
4. **예시 댓글 라벨링** → 프론트엔드 상태 관리
5. **최종 제출** → API 호출 (DB + Redis 저장)

---

## 📡 API 명세

### 1. 예시 댓글 조회

**Endpoint**: `POST /api/filter/examples`  
**인증**: 로그인 필요 (세션 기반)

**Request**:
```json
{
  "categories": ["profanity", "appearance"],
  "limit": 10,
  "mixDifficulty": true
}
```

**Response**:
```json
[
  {
    "id": 1,
    "categoryId": "profanity",
    "commentText": "야 이 미친 새끼가 ㅅㅂ",
    "suggestedLabel": "block",
    "difficultyLevel": "EASY"
  },
  {
    "id": 2,
    "categoryId": "appearance",
    "commentText": "와 못생겼다",
    "suggestedLabel": "block",
    "difficultyLevel": "MEDIUM"
  }
]
```

**특징**:
- 1개 카테고리 선택: 해당 카테고리에서 10개 반환
- 여러 카테고리 선택: 총 10개를 카테고리별로 균등 분배
  - 예: 2개 카테고리 → 각 5개씩
  - 예: 3개 카테고리 → 4개, 3개, 3개

---

### 2. 필터링 설정 저장

**Endpoint**: `POST /api/filter/preferences`  
**인증**: 로그인 필요 (세션 기반)

**Request**:
```json
{
  "channelId": 123,  // null이면 전역 설정
  "selectedCategories": ["profanity", "appearance"],
  "customRuleKeywords": {
    "profanity": ["ㅅㅂ", "병X"],
    "appearance": ["못생겼다"]
  },
  "dislikeExamples": ["야 이 미친 새끼가 ㅅㅂ", "와 못생겼다"],
  "allowExamples": ["컨디션 안 좋아보이네"]
}
```

**Response**:
```json
{
  "id": 1,
  "userId": 123,
  "channelId": 456,
  "selectedCategories": ["profanity", "appearance"],
  "customRuleKeywords": {
    "profanity": ["ㅅㅂ", "병X"],
    "appearance": ["못생겼다"]
  },
  "dislikeExamples": ["야 이 미친 새끼가 ㅅㅂ", "와 못생겼다"],
  "allowExamples": ["컨디션 안 좋아보이네"],
  "isActive": true,
  "createdAt": "2024-01-01T00:00:00",
  "updatedAt": "2024-01-01T00:00:00"
}
```

**에러 응답** (최소 선택 개수 미달):
```json
{
  "error": "예시 댓글을 최소 3개 이상 선택해주세요. (현재: 2개)"
}
```
- **HTTP Status**: 400 Bad Request

---

### 3. 필터링 설정 조회

**Endpoint**: `GET /api/filter/preferences?channelId={channelId}`  
**인증**: 로그인 필요 (세션 기반)

**Query Parameters**:
- `channelId` (optional): 채널별 설정 조회, 없으면 전역 설정 조회

**Response**: 저장 API와 동일한 형식

---

## ⚠️ 중요 사항

### 1. 최소 선택 개수
- **예시 댓글 라벨링**: 최소 **3개 이상** 필수
- `dislikeExamples.length + allowExamples.length >= 3`
- 미달 시 400 에러 반환

### 2. 카테고리 목록
- `profanity`: 욕설·비속어
- `appearance`: 외모·신체 비하
- `personal_attack`: 인신공격·모욕
- `hate_speech`: 혐오·차별 발언
- `sexual`: 성적 발언·희롱
- `spam`: 스팸·광고·도배

### 3. 채널별 vs 전역 설정
- `channelId`가 있으면: 해당 채널에만 적용
- `channelId`가 `null`이면: 전역 설정 (모든 채널에 적용)

### 4. 예시 댓글 분배
- 백엔드에서 자동으로 카테고리별 균등 분배 처리
- 프론트엔드는 `limit: 10`만 지정하면 됨

---

## 📝 데이터 구조 (JavaScript 예시)

### ExampleRequest (예시 요청 객체)
```javascript
// 예시: 예시 댓글 조회 요청 페이로드
const exampleRequest = {
  categories: ['profanity', 'appearance'], // 선택한 카테고리 배열
  limit: 10,                               // 기본값: 10
  mixDifficulty: true                      // 기본값: true
};
```

### FilterPreferenceRequest (예시 요청 객체)
```javascript
// 예시: 필터 설정 저장 요청 페이로드
const filterPreferenceRequest = {
  channelId: 123, // 또는 null (전역 설정)
  selectedCategories: ['profanity', 'appearance'],
  customRuleKeywords: {                         // 카테고리별 키워드
    profanity: ['ㅅㅂ', '병X'],
    appearance: ['못생겼다']
  },
  dislikeExamples: ['야 이 미친 새끼가 ㅅㅂ'],   // 숨기고 싶은 댓글
  allowExamples: ['컨디션 안 좋아보이네']       // 괜찮은 댓글
};
```

### FilterExampleCommentDto (예시 응답 객체)
```javascript
// 예시: 예시 댓글 응답 객체
const exampleComment = {
  id: 1,
  categoryId: 'profanity',
  commentText: '야 이 미친 새끼가 ㅅㅂ',
  suggestedLabel: 'block', // 'block' 또는 'allow'
  difficultyLevel: 'EASY'  // 'EASY' | 'MEDIUM' | 'HARD'
};
```

---

## 🔄 권장 플로우 (React + Axios 예시)

```javascript
import React, { useState } from 'react';
import axios from 'axios';

// 예시 컴포넌트 내부

// Step 1, 2: 프론트엔드 상태 관리
const [categories, setCategories] = useState([]);      // 선택한 카테고리 배열
const [keywords, setKeywords] = useState({});          // { categoryId: [keyword1, keyword2] }

// Step 3: 예시 댓글 조회
const fetchExamples = async () => {
  try {
    const res = await axios.post('/api/filter/examples', {
      categories,
      limit: 10,
      mixDifficulty: true,
    });
    const examples = res.data;
    // 예시 댓글 표시 (상태에 저장 등)
    console.log('예시 댓글:', examples);
  } catch (err) {
    console.error(err);
    alert('예시 댓글 조회에 실패했습니다.');
  }
};

// Step 4: 라벨링 (프론트엔드 상태 관리)
const [dislikeExamples, setDislikeExamples] = useState([]); // 숨기고 싶은 댓글
const [allowExamples, setAllowExamples] = useState([]);     // 괜찮은 댓글

// Step 5: 최종 제출
const submit = async () => {
  // 최소 3개 검증
  if (dislikeExamples.length + allowExamples.length < 3) {
    alert('예시 댓글을 최소 3개 이상 선택해주세요.');
    return;
  }
  
  try {
    const res = await axios.post('/api/filter/preferences', {
      channelId: selectedChannelId, // 또는 null (전역 설정)
      selectedCategories: categories,
      customRuleKeywords: keywords,
      dislikeExamples,
      allowExamples,
    });
    console.log('저장 완료:', res.data);
    alert('필터링 설정이 저장되었습니다.');
  } catch (err) {
    console.error(err);
    const msg = err.response?.data?.error || '저장 실패';
    alert(msg);
  }
};
```

---

## 📌 참고
- 모든 API는 인증이 필요합니다 (세션 기반 로그인)
- 에러 발생 시 적절한 에러 메시지 표시 권장
- DB와 Redis는 백엔드에서 자동 처리됩니다

