package com.example.sporty.features.exerciseMatching.domain.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

/** Refund outbox committed atomically with participant removal / contribution clearing. */
@Entity
@Table(name = "match_point_refund")
@Getter
@NoArgsConstructor
public class PointRefundEntity {
    @Id @Column(name = "operation_id", length = 36)
    private String operationId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long amount;
    @Column(nullable = false, length = 8)
    private String kind;
    @Column(nullable = false, length = 12)
    private String status;
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;
    public PointRefundEntity(String id, Long userId, Long amount, String kind) {
        this.operationId = id;
        this.userId = userId;
        this.amount = amount;
        this.kind = kind;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }
    public boolean isPending() { return "PENDING".equals(status); }
    public void complete() { status = "DONE"; }
}
