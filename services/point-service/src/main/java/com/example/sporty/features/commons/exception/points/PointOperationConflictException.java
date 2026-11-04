package com.example.sporty.features.commons.exception.points;

public class PointOperationConflictException extends RuntimeException {
    public PointOperationConflictException() {
        super("이미 사용된 포인트 작업 ID의 요청 내용이 다릅니다.");
    }
}
