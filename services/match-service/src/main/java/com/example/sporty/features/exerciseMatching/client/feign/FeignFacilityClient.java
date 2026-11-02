package com.example.sporty.features.exerciseMatching.client.feign;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.exerciseMatching.client.FacilityClient;

import feign.FeignException;
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
        try {
            List<FacilityServiceSummary> services = facilityServiceFeignClient.getServices(serviceIds);
            if (services == null || services.stream().anyMatch(service ->
                    service == null || service.serviceId() == null)) {
                throw new ExternalServiceUnavailableException("facility-service");
            }
            return services;
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("facility-service", e);
        }
    }

    @Override
    public List<Long> searchServiceIds(String region, Boolean isFree) {
        try {
            List<Long> serviceIds = facilityServiceFeignClient.searchServiceIds(region, isFree);
            if (serviceIds == null || serviceIds.stream().anyMatch(java.util.Objects::isNull)) {
                throw new ExternalServiceUnavailableException("facility-service");
            }
            return serviceIds;
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("facility-service", e);
        }
    }
}
