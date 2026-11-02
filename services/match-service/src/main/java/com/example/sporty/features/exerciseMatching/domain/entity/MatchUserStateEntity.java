package com.example.sporty.features.exerciseMatching.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매치 DB의 사용자별 잠금 및 탈퇴 기록. 유저 DB와 FK를 연결하지 않는다. */
@Entity
@Table(name = "match_user_state")
@Getter
@NoArgsConstructor
public class MatchUserStateEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "withdrawn", nullable = false)
    private boolean withdrawn;

    public void markWithdrawn() {
        withdrawn = true;
    }
}
