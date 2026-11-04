package com.example.sporty.features.points.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
SAGA 패턴 시연용 최소 포인트 잔액 계정이다. 결제·충전 연동은 하지 않는다.
userId는 user-service의 user.id 논리 참조다. 서비스별 DB 분리로 FK는 두지 않는다.
*/
@Entity
@Table(name = "point_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Long balance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public PointAccountEntity(Long userId, Long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public void deduct(Long amount) {
        this.balance -= amount;
    }

    public void refund(Long amount) {
        this.balance += amount;
    }

    public boolean hasAtLeast(Long amount) {
        return this.balance >= amount;
    }
}
