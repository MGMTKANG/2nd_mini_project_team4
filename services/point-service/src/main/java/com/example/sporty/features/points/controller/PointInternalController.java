package com.example.sporty.features.points.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.points.domain.dto.PointAmountRequestDto;
import com.example.sporty.features.points.domain.dto.PointBalanceResponseDto;
import com.example.sporty.features.points.service.PointService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// match-service 등 타 마이크로서비스 Feign 전용 내부 API다.
@Tag(name = "내부 통신 API (Internal)", description = "match-service가 참가/보상 트랜잭션에서 호출하는 포인트 차감·환불 API")
@RestController
@RequestMapping("/internal/points")
@RequiredArgsConstructor
public class PointInternalController {

    private final PointService pointService;

    @Operation(summary = "포인트 차감 (Feign 전용)", description = "매치 참가 시 1인당 참가비만큼 차감한다. 잔액 부족 시 409.")
    @PostMapping("/{userId}/deduct")
    public ResponseEntity<PointBalanceResponseDto> deduct(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody PointAmountRequestDto request
    ) {
        return ResponseEntity.ok(new PointBalanceResponseDto(pointService.deduct(userId, request.amount())));
    }

    @Operation(summary = "포인트 환불 (Feign 전용)", description = "펀딩 실패(정원 미달) 보상 트랜잭션에서 참가비를 환불한다.")
    @PostMapping("/{userId}/refund")
    public ResponseEntity<PointBalanceResponseDto> refund(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody PointAmountRequestDto request
    ) {
        return ResponseEntity.ok(new PointBalanceResponseDto(pointService.refund(userId, request.amount())));
    }
}
