package com.example.sporty.features.points.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import com.example.sporty.features.points.domain.entity.PointAccountEntity;

import jakarta.persistence.LockModeType;

public interface PointAccountRepository extends JpaRepository<PointAccountEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO point_wallet (user_id, version, balance, created_at, updated_at)
            VALUES (:userId, 0, :balance, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    void ensureAccount(@Param("userId") Long userId, @Param("balance") Long balance);

    // 차감·환불은 동시 요청 간 경합을 막기 위해 행을 잠그고 조회한다.
    Optional<PointAccountEntity> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointAccountEntity a where a.userId = :userId")
    Optional<PointAccountEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
