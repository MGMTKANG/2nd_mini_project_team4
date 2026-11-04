package com.example.sporty.features.exerciseMatching.domain.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

/** Remote debit intent survives rollback of the match transaction. No FK to rollback-prone rows. */
@Entity
@Table(name = "match_point_debit")
@Getter
@NoArgsConstructor
public class PointDebitEntity {
    @Id @Column(name = "operation_id", length = 36)
    private String operationId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long amount;
    @Column(nullable = false, length = 12)
    private String status;
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    public PointDebitEntity(String operationId, Long userId, Long amount) {
        this.operationId = operationId;
        this.userId = userId;
        this.amount = amount;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }
    public boolean isPending() { return "PENDING".equals(status); }
    public void applied() { status = "APPLIED"; }
    public void cancelled() { status = "CANCELLED"; }
}
