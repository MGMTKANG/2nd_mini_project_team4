package com.example.sporty.features.exerciseMatching.client;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 매치 서비스가 체육시설 서비스에 요청하는 시설 조회. */
public interface FacilityClient {

    // 없는 ID는 결과에서 빠진다.
    List<FacilityServiceSummary> findServices(Collection<Long> serviceIds);

    // 매치 목록의 지역·무료 여부 검색 조건에 맞는 체육서비스 ID. region은 부분 일치다.
    List<Long> searchServiceIds(String region, Boolean isFree);

    default Optional<FacilityServiceSummary> findService(Long serviceId) {
        return findServices(List.of(serviceId)).stream()
                .filter(service -> serviceId.equals(service.serviceId()))
                .findFirst();
    }

    record FacilityServiceSummary(
            Long serviceId,
            String serviceName,
            String locationName,
            String region,
            Boolean isFree,
            LocalDateTime reservationDeadlineAt) {
    }
}
