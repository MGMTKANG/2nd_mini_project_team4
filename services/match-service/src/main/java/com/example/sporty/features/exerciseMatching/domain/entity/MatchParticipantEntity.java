package com.example.sporty.features.exerciseMatching.domain.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.example.sporty.features.exerciseMatching.domain.enums.MatchParticipantRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "match_participant", uniqueConstraints =
    @UniqueConstraint(name = "uk_match_participant_match_user", columnNames = {"match_id", "user_id"}))
@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class MatchParticipantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_participant_id")
    private Long id;

    // 유저 서비스의 user.id 논리 참조. 서비스별 DB 분리로 FK는 두지 않는다.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    @ToString.Exclude
    private MatchEntity match;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20, nullable = false)
    private MatchParticipantRole role;

    // 참가 시점에 차감된 포인트(1인당 참가비). 펀딩 실패 시 이 금액만큼 환불한다. 펀딩 없는 매치는 null이다.
    @Column(name = "contribution_points")
    private Long contributionPoints;

    // New contributions reference a durable debit; null means a pre-migration contribution.
    @Column(name = "point_operation_id", length = 36)
    private String pointOperationId;

    // 환불 완료 후 중복 환불을 막기 위해 호출한다.
    public void clearContribution() {
        this.contributionPoints = null;
    }
}
