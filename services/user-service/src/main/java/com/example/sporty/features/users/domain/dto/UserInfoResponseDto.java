package com.example.sporty.features.users.domain.dto;

import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.entity.District;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.users.domain.entity.Gender;
import com.example.sporty.features.users.domain.entity.UserEntity;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class UserInfoResponseDto {
    private String email;
    private Gender gender;
    private String nickname;
    private District district;
    private String imageUrl;
    private List<SportType> preferenceSports;

    public static UserInfoResponseDto fromEntity(UserEntity userEntity, ProfileEntity profileEntity, List<SportType> preferenceSports) {
        return UserInfoResponseDto.builder()
                .email(userEntity.getEmail())
                .gender(userEntity.getGender())
                .nickname(profileEntity != null ? profileEntity.getNickname() : null)
                .district(profileEntity != null ? profileEntity.getDistrict() : null)
                .imageUrl(profileEntity != null ? profileEntity.getImageUrl() : null)
                .preferenceSports(preferenceSports)
                .build();
    }
}
