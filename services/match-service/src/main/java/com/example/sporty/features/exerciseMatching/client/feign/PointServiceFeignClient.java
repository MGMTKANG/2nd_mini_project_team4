package com.example.sporty.features.exerciseMatching.client.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 포인트 서비스 내부 API. 경로와 응답 형식은 point-service와 맞춤 (docs/match-crowdfunding-erd-spec.md 참고).
@FeignClient(name = "point-service", url = "${clients.point-service.url:}")
public interface PointServiceFeignClient {

    @PostMapping("/internal/points/{userId}/deduct")
    PointBalanceResponse deduct(@PathVariable("userId") Long userId, @RequestBody PointAmountRequest request);

    @PostMapping("/internal/points/{userId}/refund")
    PointBalanceResponse refund(@PathVariable("userId") Long userId, @RequestBody PointAmountRequest request);

    @PostMapping("/internal/points/{userId}/operations/deduct")
    PointBalanceResponse deductOperation(@PathVariable("userId") Long userId, @RequestBody PointAmountRequest request);

    @PostMapping("/internal/points/{userId}/operations/refund")
    PointBalanceResponse refundOperation(@PathVariable("userId") Long userId, @RequestBody PointAmountRequest request);

    @PostMapping("/internal/points/{userId}/operations/cancel")
    PointBalanceResponse cancel(@PathVariable("userId") Long userId, @RequestBody PointAmountRequest request);

    record PointAmountRequest(Long amount, String operationId) {
        PointAmountRequest(Long amount) { this(amount, null); }
    }

    record PointBalanceResponse(Long balance) {
    }
}
