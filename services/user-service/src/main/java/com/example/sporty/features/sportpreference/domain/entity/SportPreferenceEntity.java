package com.example.sporty.features.sportpreference.domain.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.sporty.features.commons.util.SportType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sport_preference", uniqueConstraints =
    @UniqueConstraint(name = "uk_sport_preference_profile_sport", columnNames = {"profile_id", "sport_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SportPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false, length = 15)
    private SportType sportType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public SportPreferenceEntity(Long profileId, SportType sportType) {
        this.profileId = profileId;
        this.sportType = sportType;
    }
}
