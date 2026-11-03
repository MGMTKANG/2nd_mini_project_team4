package com.example.sporty.support;

import java.util.concurrent.ThreadLocalRandom;

import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;

public final class FacilityFixtures {
    private FacilityFixtures() {}

    public static long newServiceId() {
        // Integer 범위를 넘는 ID도 함께 검증한다.
        return ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
    }

    public static FacilityServiceSummary create(FakeFacilityClient facilityClient, long serviceId) {
        return create(facilityClient, serviceId, true);
    }

    public static FacilityServiceSummary create(FakeFacilityClient facilityClient, long serviceId, Boolean isFree) {
        FacilityServiceSummary service = service(serviceId, isFree);
        facilityClient.add(service);
        return service;
    }

    public static FacilityServiceSummary service(long id, Boolean isFree) {
        return new FacilityServiceSummary(id, "S" + id, "Test service " + id, "Test location", "강남구", isFree, null);
    }
}
