package com.example.sporty.features.exerciseMatching;

import com.example.sporty.features.exerciseMatching.client.UserClient;
import com.example.sporty.features.exerciseMatching.client.UserClient.ProfileSummary;
import com.example.sporty.features.exerciseMatching.client.UserClient.UserSummary;
import com.example.sporty.features.exerciseMatching.client.FacilityClient;
import com.example.sporty.support.FakeClientsConfig;
import com.example.sporty.support.FakeFacilityClient;
import com.example.sporty.support.FakeUserClient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchResponseDto;
import com.example.sporty.features.exerciseMatching.domain.dto.MatchSearchRequestDto;
import com.example.sporty.features.exerciseMatching.domain.entity.MatchEntity;
import com.example.sporty.features.exerciseMatching.domain.enums.GenderGroup;
import com.example.sporty.features.exerciseMatching.service.MatchService;
import com.example.sporty.support.FacilityFixtures;
import com.example.sporty.features.exerciseMatching.client.FacilityClient.FacilityServiceSummary;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.auto_quote_keyword=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({MatchService.class, FakeClientsConfig.class})
class MatchFacilityTest {
    @Autowired private EntityManager em;
    @Autowired private MatchService matchService;
    @Autowired private FakeFacilityClient fakeFacilityClient;

    @Test
    void searchFiltersPersistedBooleansAndLoadsFacilityRelationships() {
        long freeServiceId = FacilityFixtures.newServiceId();
        FacilityServiceSummary free = FacilityFixtures.create(fakeFacilityClient, freeServiceId, true);
        FacilityServiceSummary paid = FacilityFixtures.create(fakeFacilityClient, freeServiceId + 1, false);
        Long freeMatchId = persistMatch(free).getId();
        Long paidMatchId = persistMatch(paid).getId();
        em.flush();
        em.clear();

        for (boolean isFree : List.of(true, false)) {
            var result = matchService.searchMatches(MatchSearchRequestDto.builder()
                    .region("강남구").isFree(isFree).build());
            assertThat(result).extracting(MatchResponseDto::getMatchId)
                    .containsExactly(isFree ? freeMatchId : paidMatchId);
            assertThat(result.get(0).getIsFree()).isEqualTo(isFree);
            assertThat(result.get(0).getRegion()).isEqualTo("강남구");
            assertThat(result.get(0).getNumCurrentParticipant()).isZero();
        }
        assertThat(matchService.searchMatches(MatchSearchRequestDto.builder().build()))
                .extracting(MatchResponseDto::getMatchId)
                .containsExactlyInAnyOrder(freeMatchId, paidMatchId);
        assertThat(matchService.searchMatches(MatchSearchRequestDto.builder()
                .region("서초구").build())).isEmpty();

        var detail = matchService.getMatchDetail(freeMatchId, null);
        assertThat(detail.getServiceId()).isEqualTo(freeServiceId);
        assertThat(detail.getServiceName()).isEqualTo(free.serviceName());
        assertThat(detail.getLocationName()).isEqualTo("Test location");
        assertThat(detail.getRegion()).isEqualTo("강남구");
    }

    private MatchEntity persistMatch(FacilityServiceSummary service) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 10, 18, 0);
        MatchEntity match = MatchEntity.builder().title("Facility test")
                .serviceId(service.serviceId()).startAt(start).endAt(start.plusHours(2))
                .maxParticipant(10).sportType(SportType.FUTSAL)
                .genderGroup(GenderGroup.MIXED).build();
        em.persist(match);
        return match;
    }
}
