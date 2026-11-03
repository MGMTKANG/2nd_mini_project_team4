package com.example.sporty.features.sportpreference.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;

@Repository
public interface SportPreferenceRepository extends JpaRepository<SportPreferenceEntity, Long> {
    List<SportPreferenceEntity> findByProfileId(Long profileId);
    void deleteByProfileId(Long profileId);
}
