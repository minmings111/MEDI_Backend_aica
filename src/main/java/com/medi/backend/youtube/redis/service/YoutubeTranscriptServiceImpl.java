// package com.medi.backend.youtube.redis.service;

// import java.time.Duration;
// import java.util.List;

// import org.springframework.data.redis.core.StringRedisTemplate;
// import org.springframework.stereotype.Service;

// import com.google.api.services.youtube.YouTube;
// import com.google.api.services.youtube.model.Caption;
// import com.google.api.services.youtube.model.CaptionListResponse;
// import com.medi.backend.youtube.redis.util.YoutubeApiClientUtil;
// import com.medi.backend.youtube.service.YoutubeOAuthService;

// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;

// /**
//  * YouTube 비디오 스크립트(자막) 저장 서비스 구현체
//  * 
//  * 주요 기능:
//  * 1. YouTube Data API v3 Captions API를 사용하여 자막 데이터 수집
//  * 2. 한국어 자막 우선 조회
//  * 3. Redis에 텍스트 형식으로 저장
//  * 
//  * Redis 저장 형식:
//  * - Key: video:{video_id}:transcript
//  * - Type: String
//  * - Value: 스크립트 텍스트 원본 (예: "[음악]\n경민이 밖에 나가있을 때...")
//  * 
//  * 참고:
//  * - Python 코드: youtube_transcript_api 라이브러리 사용
//  * - Java 구현: YouTube Data API v3 Captions API 사용
//  * - 언어 우선순위: 한국어(ko) → 영어(en) → 기타
//  */
// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class YoutubeTranscriptServiceImpl implements YoutubeTranscriptService {

//     private final YoutubeOAuthService youtubeOAuthService;
//     private final StringRedisTemplate stringRedisTemplate;

//     /**
//      * 특정 비디오의 스크립트(자막)를 Redis에 저장
//      * 
//      * Python 코드 참고:
//      * ```python
//      * from youtube_transcript_api import YouTubeTranscriptApi
//      * client = YouTubeTranscriptApi()
//      * fetched = client.fetch(video_id, languages=['ko'])
//      * transcript_text = "\n".join([entry['text'] for entry in fetched.to_raw_data()])
//      * ```
//      * 
//      * @param videoId YouTube 비디오 ID
//      * @param userId 사용자 ID (OAuth 토큰 조회용)
//      * @return 저장 성공 여부
//      */
//     @Override
//     public boolean saveTranscriptToRedis(String videoId, Integer userId) {
//         try {
//             // 1. videoId 검증
//             if (videoId == null || videoId.isBlank()) {
//                 log.warn("비디오 ID가 없습니다: videoId={}", videoId);
//                 return false;
//             }

//             // 2. OAuth 토큰 가져오기
//             String token = youtubeOAuthService.getValidAccessToken(userId);
//             if (token == null || token.isBlank()) {
//                 log.warn("OAuth 토큰을 가져올 수 없습니다: userId={}", userId);
//                 return false;
//             }

//             // 3. YouTube API 클라이언트 생성
//             YouTube yt = YoutubeApiClientUtil.buildClient(token);

//             // 4. 자막 목록 조회
//             // ⭐ YouTube Captions API 요청 생성
//             // API 엔드포인트: youtube.captions.list
//             // 용도: 특정 영상의 자막 목록 조회
//             YouTube.Captions.List captionsRequest = yt.captions()
//                 .list("snippet", videoId);  // part="snippet", videoId 지정
            
//             // ⭐ 실제 YouTube Captions API 호출 실행
//             // 이 시점에서 YouTube 서버로 HTTP 요청이 전송됨
//             CaptionListResponse captionsResponse = captionsRequest.execute();

//             if (captionsResponse.getItems() == null || captionsResponse.getItems().isEmpty()) {
//                 log.info("영상 {}에 자막이 없습니다", videoId);
//                 return false;
//             }

//             // 5. 한국어 자막 우선 선택 (Python: languages=['ko'])
//             Caption koreanCaption = null;
//             Caption englishCaption = null;
//             Caption fallbackCaption = null;

//             for (Caption caption : captionsResponse.getItems()) {
//                 String language = caption.getSnippet().getLanguage();
//                 if ("ko".equals(language)) {
//                     koreanCaption = caption;
//                     break;  // 한국어 자막 찾으면 즉시 종료
//                 } else if ("en".equals(language) && englishCaption == null) {
//                     englishCaption = caption;
//                 } else if (fallbackCaption == null) {
//                     fallbackCaption = caption;
//                 }
//             }

//             // 6. 자막 선택 (한국어 → 영어 → 기타 순서)
//             Caption selectedCaption = koreanCaption != null ? koreanCaption
//                 : (englishCaption != null ? englishCaption : fallbackCaption);

//             if (selectedCaption == null) {
//                 log.warn("영상 {}에 사용 가능한 자막이 없습니다", videoId);
//                 return false;
//             }

//             // 7. 자막 다운로드
//             // ⭐ YouTube Captions API 요청 생성
//             // API 엔드포인트: youtube.captions.download
//             // 용도: 특정 자막의 실제 텍스트 내용 다운로드
//             YouTube.Captions.Download downloadRequest = yt.captions()
//                 .download(selectedCaption.getId());
            
