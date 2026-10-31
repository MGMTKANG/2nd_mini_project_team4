package com.example.sporty.features.exerciseMatching.client.feign;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.sporty.features.exerciseMatching.client.FacilityClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeignFacilityClient implements FacilityClient {

    private final FacilityServiceFeignClient facilityServiceFeignClient;

    @Override
    public List<FacilityServiceSummary> findServices(Collection<Long> serviceIds) {
        if (serviceIds.isEmpty()) {
            return List.of();
        }
        return facilityServiceFeignClient.getServices(serviceIds);
    }

    @Override
    public List<Long> searchServiceIds(String region, Boolean isFree) {
        return facilityServiceFeignClient.searchServiceIds(region, isFree);
    }
}
