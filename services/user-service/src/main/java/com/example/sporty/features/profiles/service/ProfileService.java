package com.example.sporty.features.profiles.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sporty.features.commons.exception.profiles.ProfileNotFoundException;
import com.example.sporty.features.commons.exception.users.DuplicateNicknameException;
import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.profiles.domain.dto.ProfileDetailResponseDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateRequestDto;
import com.example.sporty.features.profiles.domain.dto.ProfileUpdateResponseDto;
import com.example.sporty.features.profiles.domain.entity.ProfileEntity;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.sportpreference.domain.entity.SportPreferenceEntity;
import com.example.sporty.features.sportpreference.repository.SportPreferenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final SportPreferenceRepository sportPreferenceRepository;

    @Transactional(readOnly = true)
    public ProfileDetailResponseDto getProfile(Long userId) {
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException("프로필이 존재하지 않습니다."));

        List<SportType> sports = sportPreferenceRepository.findByProfileId(profile.getId()).stream()
                .map(SportPreferenceEntity::getSportType)
                .toList();

        return ProfileDetailResponseDto.fromEntity(profile, sports);
    }

    @Transactional(readOnly = true)
    public ProfileDetailResponseDto getProfileById(Long profileId) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException("프로필이 존재하지 않습니다."));

        List<SportType> sports = sportPreferenceRepository.findByProfileId(profile.getId()).stream()
                .map(SportPreferenceEntity::getSportType)
                .toList();

        return ProfileDetailResponseDto.fromEntity(profile, sports);
    }

    @Transactional
    public ProfileUpdateResponseDto updateProfile(Long userId, ProfileUpdateRequestDto request) {
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException("프로필이 존재하지 않습니다."));

        if (profileRepository.existsByNicknameAndUserIdNot(request.getNickname(), userId)) {
            throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
        }

        profile.update(request.getNickname(), request.getDistrict(), profile.getImageUrl());

        sportPreferenceRepository.deleteByProfileId(profile.getId());

        List<SportType> distinctSportTypes = (request.getSportTypes() != null)
                ? request.getSportTypes().stream().distinct().toList()
                : Collections.emptyList();

        if (!distinctSportTypes.isEmpty()) {
            List<SportPreferenceEntity> newSports = distinctSportTypes.stream()
                    .map(sport -> SportPreferenceEntity.builder()
                            .profileId(profile.getId())
                            .sportType(sport)
                            .build())
                    .toList();
            sportPreferenceRepository.saveAll(newSports);
        }

        return ProfileUpdateResponseDto.fromEntity(profile, distinctSportTypes);
    }
}