//             // ⭐ 실제 YouTube Captions API 호출 실행
//             // 이 시점에서 YouTube 서버로 HTTP 요청이 전송됨
//             String transcriptText = downloadRequest.executeAsString();

//             if (transcriptText == null || transcriptText.isBlank()) {
//                 log.warn("영상 {}의 자막 텍스트가 비어있습니다", videoId);
//                 return false;
//             }

//             // 8. Python 코드 참고: entry['text']를 join하는 부분
//             // YouTube API는 이미 텍스트 형식으로 반환하므로 추가 처리 불필요
//             // 다만, 필요시 XML/JSON 파싱하여 텍스트만 추출 가능
//             String cleanedTranscript = cleanTranscriptText(transcriptText);

//             // 9. Redis에 저장
//             String redisKey = "video:" + videoId + ":transcript";
//             stringRedisTemplate.opsForValue().set(redisKey, cleanedTranscript);
//             stringRedisTemplate.expire(redisKey, Duration.ofDays(3));

//             log.info("영상 {}의 자막 저장 완료: 언어={}, 길이={}자", 
//                 videoId, selectedCaption.getSnippet().getLanguage(), cleanedTranscript.length());
//             return true;

//         } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
//             String errorReason = extractErrorReason(e);
//             if ("captionNotFound".equals(errorReason)) {
//                 log.info("영상 {}에 자막이 없습니다", videoId);
//             } else {
//                 log.error("영상 {}의 자막 조회 실패: {} (reason: {})", 
//                     videoId, e.getMessage(), errorReason);
//             }
//             return false;
//         } catch (Exception e) {
//             log.error("영상 {}의 자막 저장 실패: {}", videoId, e.getMessage(), e);
//             return false;
//         }
//     }

//     /**
//      * 여러 비디오의 스크립트(자막)를 Redis에 일괄 저장
//      * 
//      * @param videoIds YouTube 비디오 ID 목록
//      * @param userId 사용자 ID (OAuth 토큰 조회용)
//      * @return 저장 성공한 비디오 개수
//      */
//     @Override
//     public long saveTranscriptsToRedis(List<String> videoIds, Integer userId) {
//         if (videoIds == null || videoIds.isEmpty()) {
//             log.warn("비디오 ID 리스트가 비어있습니다: userId={}", userId);
//             return 0;
//         }

//         long successCount = 0;
//         for (String videoId : videoIds) {
//             try {
//                 if (saveTranscriptToRedis(videoId, userId)) {
//                     successCount++;
//                 }
//             } catch (Exception e) {
//                 log.error("비디오 {}의 자막 저장 실패: {}", videoId, e.getMessage());
//                 // 한 비디오 실패해도 다른 비디오는 계속 처리
//             }
//         }

//         log.info("일괄 자막 저장 완료: userId={}, 성공={}개, 전체={}개", 
//             userId, successCount, videoIds.size());
//         return successCount;
//     }

//     /**
//      * Redis에서 특정 비디오의 스크립트 조회
//      * 
//      * @param videoId YouTube 비디오 ID
//      * @return 스크립트 텍스트 (없으면 null)
//      */
//     @Override
//     public String getTranscriptFromRedis(String videoId) {
//         if (videoId == null || videoId.isBlank()) {
//             return null;
//         }

//         String redisKey = "video:" + videoId + ":transcript";
//         return stringRedisTemplate.opsForValue().get(redisKey);
//     }

//     /**
//      * 자막 텍스트 정리 (XML/JSON 태그 제거 등)
//      * 
//      * YouTube Captions API는 XML 형식으로 반환할 수 있으므로,
//      * 텍스트만 추출하는 작업이 필요할 수 있습니다.
//      * 
//      * @param rawTranscript 원본 자막 텍스트
//      * @return 정리된 자막 텍스트
//      */
//     private String cleanTranscriptText(String rawTranscript) {
//         if (rawTranscript == null) {
//             return "";
//         }

//         // XML 태그 제거 (예: <text start="1.5" dur="2.0">텍스트</text>)
//         String cleaned = rawTranscript
//             .replaceAll("<[^>]+>", "")  // XML 태그 제거
//             .replaceAll("&lt;", "<")
//             .replaceAll("&gt;", ">")
//             .replaceAll("&amp;", "&")
//             .replaceAll("&quot;", "\"")
//             .replaceAll("&#39;", "'")
//             .trim();

//         return cleaned;
//     }

//     /**
//      * Google API 에러 응답에서 에러 원인(reason) 추출
//      * 
//      * @param e GoogleJsonResponseException 예외 객체
//      * @return 에러 원인 문자열 (예: "captionNotFound")
//      */
//     private String extractErrorReason(com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
//         try {
//             com.google.api.client.googleapis.json.GoogleJsonError error = e.getDetails();
//             if (error != null && error.getErrors() != null && !error.getErrors().isEmpty()) {
//                 com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo firstError = 
//                     error.getErrors().get(0);
//                 if (firstError != null) {
//                     return firstError.getReason();
//                 }
//             }
//         } catch (Exception ex) {
//             log.debug("에러 원인 추출 실패: {}", ex.getMessage());
//         }
//         return "";
//     }
// }

