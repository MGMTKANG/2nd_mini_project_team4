package com.example.sporty.features.commons.exception.auth;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("유효하지 않은 리프레시 토큰입니다.");
    }

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
