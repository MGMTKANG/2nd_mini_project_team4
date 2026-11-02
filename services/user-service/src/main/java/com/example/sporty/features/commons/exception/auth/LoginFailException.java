package com.example.sporty.features.commons.exception.auth;

public class LoginFailException extends RuntimeException {
    public LoginFailException(String message) {
        super(message);
    }
}
