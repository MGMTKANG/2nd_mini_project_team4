package com.example.sporty.features.facilities.domain.dto;

import java.time.LocalDateTime;

import com.example.sporty.features.facilities.domain.entity.LocationEntity;
import com.example.sporty.features.facilities.domain.entity.ServiceEntity;

import lombok.Builder;
import lombok.Getter;

// serviceId는 DB 내부 PK가 아니라 서울시 외부 식별자다(서비스별 DB 분리로 내부 PK는 서비스마다 달라질 수 있음).
// id는 facility-service DB의 내부 PK로, 다른 서비스가 매치-시설 연관을 저장할 때 이 값을 사용한다.
@Getter
@Builder
public class FacilityServiceSummaryDto {

    private Long id;
    private String serviceId;
    private String serviceName;
    private String locationName;
    private String region;
    private Boolean isFree;
    private LocalDateTime reservationDeadlineAt;

    public static FacilityServiceSummaryDto from(ServiceEntity service) {
        LocationEntity location = service.getLocation();

        return FacilityServiceSummaryDto.builder()
                .id(service.getId())
                .serviceId(service.getServiceId())
                .serviceName(service.getName())
                .locationName(location.getName())
                .region(location.getRegion())
                .isFree(service.getIsFree())
                .reservationDeadlineAt(service.getReservationDeadlineAt())
                .build();
    }
}
