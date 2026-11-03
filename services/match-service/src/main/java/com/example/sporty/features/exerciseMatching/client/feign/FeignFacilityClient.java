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
    public List<FacilityServiceSummary> findServices(Collection<String> serviceIds) {
        if (serviceIds.isEmpty()) {
            return List.of();
        }
        try {
            List<FacilityServiceSummary> services = facilityServiceFeignClient.getServices(serviceIds);
            return validateServices(services);
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("facility-service", e);
        }
    }

    @Override
    public List<FacilityServiceSummary> findServicesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        try {
            return validateServices(facilityServiceFeignClient.getServicesByIds(ids));
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("facility-service", e);
        }
    }

    private List<FacilityServiceSummary> validateServices(List<FacilityServiceSummary> services) {
        if (services == null || services.stream().anyMatch(service ->
                service == null || service.id() == null
                        || service.serviceId() == null || service.serviceId().isBlank())) {
            throw new ExternalServiceUnavailableException("facility-service");
        }
        return services;
    }

    @Override
    public List<String> searchServiceIds(String region, Boolean isFree) {
        try {
            List<String> serviceIds = facilityServiceFeignClient.searchServiceIds(region, isFree);
            if (serviceIds == null || serviceIds.stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new ExternalServiceUnavailableException("facility-service");
            }
            return serviceIds;
        } catch (FeignException e) {
            throw new ExternalServiceUnavailableException("facility-service", e);
        }
    }
}
