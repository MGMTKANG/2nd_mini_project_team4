package com.example.sporty.features.profiles.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.sporty.features.profiles.domain.entity.ProfileEntity;

@Repository
public interface ProfileRepository extends JpaRepository<ProfileEntity, Long> {
    Optional<ProfileEntity> findByUserId(Long userId);
    List<ProfileEntity> findByUserIdIn(Collection<Long> userIds);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndUserIdNot(String nickname, Long userId);
}
