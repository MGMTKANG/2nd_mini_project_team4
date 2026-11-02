package com.example.sporty.features.internal.dto;

public record UserSummaryDto(Long userId, String status) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
