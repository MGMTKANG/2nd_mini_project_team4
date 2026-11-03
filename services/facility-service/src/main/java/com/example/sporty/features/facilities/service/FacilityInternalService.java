package com.example.sporty.features.facilities.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.facilities.domain.dto.FacilityServiceSummaryDto;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;
import com.example.sporty.features.facilities.repository.ServiceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 운동매칭 서비스 등 다른 서비스가 호출하는 내부 조회 전용 서비스다.
 * 외부 공개 API(FacilityQueryService)와 응답 형식·검증 규칙이 다르므로 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityInternalService {

    private final ServiceRepository serviceRepository;

    // 없는 ID·비활성 시설은 결과에서 빠진다 (운동매칭 서비스 FacilityClient 계약).
    public List<FacilityServiceSummaryDto> findSummaries(Collection<String> serviceIds) {
        List<ServiceEntity> services =
                serviceRepository.findAllByServiceIdInAndActiveTrue(serviceIds);

        return services.stream()
                .map(FacilityServiceSummaryDto::from)
                .toList();
    }

    // 내부 숫자 PK로 조회한다 (없는 ID·비활성 시설은 결과에서 빠진다).
    public List<FacilityServiceSummaryDto> findSummariesByIds(Collection<Long> ids) {
        List<ServiceEntity> services =
                serviceRepository.findAllByIdInAndActiveTrue(ids);

        return services.stream()
                .map(FacilityServiceSummaryDto::from)
                .toList();
    }

    // region은 대소문자 무시 부분일치, isFree는 비우면 조건에서 제외한다.
    public List<String> searchServiceIds(String region, Boolean isFree) {
        return serviceRepository.findActiveServiceIdsByRegionAndIsFree(
                blankToNull(region), isFree
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
