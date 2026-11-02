package com.example.sporty.features.commons.exception.exerciseMatching;

public class MatchCapacityBelowParticipantsException extends RuntimeException {

    public MatchCapacityBelowParticipantsException() {
        super("모집 정원은 현재 참가 인원 이상이어야 합니다.");
    }
}
