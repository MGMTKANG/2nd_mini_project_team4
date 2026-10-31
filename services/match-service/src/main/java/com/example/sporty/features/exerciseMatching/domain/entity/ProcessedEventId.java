package com.example.sporty.features.exerciseMatching.domain.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "consumer_name", length = 100, nullable = false)
    private String consumerName;

    @Column(name = "event_id", length = 36, columnDefinition = "CHAR(36)", nullable = false)
    private String eventId;
}
