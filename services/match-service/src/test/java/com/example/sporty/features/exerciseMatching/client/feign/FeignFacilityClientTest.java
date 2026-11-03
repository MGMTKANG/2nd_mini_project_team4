package com.example.sporty.features.exerciseMatching.client.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sporty.features.commons.exception.exerciseMatching.ExternalServiceUnavailableException;
import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;

import feign.FeignException;

@ExtendWith(MockitoExtension.class)
class FeignFacilityClientTest {
    @Mock private FacilityServiceFeignClient remote;
    @InjectMocks private FeignFacilityClient client;

    @Test
    void emptyLookupsSkipRemoteCalls() {
        assertThat(client.findServices(List.of())).isEmpty();
        assertThat(client.findServicesByIds(List.of())).isEmpty();
        verifyNoInteractions(remote);
    }

    @Test
    void singleLookupsMatchTheirOwnIdentifier() {
        var other = new FacilityServiceSummary(2L, "1", null, null, null, null, null);
        var requested = new FacilityServiceSummary(1L, "S1", null, null, null, null, null);
        when(remote.getServices(List.of("S1"))).thenReturn(List.of(other, requested));
        when(remote.getServicesByIds(List.of(1L))).thenReturn(List.of(other, requested));

        assertThat(client.findService("S1")).contains(requested);
        assertThat(client.findServiceById(1L)).contains(requested);
        when(remote.getServices(List.of("S1"))).thenReturn(List.of(other));
        when(remote.getServicesByIds(List.of(1L))).thenReturn(List.of(other));
        assertThat(client.findService("S1")).isEmpty();
        assertThat(client.findServiceById(1L)).isEmpty();
    }

    @Test
    void bothSummaryLookupsRejectMissingOrBlankRequiredFields() {
        for (List<FacilityServiceSummary> response : Arrays.<List<FacilityServiceSummary>>asList(
                null, Collections.singletonList(null),
                List.of(new FacilityServiceSummary(null, "S1", null, null, null, null, null)),
                List.of(new FacilityServiceSummary(1L, null, null, null, null, null, null)),
                List.of(new FacilityServiceSummary(1L, "", null, null, null, null, null)),
                List.of(new FacilityServiceSummary(1L, "   ", null, null, null, null, null)))) {
            when(remote.getServices(List.of("S1"))).thenReturn(response);
            when(remote.getServicesByIds(List.of(1L))).thenReturn(response);

            assertThatThrownBy(() -> client.findServices(List.of("S1")))
                    .isInstanceOf(ExternalServiceUnavailableException.class);
            assertThatThrownBy(() -> client.findServicesByIds(List.of(1L)))
                    .isInstanceOf(ExternalServiceUnavailableException.class);
        }
    }

    @Test
    void searchReturnsSvcIdsAndAllowsEmptyResults() {
        when(remote.searchServiceIds("강남", true)).thenReturn(List.of("S1", "S2")).thenReturn(List.of());

        assertThat(client.searchServiceIds("강남", true)).containsExactly("S1", "S2");
        assertThat(client.searchServiceIds("강남", true)).isEmpty();
    }

    @Test
    void searchRejectsNullOrBlankIds() {
        for (List<String> response : Arrays.<List<String>>asList(null, Collections.singletonList(null),
                List.of(""), List.of("   "), Arrays.asList("S1", null))) {
            when(remote.searchServiceIds(null, null)).thenReturn(response);

            assertThatThrownBy(() -> client.searchServiceIds(null, null))
                    .isInstanceOf(ExternalServiceUnavailableException.class);
        }
    }

    @Test
    void allThreeRemoteFailuresBecomeUnavailableWithTheirCause() {
        FeignException failure = mock(FeignException.class);
        when(remote.getServices(List.of("S1"))).thenThrow(failure);
        when(remote.getServicesByIds(List.of(1L))).thenThrow(failure);
        when(remote.searchServiceIds(null, null)).thenThrow(failure);

        assertThatThrownBy(() -> client.findServices(List.of("S1")))
                .isInstanceOf(ExternalServiceUnavailableException.class).hasCause(failure);
        assertThatThrownBy(() -> client.findServicesByIds(List.of(1L)))
                .isInstanceOf(ExternalServiceUnavailableException.class).hasCause(failure);
        assertThatThrownBy(() -> client.searchServiceIds(null, null))
                .isInstanceOf(ExternalServiceUnavailableException.class).hasCause(failure);
    }
}
