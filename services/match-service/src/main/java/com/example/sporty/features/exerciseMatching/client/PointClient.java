package com.example.sporty.features.exerciseMatching.client;

/** 매치 서비스가 포인트 서비스에 요청하는 참가비 차감·환불 (SAGA 보상 트랜잭션). */
public interface PointClient {

    // 잔액 부족 시 commons.exception.exerciseMatching.InsufficientPointsException을 던진다.
    Long deduct(Long userId, Long amount);

    Long deduct(Long userId, Long amount, String operationId);
    Long refund(Long userId, Long amount, String operationId);
    Long cancel(Long userId, Long amount, String operationId);

    // 펀딩 실패(정원 미달) 보상 트랜잭션에서 호출한다.
    Long refund(Long userId, Long amount);
}
