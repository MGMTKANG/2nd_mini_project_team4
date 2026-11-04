package com.example.sporty.features.exerciseMatching.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.example.sporty.features.exerciseMatching.domain.entity.PointDebitEntity;

public interface PointDebitRepository extends JpaRepository<PointDebitEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PointDebitEntity d where d.operationId = :id")
    Optional<PointDebitEntity> findForUpdate(@Param("id") String id);
    List<PointDebitEntity> findTop50ByStatusAndCreatedAtBeforeAndOperationIdGreaterThanOrderByOperationIdAsc(String status, Instant before, String cursor);
}
