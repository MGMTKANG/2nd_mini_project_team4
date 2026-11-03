package com.example.sporty.features.facilities.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

public interface ServiceRepository
        extends JpaRepository<ServiceEntity, Long>,
                JpaSpecificationExecutor<ServiceEntity> {

    Optional<ServiceEntity> findByServiceId(String serviceId);

    @EntityGraph(attributePaths = "location")
    Optional<ServiceEntity> findByIdAndActiveTrue(Long id);

    List<ServiceEntity> findAllByActiveTrue();

    @EntityGraph(attributePaths = "location")
    List<ServiceEntity> findAll(
            Specification<ServiceEntity> specification
    );

    @EntityGraph(attributePaths = "location")
    List<ServiceEntity> findAllByServiceIdInAndActiveTrue(Collection<String> serviceIds);

    // 지역은 대소문자 무시 부분일치, isFree는 null이면 조건에서 제외한다.
    @Query("""
            select s.serviceId from ServiceEntity s
            join s.location l
            where s.active = true
            and (:region is null or lower(l.region) like lower(concat('%', :region, '%')))
            and (:isFree is null or s.isFree = :isFree)
            """)
    List<String> findActiveServiceIdsByRegionAndIsFree(
            @Param("region") String region,
            @Param("isFree") Boolean isFree
    );
}
