package com.example.sporty.features.points.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sporty.features.points.domain.entity.PointOperationEntity;

import jakarta.persistence.LockModeType;

public interface PointOperationRepository extends JpaRepository<PointOperationEntity, String> {
    // An upsert also locks an existing key. Unlike find-then-insert, concurrent first requests are safe.
    @Modifying
    @Query(value = """
            INSERT INTO point_idempotency_operation
                (operation_id, user_id, amount, kind, operation_status, created_at, updated_at)
            VALUES (:id, :userId, :amount, :kind, 'NEW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE operation_id = operation_id
            """, nativeQuery = true)
    void ensureOperation(@Param("id") String id, @Param("userId") Long userId,
            @Param("amount") Long amount, @Param("kind") String kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PointOperationEntity o where o.operationId = :id")
    Optional<PointOperationEntity> findByIdForUpdate(@Param("id") String id);
}
