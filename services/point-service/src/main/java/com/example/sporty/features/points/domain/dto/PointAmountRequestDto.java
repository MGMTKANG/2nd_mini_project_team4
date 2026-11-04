package com.example.sporty.features.points.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PointAmountRequestDto(
        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 1 이상이어야 합니다.")
        Long amount,
        // Nullable only for rolling deployment compatibility with the previous match-service.
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                message = "operationId는 UUID여야 합니다.")
        String operationId
) {
}
