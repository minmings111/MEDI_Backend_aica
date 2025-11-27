package com.medi.backend.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 애플리케이션 전역 예외 처리기.
 * - 보안/인증 관련 예외는 SecurityConfig 쪽에서 처리하고,
 *   이 클래스는 비즈니스/검증 예외를 담당한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스/검증 로직에서 발생한 IllegalArgumentException 처리.
     * 예: 필터 설정 최소 개수 위반 등.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        String path = request != null ? request.getRequestURI() : null;
        log.warn("IllegalArgumentException at {}: {}", path, ex.getMessage());
        ErrorResponse body = ErrorResponse.ofValidation(ex.getMessage(), path);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * @Valid 검증 실패 (RequestBody)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String path = request != null ? request.getRequestURI() : null;
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("MethodArgumentNotValidException at {}: {}", path, message);
        ErrorResponse body = ErrorResponse.ofValidation(message, path);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * @ModelAttribute, 쿼리 파라미터 바인딩 실패 등.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException ex,
            HttpServletRequest request
    ) {
        String path = request != null ? request.getRequestURI() : null;
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("BindException at {}: {}", path, message);
        ErrorResponse body = ErrorResponse.ofValidation(message, path);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 처리되지 않은 모든 예외에 대한 최종 방어선.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        String path = request != null ? request.getRequestURI() : null;
        log.error("Unexpected exception at {}: {}", path, ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.ofInternal("알 수 없는 서버 오류가 발생했습니다.", path);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
