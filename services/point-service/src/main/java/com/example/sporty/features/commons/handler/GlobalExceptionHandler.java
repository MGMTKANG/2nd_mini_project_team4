package com.example.sporty.features.commons.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.sporty.features.commons.exception.points.InsufficientPointsException;
import com.example.sporty.features.commons.exception.points.PointOperationConflictException;
import com.example.sporty.features.commons.exception.points.PointOperationCancelledException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .code("INVALID_REQUEST")
                .message(message)
                .build());
    }

    @ExceptionHandler(InsufficientPointsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPoints(InsufficientPointsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .code("INSUFFICIENT_POINTS")
                .message(e.getMessage())
                .build());
    }

    @ExceptionHandler(PointOperationConflictException.class)
    public ResponseEntity<ErrorResponse> handleOperationConflict(PointOperationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .code("POINT_OPERATION_CONFLICT").message(e.getMessage()).build());
    }

    @ExceptionHandler(PointOperationCancelledException.class)
    public ResponseEntity<ErrorResponse> handleOperationCancelled(PointOperationCancelledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .code("POINT_OPERATION_CANCELLED").message(e.getMessage()).build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .code("INVALID_REQUEST").message("요청 값이 올바르지 않습니다.").build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .code("INVALID_REQUEST")
                .message("요청 본문을 읽을 수 없습니다.")
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("서버 내부 오류가 발생했습니다.")
                .build());
    }
}
