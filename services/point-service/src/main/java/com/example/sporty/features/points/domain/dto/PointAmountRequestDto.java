package com.example.sporty.features.points.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PointAmountRequestDto(
        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 1 이상이어야 합니다.")
        Long amount
) {
}
