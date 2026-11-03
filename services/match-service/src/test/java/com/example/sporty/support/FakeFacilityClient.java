package com.example.sporty.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.example.sporty.features.exerciseMatching.client.FacilityClient;

/** 체육시설 서비스 대신 체육서비스 정보를 메모리에 보관한다. */
public class FakeFacilityClient implements FacilityClient {

    private final Map<String, FacilityServiceSummary> services = new ConcurrentHashMap<>();

    public void add(FacilityServiceSummary service) {
        services.put(service.serviceId(), service);
    }

    @Override
    public List<FacilityServiceSummary> findServices(Collection<String> serviceIds) {
        return serviceIds.stream().map(services::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<FacilityServiceSummary> findServicesByIds(Collection<Long> ids) {
        return services.values().stream().filter(service -> ids.contains(service.id())).toList();
    }

    // 기존 모놀리식 검색과 같이 지역은 대소문자 무시 부분 일치, 무료 여부는 정확히 일치시킨다.
    @Override
    public List<String> searchServiceIds(String region, Boolean isFree) {
        return services.values().stream()
                .filter(service -> region == null || (service.region() != null
                        && service.region().toLowerCase().contains(region.toLowerCase())))
                .filter(service -> isFree == null || isFree.equals(service.isFree()))
                .map(FacilityServiceSummary::serviceId)
                .toList();
    }
}
