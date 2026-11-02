package com.example.sporty.features.facilities.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

import lombok.Builder;
import lombok.Getter;

// serviceId는 DB 내부 PK가 아니라 서울시 외부 식별자다(서비스별 DB 분리로 내부 PK는 서비스마다 달라질 수 있음).
@Getter
@Builder
public class FacilityServiceSummaryDto {

    private String serviceId;
    private String serviceName;
    private String locationName;
    private String region;
    private Boolean isFree;
    private LocalDateTime reservationDeadlineAt;

    public static FacilityServiceSummaryDto from(ServiceEntity service) {
        LocationEntity location = service.getLocation();

        return FacilityServiceSummaryDto.builder()
                .serviceId(service.getServiceId())
                .serviceName(service.getName())
                .locationName(location.getName())
                .region(location.getRegion())
                .isFree(service.getIsFree())
                .reservationDeadlineAt(service.getReservationDeadlineAt())
                .build();
    }
}
