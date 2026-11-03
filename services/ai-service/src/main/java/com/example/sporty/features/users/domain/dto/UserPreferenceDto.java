package com.example.sporty.features.users.domain.dto;

import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.users.domain.enums.Gender;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// user-service GET /internal/users/{userId}/preferences 응답. 받는 쪽이라 칸 이름만 맞춘다.
@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserPreferenceDto {

    private Long userId;
    private Gender gender;
    private String district;
    private List<SportType> preferenceSports;
}