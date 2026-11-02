package com.example.sporty.features.facilities.controller;

import java.util.Collection;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sporty.features.facilities.domain.dto.FacilityServiceSummaryDto;
import com.example.sporty.features.facilities.service.FacilityInternalService;

import lombok.RequiredArgsConstructor;

// 운동매칭 서비스(FacilityServiceFeignClient)가 호출하는 내부 API다.
@RestController
@RequiredArgsConstructor
public class FacilityInternalController {

    private final FacilityInternalService facilityInternalService;

    @GetMapping("/internal/facility-services")
    public ResponseEntity<List<FacilityServiceSummaryDto>> getServices(
            @RequestParam("ids") Collection<String> serviceIds
    ) {
        return ResponseEntity.ok(
                facilityInternalService.findSummaries(serviceIds)
        );
    }

    @GetMapping("/internal/facility-services/ids")
    public ResponseEntity<List<String>> searchServiceIds(
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "isFree", required = false) Boolean isFree
    ) {
        return ResponseEntity.ok(
                facilityInternalService.searchServiceIds(region, isFree)
        );
    }
}
