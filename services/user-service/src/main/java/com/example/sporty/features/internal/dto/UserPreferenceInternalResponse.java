package com.example.sporty.features.internal.dto;

import java.util.List;

import com.example.sporty.features.commons.util.SportType;
import com.example.sporty.features.users.domain.entity.Gender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "AI 추천용 회원 선호 정보 응답 DTO")
public class UserPreferenceInternalResponse {

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "성별 (MALE, FEMALE)", example = "FEMALE")
    private Gender gender;

    @Schema(description = "활동 자치구 한글 명칭", example = "강남구")
    private String district;

    @Schema(description = "선호 운동 종목 목록", example = "[\"FUTSAL\", \"TENNIS\"]")
    private List<SportType> preferenceSports;
}
