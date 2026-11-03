package com.example.sporty.features.points.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.points.domain.dto.PointBalanceResponseDto;
import com.example.sporty.features.points.service.PointService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "포인트(Point)", description = "로그인 사용자 포인트 잔액 조회 API")
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @Operation(summary = "내 포인트 잔액 조회", description = "마이페이지에서 사용하는 로그인 사용자의 포인트 잔액을 반환한다.")
    @GetMapping("/me")
    public ResponseEntity<PointBalanceResponseDto> getMyBalance(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(new PointBalanceResponseDto(pointService.getBalance(userId)));
    }
}
