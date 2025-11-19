package com.medi.backend.youtube.redis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Caption;
import com.google.api.services.youtube.model.CaptionListResponse;
import com.medi.backend.youtube.redis.util.YoutubeApiClientUtil;
import com.medi.backend.youtube.service.YoutubeOAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * YouTube 비디오 스크립트(자막) 저장 서비스 구현체
 * 
 * 주요 기능:
 * 1. YouTube Data API v3 Captions API를 사용하여 자막 데이터 수집
 * 2. 한국어 자막 조회 (languages=['ko'])
 * 3. Redis에 텍스트 형식으로 저장
 * 
 * Redis 저장 형식:
 * - Key: video:{video_id}:transcript
 * - Type: String (JSON)
 * - Value: JSON 형식의 스크립트 데이터
 *   {
 *     "video_id": "KNY8AGkPXC4",
 *     "video_title": "비디오 제목",
 *     "transcript": "스크립트 텍스트"
 *   }
 * 
 * 비디오 제목은 레디스의 video:{video_id}:meta:json에서 가져옴
 * 
 * Python 코드 참고:
 * ```python
 * from youtube_transcript_api import YouTubeTranscriptApi
 * client = YouTubeTranscriptApi()
 * fetched = client.fetch(video_id, languages=['ko'])
 * transcript_text = "\n".join([entry['text'] for entry in fetched.to_raw_data()])
 * ```
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeTranscriptServiceImpl implements YoutubeTranscriptService {

    private final YoutubeOAuthService youtubeOAuthService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final com.medi.backend.youtube.service.YoutubeDataApiClient youtubeDataApiClient;
    private final com.medi.backend.youtube.config.YoutubeDataApiProperties youtubeDataApiProperties;

    /**
     * 특정 비디오의 스크립트(자막)를 Redis에 저장
     * 
     * @param videoId YouTube 비디오 ID
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @return 저장 성공 여부
     */
    @Override
    public boolean saveTranscriptToRedis(String videoId, Integer userId) {
        try {
            // 1. videoId 검증
            if (videoId == null || videoId.isBlank()) {
                log.warn("비디오 ID가 없습니다: videoId={}", videoId);
                return false;
            }

            // 2. OAuth 토큰 가져오기
            String token = youtubeOAuthService.getValidAccessToken(userId);
            if (token == null || token.isBlank()) {
                log.warn("OAuth 토큰을 가져올 수 없습니다: userId={}", userId);
                return false;
            }

            // 3. YouTube API 클라이언트 생성
            YouTube yt = YoutubeApiClientUtil.buildClient(token);

            // 4. 클라이언트를 받는 메서드 호출
            return saveTranscriptToRedisWithClient(videoId, yt);

        } catch (Exception e) {
            log.error("영상 {}의 자막 저장 실패: {}", videoId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 특정 비디오의 스크립트(자막)를 Redis에 저장 (클라이언트 재사용)
     * 
     * @param videoId YouTube 비디오 ID
     * @param yt YouTube API 클라이언트
     * @return 저장 성공 여부
     */
    private boolean saveTranscriptToRedisWithClient(String videoId, YouTube yt) {
        try {
            // 1. videoId 검증
            if (videoId == null || videoId.isBlank()) {
                log.warn("비디오 ID가 없습니다: videoId={}", videoId);
                return false;
            }

            // 2. 자막 목록 조회
            // API 키 fallback: API 키 우선, 실패 시 OAuth 토큰으로 fallback
            CaptionListResponse captionsResponse;
            try {
                if (youtubeDataApiClient.hasApiKeys()) {
                    try {
                        captionsResponse = youtubeDataApiClient.fetchCaptions(videoId);
                        log.debug("자막 목록 조회 성공 (API 키): videoId={}", videoId);
                    } catch (com.medi.backend.youtube.exception.NoAvailableApiKeyException ex) {
                        if (!youtubeDataApiProperties.isEnableFallback()) {
                            throw ex;
                        }
                        log.debug("YouTube Data API 키 사용 불가, OAuth 토큰으로 폴백: videoId={}", videoId);
                        YouTube.Captions.List captionsRequest = yt.captions()
                            .list(Arrays.asList("snippet"), videoId);
                        captionsResponse = captionsRequest.execute();
                    }
                } else {
                    YouTube.Captions.List captionsRequest = yt.captions()
                        .list(Arrays.asList("snippet"), videoId);
                    captionsResponse = captionsRequest.execute();
                }
            } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                // API 키 쿼터 초과 등 403 에러 처리
                if (youtubeDataApiClient.hasApiKeys() && youtubeDataApiProperties.isEnableFallback() 
                        && e.getStatusCode() == 403) {
                    String errorReason = com.medi.backend.youtube.redis.util.YoutubeErrorUtil.extractErrorReason(e);
                    if ("quotaExceeded".equals(errorReason) || "dailyLimitExceeded".equals(errorReason) 
                            || "userRateLimitExceeded".equals(errorReason)) {
                        log.debug("YouTube Data API 키 쿼터 초과, OAuth 토큰으로 폴백: videoId={}", videoId);
                        YouTube.Captions.List captionsRequest = yt.captions()
                            .list(Arrays.asList("snippet"), videoId);
                        captionsResponse = captionsRequest.execute();
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }

            if (captionsResponse.getItems() == null || captionsResponse.getItems().isEmpty()) {
                log.info("영상 {}에 자막이 없습니다", videoId);
                return false;
            }

            // 2-1. 디버깅: 모든 자막 정보 로그 출력
            log.info("영상 {}의 자막 목록 (총 {}개):", videoId, captionsResponse.getItems().size());
            for (Caption caption : captionsResponse.getItems()) {
                String lang = caption.getSnippet().getLanguage();
                String trackKind = caption.getSnippet().getTrackKind();
                String name = caption.getSnippet().getName();
                log.info("  - 언어: {}, trackKind: {}, name: {}, id: {}", 
                    lang, trackKind, name, caption.getId());
            }

            // 3. 한국어 자막 찾기 (수동 자막 우선, 없으면 자동 생성 자막)
            Caption koreanCaption = null;
            Caption autoGeneratedCaption = null;
            
            for (Caption caption : captionsResponse.getItems()) {
                String language = caption.getSnippet().getLanguage();
                if ("ko".equals(language)) {
                    String trackKind = caption.getSnippet().getTrackKind();
                    
                    log.info("영상 {}의 한국어 자막 발견: trackKind={}, id={}", 
                        videoId, trackKind, caption.getId());
                    
                    // 수동 자막 우선 선택 (trackKind가 "standard"이거나 null인 경우)
                    // 참고: YouTube API에서 수동 자막은 trackKind가 null이거나 "standard"일 수 있음
                    if (trackKind == null || "standard".equalsIgnoreCase(trackKind)) {
                        koreanCaption = caption;
                        log.info("영상 {}의 수동 자막 선택: id={}", videoId, caption.getId());
                        break;  // 수동 자막 찾으면 바로 사용
                    }
                    
                    // 자동 생성 자막은 백업으로 저장 (대소문자 구분 없이)
                    if (trackKind != null && "asr".equalsIgnoreCase(trackKind) && autoGeneratedCaption == null) {
                        autoGeneratedCaption = caption;
                        log.info("영상 {}의 자동 생성 자막 백업 저장: id={}", videoId, caption.getId());
                    }
                }
            }
            
            // 수동 자막이 없으면 자동 생성 자막 사용
            if (koreanCaption == null) {
                koreanCaption = autoGeneratedCaption;
                if (koreanCaption != null) {
                    log.info("영상 {}의 자동 생성 자막 사용: id={}", videoId, koreanCaption.getId());
                }
            }

            if (koreanCaption == null) {
                log.warn("영상 {}에 한국어 자막이 없습니다 (수동/자동 모두 없음)", videoId);
                return false;
            }
            
            // 로그에 자막 종류 기록
            String trackKind = koreanCaption.getSnippet().getTrackKind();
            String captionType = (trackKind == null || "standard".equalsIgnoreCase(trackKind)) ? "수동" : "자동 생성";
            log.info("영상 {}의 한국어 자막 선택 완료: {} (trackKind: {}, id: {})", 
                videoId, captionType, trackKind, koreanCaption.getId());

            // 4. 자막 다운로드
            try {
                YouTube.Captions.Download downloadRequest = yt.captions()
                    .download(koreanCaption.getId());
                
                // 자막을 ByteArrayOutputStream으로 다운로드 후 String으로 변환
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                downloadRequest.executeMediaAndDownloadTo(outputStream);
                String transcriptText = outputStream.toString("UTF-8");

                if (transcriptText == null || transcriptText.isBlank()) {
                    log.warn("영상 {}의 자막 텍스트가 비어있습니다 (다운로드는 성공했으나 내용 없음)", videoId);
                    return false;
                }
                
                log.info("영상 {}의 자막 다운로드 성공: 원본 길이={}자", videoId, transcriptText.length());
                
                // 5. 텍스트 정리 (XML 태그 제거)
                String cleanedTranscript = cleanTranscriptText(transcriptText);
                
                if (cleanedTranscript == null || cleanedTranscript.isBlank()) {
                    log.warn("영상 {}의 자막 텍스트 정리 후 비어있습니다", videoId);
                    return false;
                }
                
                log.info("영상 {}의 자막 텍스트 정리 완료: 정리 후 길이={}자", videoId, cleanedTranscript.length());
                
                // 6. 레디스에서 비디오 메타데이터 조회 (video_title 가져오기)
                String videoTitle = getVideoTitleFromRedis(videoId);
                
                // 7. JSON 형식으로 변환 (순서 보장: video_id, video_title, transcript)
                Map<String, String> transcriptData = new LinkedHashMap<>();
                transcriptData.put("video_id", videoId);
                transcriptData.put("video_title", videoTitle != null ? videoTitle : "");
                transcriptData.put("transcript", cleanedTranscript);
                
                String jsonValue;
                try {
                    jsonValue = objectMapper.writeValueAsString(transcriptData);
                } catch (JsonProcessingException e) {
                    log.error("영상 {}의 JSON 변환 실패: {}", videoId, e.getMessage(), e);
                    return false;
                }
                
                // 8. Redis에 저장
                String redisKey = "video:" + videoId + ":transcript";
                stringRedisTemplate.opsForValue().set(redisKey, jsonValue);
                stringRedisTemplate.expire(redisKey, Duration.ofDays(3));

                log.info("영상 {}의 자막 저장 완료: Redis key={}, JSON 길이={}자", 
                    videoId, redisKey, jsonValue.length());
                return true;
                
            } catch (Exception downloadException) {
                log.error("영상 {}의 자막 다운로드 실패: captionId={}, error={}", 
                    videoId, koreanCaption.getId(), downloadException.getMessage(), downloadException);
                return false;
            }

        } catch (Exception e) {
            log.error("영상 {}의 자막 저장 실패: {}", videoId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 여러 비디오의 스크립트(자막)를 Redis에 일괄 저장
     * 
     * @param videoIds YouTube 비디오 ID 목록
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @return 저장 성공한 비디오 개수
     */
    @Override
    public long saveTranscriptsToRedis(List<String> videoIds, Integer userId) {
        if (videoIds == null || videoIds.isEmpty()) {
            log.warn("비디오 ID 리스트가 비어있습니다: userId={}", userId);
            return 0;
        }

        try {
            // 클라이언트를 한 번만 생성하여 재사용
            String token = youtubeOAuthService.getValidAccessToken(userId);
            if (token == null || token.isBlank()) {
                log.warn("OAuth 토큰을 가져올 수 없습니다: userId={}", userId);
                return 0;
            }

            YouTube yt = YoutubeApiClientUtil.buildClient(token);
            return saveTranscriptsToRedis(videoIds, yt);

        } catch (Exception e) {
            log.error("일괄 자막 저장 실패: userId={}, {}", userId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 여러 비디오의 스크립트(자막)를 Redis에 일괄 저장 (클라이언트 재사용)
     * 
     * 동기화 서비스에서 이미 생성한 YouTube API 클라이언트를 재사용하여
     * OAuth 토큰 조회 및 클라이언트 생성을 생략합니다.
     * 
     * @param videoIds YouTube 비디오 ID 목록
     * @param yt YouTube API 클라이언트 (재사용)
     * @return 저장 성공한 비디오 개수
     */
    @Override
    public long saveTranscriptsToRedis(List<String> videoIds, YouTube yt) {
        if (videoIds == null || videoIds.isEmpty()) {
            log.warn("비디오 ID 리스트가 비어있습니다");
            return 0;
        }

        if (yt == null) {
            log.warn("YouTube API 클라이언트가 null입니다");
            return 0;
        }

        long successCount = 0;
        for (String videoId : videoIds) {
            try {
                if (saveTranscriptToRedisWithClient(videoId, yt)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("비디오 {}의 자막 저장 실패: {}", videoId, e.getMessage());
                // 한 비디오 실패해도 다른 비디오는 계속 처리
            }
        }

        log.info("일괄 자막 저장 완료: 성공={}개, 전체={}개", successCount, videoIds.size());
        return successCount;
    }

    /**
     * Redis에서 특정 비디오의 스크립트 조회
     * 
     * @param videoId YouTube 비디오 ID
     * @return 스크립트 텍스트 (없으면 null)
     */
    @Override
    public String getTranscriptFromRedis(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return null;
        }

        String redisKey = "video:" + videoId + ":transcript";
        return stringRedisTemplate.opsForValue().get(redisKey);
    }
    
    /**
     * 레디스에서 비디오 제목 조회
     * 
     * @param videoId YouTube 비디오 ID
     * @return 비디오 제목 (없으면 null)
     */
    private String getVideoTitleFromRedis(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return null;
        }
        
        try {
            String metaKey = "video:" + videoId + ":meta:json";
            String metaJson = stringRedisTemplate.opsForValue().get(metaKey);
            
            if (metaJson == null || metaJson.isBlank()) {
                log.warn("영상 {}의 메타데이터를 레디스에서 찾을 수 없습니다", videoId);
                return null;
            }
            
            // JSON 파싱하여 video_title 추출
            Map<String, Object> metaData = objectMapper.readValue(
                metaJson, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            Object titleObj = metaData.get("video_title");
            return titleObj != null ? titleObj.toString() : null;
            
        } catch (JsonProcessingException e) {
            log.error("영상 {}의 메타데이터 JSON 파싱 실패: {}", videoId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("영상 {}의 제목 조회 실패: {}", videoId, e.getMessage());
            return null;
        }
    }

    /**
     * 자막 텍스트 정리 (시간 정보 제거, 순수 텍스트만 추출)
     * 
     * YouTube Captions API는 XML, VTT, SRT 등 다양한 형식으로 반환할 수 있으므로,
     * 시간 정보를 제거하고 순수 텍스트만 \n으로 구분하여 추출합니다.
     * 
     * 클라이언트 요구사항:
     * - 시간 정보는 필요없음
     * - \n으로 구분하면 그만
     * - [음악], [웃음] 같은 메타데이터는 유지
     * 
     * @param rawTranscript 원본 자막 텍스트
     * @return 정리된 자막 텍스트 (\n으로 구분)
     */
    private String cleanTranscriptText(String rawTranscript) {
        if (rawTranscript == null) {
            return "";
        }

        // 1. XML 태그 제거 (예: <text start="1.5" dur="2.0">텍스트</text>)
        String cleaned = rawTranscript
            .replaceAll("<[^>]+>", "")  // XML 태그 제거
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&amp;", "&")
            .replaceAll("&quot;", "\"")
            .replaceAll("&#39;", "'");

        // 2. 라인별로 처리하여 시간 정보 및 불필요한 라인 제거
        String[] lines = cleaned.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            
            // 빈 라인 스킵
            if (line.isEmpty()) {
                continue;
            }
            
            // VTT/SRT 타임스탬프 제거
            // 패턴 1: 00:00:01.500 --> 00:00:03.500 (화살표 포함, 두 자리 시간)
            // 패턴 2: 0:00:05.920,0:00:14.440 (쉼표로 구분, 한 자리 시간)
            // 패턴 3: 00:00:01.500,00:00:03.500 (쉼표로 구분, 두 자리 시간)
            // 패턴 4: 0:00:05.920 --> 0:00:14.440 (화살표 포함, 한 자리 시간)
            // 패턴 5: 0:00:05.920 (단일 타임스탬프로 시작하는 라인)
            if (line.matches(".*-->.*") ||  // 화살표 포함 (모든 형식)
                line.matches("^\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}[,\\s]*\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}.*") ||  // 두 타임스탬프 (쉼표 또는 공백으로 구분)
                line.matches("^\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}\\s*$") ||  // 단일 타임스탬프만 있는 라인
                line.matches("^\\d{1,2}:\\d{2}:\\d{2}[.,]\\d{3}[,\\s]*$")) {  // 단일 타임스탬프 + 쉼표/공백만
                continue;
            }
            
            // 숫자만 있는 라인 제거 (SRT 시퀀스 번호, 예: 1, 2, 3)
            if (line.matches("^\\d+$")) {
                continue;
            }
            
            // WEBVTT, Kind:, Language: 같은 메타데이터 라인 제거
            if (line.startsWith("WEBVTT") || 
                line.startsWith("Kind:") || 
                line.startsWith("Language:") ||
                line.startsWith("NOTE")) {
                continue;
            }
            
            // 유효한 텍스트 라인만 추가
            if (!line.isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
        }
        
        return result.toString().trim();
    }
}
