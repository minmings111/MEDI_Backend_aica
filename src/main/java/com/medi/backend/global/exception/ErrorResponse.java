package com.medi.backend.global.exception;

import java.time.LocalDateTime;

/**
 * API 에러 응답 공통 포맷.
 */
public class ErrorResponse {

    private final String code;
    private final String message;
    private final LocalDateTime timestamp;
    private final String path;

    public ErrorResponse(String code, String message, LocalDateTime timestamp, String path) {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
        this.path = path;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getPath() {
        return path;
    }

    // ===== 정적 팩토리 메서드 =====

    public static ErrorResponse ofValidation(String message, String path) {
        return new ErrorResponse("VALIDATION_ERROR", message, LocalDateTime.now(), path);
    }

    public static ErrorResponse ofNotFound(String message, String path) {
        return new ErrorResponse("NOT_FOUND", message, LocalDateTime.now(), path);
    }

    public static ErrorResponse ofInternal(String message, String path) {
        return new ErrorResponse("INTERNAL_SERVER_ERROR", message, LocalDateTime.now(), path);
    }
}
