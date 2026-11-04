package com.example.sporty.features.points.domain.entity;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.UpdateTimestamp;

import com.example.sporty.features.commons.exception.points.PointOperationConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A durable operation key and its result are committed with the wallet change. */
@Entity
@Table(name = "point_idempotency_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointOperationEntity {
    @Id
    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "operation_status", nullable = false, length = 16)
    private String status;

    @Column(name = "result_balance")
    private Long resultBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void requireSameRequest(Long userId, Long amount, String kind) {
        if (!Objects.equals(this.userId, userId) || !Objects.equals(this.amount, amount)
                || !Objects.equals(this.kind, kind)) {
            throw new PointOperationConflictException();
        }
    }

    public void complete(String status, Long balance) {
        this.status = status;
        this.resultBalance = balance;
    }
}
