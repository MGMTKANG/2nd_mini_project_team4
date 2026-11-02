package com.example.sporty.features.exerciseMatching.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventEntity {
    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", columnDefinition = "DATETIME", nullable = false)
    private LocalDateTime processedAt;
}
