package com.example.sporty.features.commons.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import com.example.sporty.features.commons.exception.ExternalServiceUnavailableException;
import com.example.sporty.features.commons.exception.ai.AiDraftException;
import com.example.sporty.features.commons.exception.ai.AiSearchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 입력 검증 실패(@Valid) → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 내용을 확인해주세요.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("INVALID_INPUT")
                        .message(message)
                        .build());
    }

    // 요청 본문을 읽을 수 없음(JSON 형식 오류, 글자 인코딩 오류 등) → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("INVALID_INPUT")
                        .message("요청 형식이 올바르지 않습니다.")
                        .build());
    }

    // AI가 문장에서 검색 조건을 찾지 못함 → 400
    @ExceptionHandler(AiSearchException.class)
    public ResponseEntity<ErrorResponse> handleAiSearch(AiSearchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("AI_SEARCH_FAILED")
                        .message(e.getMessage())
                        .build());
    }

    // AI가 문장에서 매치 정보를 찾지 못함 → 400
    @ExceptionHandler(AiDraftException.class)
    public ResponseEntity<ErrorResponse> handleAiDraft(AiDraftException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .code("AI_DRAFT_FAILED")
                        .message(e.getMessage())
                        .build());
    }

    // 다른 서비스(match-service 등)가 응답하지 않음 → 503 (match-service와 같은 코드)
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceUnavailable(ExternalServiceUnavailableException e) {
        log.warn("외부 서비스 호출 실패: service={}", e.getServiceName(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .code("EXTERNAL_SERVICE_UNAVAILABLE")
                        .message(e.getMessage())
                        .build());
    }

    // OpenAI 호출 실패(키 오류, OpenAI 장애, 네트워크 연결 실패, 응답 처리 실패 등) → 503
    @ExceptionHandler({ NonTransientAiException.class, TransientAiException.class, RestClientException.class })
    public ResponseEntity<ErrorResponse> handleAiUnavailable(RuntimeException e) {
        log.warn("OpenAI 호출 실패", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .code("AI_SERVICE_UNAVAILABLE")
                        .message("AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.")
                        .build());
    }
}