package com.example.sporty.features.exerciseMatching.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventEntity;
import com.example.sporty.features.exerciseMatching.domain.entity.ProcessedEventId;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {
    // 할당된 복합 ID의 save/merge 대신 반드시 INSERT한다. 동시 수신은 PK로 직렬화된다.
    @Modifying
    @Query(value = """
            INSERT INTO processed_event (consumer_name, event_id, processed_at)
            VALUES (:consumerName, :eventId, :processedAt)
            """, nativeQuery = true)
    int insert(@Param("consumerName") String consumerName, @Param("eventId") String eventId,
            @Param("processedAt") LocalDateTime processedAt);
}
