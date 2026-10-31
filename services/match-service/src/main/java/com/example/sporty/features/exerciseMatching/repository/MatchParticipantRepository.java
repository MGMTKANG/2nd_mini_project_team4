package com.example.sporty.features.exerciseMatching.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sporty.features.exerciseMatching.domain.entity.MatchParticipantEntity;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity, Long> {

    // [EM-08] 내 매치 목록: 생성/참여 모두 조회하며 최근 시작 일정부터 표시한다.
    @EntityGraph(attributePaths = {"match"})
    List<MatchParticipantEntity> findAllByUserIdOrderByMatch_StartAtDescMatch_IdDesc(Long userId);

    // 회원탈퇴 정리: 잠금 전에 Match/참가 엔티티를 영속성 컨텍스트에 적재하지 않는다.
    @Query("select distinct p.match.id from MatchParticipantEntity p where p.userId = :userId order by p.match.id")
    List<Long> findMatchIdsByUserIdOrderByMatchIdAsc(@Param("userId") Long userId);

    List<MatchParticipantEntity> findAllByMatch_IdOrderByIdAsc(Long matchId);

    // OWNER도 참가자로 등록되므로 역할 구분 없이 전체 참가 정보를 센다.
    long countByMatch_Id(Long matchId);

    boolean existsByMatch_IdAndUserId(Long matchId, Long userId);

    // 참가 정보를 조회한 뒤 getRole()로 OWNER/PARTICIPANT를 확인한다.
    Optional<MatchParticipantEntity> findByMatch_IdAndUserId(Long matchId, Long userId);

    void deleteAllByMatch_Id(Long matchId);
}
