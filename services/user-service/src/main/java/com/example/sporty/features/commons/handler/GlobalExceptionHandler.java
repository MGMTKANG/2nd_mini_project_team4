package com.example.sporty.features.commons.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.sporty.features.commons.exception.auth.InvalidRefreshTokenException;
import com.example.sporty.features.commons.exception.auth.LoginFailException;
import com.example.sporty.features.commons.exception.profiles.ProfileNotFoundException;
import com.example.sporty.features.commons.exception.users.DuplicateEmailException;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.commons.exception.users.PasswordMismatchException;
import com.example.sporty.features.commons.exception.users.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler({
        DuplicateEmailException.class,
        DuplicateNicknameException.class,
        PasswordMismatchException.class,
        IllegalArgumentException.class,
        IllegalStateException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(LoginFailException.class)
    public ResponseEntity<ErrorResponse> handleLoginFail(LoginFailException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({
        UserNotFoundException.class,
        ProfileNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버 내부 오류가 발생했습니다: " + e.getMessage()));
    }
}
