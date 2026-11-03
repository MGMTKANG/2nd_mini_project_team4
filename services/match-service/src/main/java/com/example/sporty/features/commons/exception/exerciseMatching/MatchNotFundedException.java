package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchNotFundedException extends RuntimeException {

    public MatchNotFundedException() {
        super("크라우드펀딩 매치가 아니어서 펀딩 실패 처리를 할 수 없습니다.");
    }
}
