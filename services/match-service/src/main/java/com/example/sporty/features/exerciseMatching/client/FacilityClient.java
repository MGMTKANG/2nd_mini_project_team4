package com.example.sporty.features.exerciseMatching.client;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 매치 서비스가 체육시설 서비스에 요청하는 시설 조회. */
public interface FacilityClient {

    // 서울시 svcId로 조회하며, 없는 ID는 결과에서 빠진다.
    List<FacilityServiceSummary> findServices(Collection<String> serviceIds);

    // 공개 API에서 받은 시설 숫자 PK로 조회한다.
    List<FacilityServiceSummary> findServicesByIds(Collection<Long> ids);

    // 매치 목록의 지역·무료 여부 검색 조건에 맞는 서울시 svcId. region은 부분 일치다.
    List<String> searchServiceIds(String region, Boolean isFree);

    default Optional<FacilityServiceSummary> findService(String serviceId) {
        return findServices(List.of(serviceId)).stream()
                .filter(service -> serviceId.equals(service.serviceId()))
                .findFirst();
    }

    default Optional<FacilityServiceSummary> findServiceById(Long id) {
        return findServicesByIds(List.of(id)).stream()
                .filter(service -> id.equals(service.id()))
                .findFirst();
    }

    record FacilityServiceSummary(
            Long id,
            String serviceId,
            String serviceName,
            String locationName,
            String region,
            Boolean isFree,
            LocalDateTime reservationDeadlineAt) {
    }
}
