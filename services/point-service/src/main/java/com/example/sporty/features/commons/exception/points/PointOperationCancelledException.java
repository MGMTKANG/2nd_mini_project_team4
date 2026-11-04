package com.example.sporty.features.commons.exception.points;

public class PointOperationCancelledException extends RuntimeException {
    public PointOperationCancelledException() {
        super("이미 취소된 포인트 차감 작업입니다.");
    }
}
