package com.example.sporty.features.commons.exception.points;

public class InsufficientPointsException extends RuntimeException {

    public InsufficientPointsException() {
        super("포인트가 부족합니다.");
    }
}
