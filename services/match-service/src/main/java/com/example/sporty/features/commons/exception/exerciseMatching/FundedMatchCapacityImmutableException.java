package com.example.sporty.features.commons.exception.exerciseMatching;

public class FundedMatchCapacityImmutableException extends RuntimeException {

    public FundedMatchCapacityImmutableException() {
        super("크라우드펀딩 매치는 모집 정원을 변경할 수 없습니다.");
    }
}
