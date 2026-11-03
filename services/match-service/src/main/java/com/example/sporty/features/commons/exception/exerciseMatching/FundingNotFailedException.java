package com.example.sporty.features.commons.exception.exerciseMatching;

public class FundingNotFailedException extends RuntimeException {

    public FundingNotFailedException() {
        super("정원이 충족되어 펀딩 실패 처리를 할 수 없습니다.");
    }
}
