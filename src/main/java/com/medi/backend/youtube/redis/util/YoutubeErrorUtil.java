package com.medi.backend.youtube.redis.util;

import lombok.extern.slf4j.Slf4j;

/**
 * YouTube API 에러 처리 유틸리티
 * 
 * 중복 코드 제거:
 * - 여러 서비스에서 동일한 에러 추출 로직이 중복됨
 * - 공통 유틸리티로 분리하여 코드 중복 제거
 */
@Slf4j
public class YoutubeErrorUtil {
    
    /**
     * Google API 에러 응답에서 에러 원인(reason) 추출
     * 
     * 참고: channel_comment_fetcher.py의 _extract_error_reason 메서드
     * Python 코드:
     *   data = json.loads(error.content.decode("utf-8"))
     *   errors = data.get("error", {}).get("errors", [])
     *   return errors[0].get("reason", "")
     * 
     * @param e GoogleJsonResponseException 예외 객체
     * @return 에러 원인 문자열 (예: "commentsDisabled", "captionNotFound")
     */
    public static String extractErrorReason(com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
        try {
            com.google.api.client.googleapis.json.GoogleJsonError error = e.getDetails();
            if (error != null && error.getErrors() != null && !error.getErrors().isEmpty()) {
                com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo firstError = 
                    error.getErrors().get(0);
                if (firstError != null) {
                    return firstError.getReason();
                }
            }
        } catch (Exception ex) {
            log.debug("에러 원인 추출 실패: {}", ex.getMessage());
        }
        return "";
    }
}

