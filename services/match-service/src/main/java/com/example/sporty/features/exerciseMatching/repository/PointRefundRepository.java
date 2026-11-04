package com.example.sporty.features.exerciseMatching.repository;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.example.sporty.features.exerciseMatching.domain.entity.PointRefundEntity;

public interface PointRefundRepository extends JpaRepository<PointRefundEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PointRefundEntity r where r.operationId = :id")
    Optional<PointRefundEntity> findForUpdate(@Param("id") String id);
    List<PointRefundEntity> findTop50ByStatusAndOperationIdGreaterThanOrderByOperationIdAsc(String status, String cursor);
}
