package com.example.sporty.features.exerciseMatching.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sporty.features.exerciseMatching.domain.entity.MatchUserStateEntity;
import jakarta.persistence.LockModeType;

public interface MatchUserStateRepository extends JpaRepository<MatchUserStateEntity, Long> {
    // MariaDB 원자적 upsert: 최초 요청끼리 경합해도 PK 예외로 트랜잭션이 망가지지 않는다.
    // 기존 withdrawn 값은 절대로 false로 덮어쓰지 않는다. 행 잠금은 커밋까지 유지된다.
    @Modifying
    @Query(value = """
            INSERT INTO match_user_state (user_id, withdrawn) VALUES (:userId, false)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    int ensureRow(@Param("userId") Long userId);

    // REPEATABLE READ에서도 과거 스냅샷이 아닌 잠금 획득 후 상태를 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MatchUserStateEntity s where s.userId = :userId")
    Optional<MatchUserStateEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
