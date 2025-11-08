package com.medi.backend.youtube.exception;

public class NoAvailableApiKeyException extends RuntimeException {

    public NoAvailableApiKeyException(String message) {
        super(message);
    }

    public NoAvailableApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}

