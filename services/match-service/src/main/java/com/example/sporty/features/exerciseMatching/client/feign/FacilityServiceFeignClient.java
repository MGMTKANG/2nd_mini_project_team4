package com.example.sporty.features.exerciseMatching.client.feign;

import java.util.Collection;
import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;

// 체육시설 서비스 내부 API (facility-service FacilityInternalController).
@FeignClient(name = "facility-service", url = "${clients.facility-service.url:}")
public interface FacilityServiceFeignClient {

    @GetMapping("/internal/facility-services")
    List<FacilityServiceSummary> getServices(@RequestParam("ids") Collection<String> serviceIds);

    @GetMapping("/internal/facility-services/by-id")
    List<FacilityServiceSummary> getServicesByIds(@RequestParam("ids") Collection<Long> ids);

    @GetMapping("/internal/facility-services/ids")
    List<String> searchServiceIds(
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "isFree", required = false) Boolean isFree);
}
