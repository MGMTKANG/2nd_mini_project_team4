package com.example.sporty.features.commons.exception.exerciseMatching;

public class InsufficientPointsException extends RuntimeException {

    public InsufficientPointsException() {
        super("포인트가 부족하여 참가할 수 없습니다.");
    }
}
