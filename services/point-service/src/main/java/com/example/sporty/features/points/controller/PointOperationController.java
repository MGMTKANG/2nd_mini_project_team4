package com.example.sporty.features.points.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.points.domain.dto.PointBalanceResponseDto;
import com.example.sporty.features.points.domain.dto.PointCancelRequestDto;
import com.example.sporty.features.points.service.PointService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Dedicated keyed protocol. An old point-service has no such routes, so it cannot silently
 * ignore operationId and charge an unrecorded debit during a mixed-version rollout.
 */
@Tag(name = "멱등 포인트 내부 API", description = "operationId가 필수인 차감·환불·취소 계약")
@RestController
@RequestMapping("/internal/points/{userId}/operations")
@RequiredArgsConstructor
public class PointOperationController {
    private final PointService pointService;

    @PostMapping("/deduct")
    public ResponseEntity<PointBalanceResponseDto> deduct(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody PointCancelRequestDto request) {
        return ResponseEntity.ok(new PointBalanceResponseDto(
                pointService.deduct(userId, request.amount(), request.operationId())));
    }

    @PostMapping("/refund")
    public ResponseEntity<PointBalanceResponseDto> refund(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody PointCancelRequestDto request) {
        return ResponseEntity.ok(new PointBalanceResponseDto(
                pointService.refund(userId, request.amount(), request.operationId())));
    }

    @PostMapping("/cancel")
    public ResponseEntity<PointBalanceResponseDto> cancel(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody PointCancelRequestDto request) {
        return ResponseEntity.ok(new PointBalanceResponseDto(
                pointService.cancel(userId, request.amount(), request.operationId())));
    }
}
