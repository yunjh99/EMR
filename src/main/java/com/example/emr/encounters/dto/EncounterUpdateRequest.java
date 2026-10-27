package com.example.emr.encounters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EncounterUpdateRequest(
        // 주호소
        String chiefComplaint,
        // 진료 소견
        String clinicalNote,
        // 치료 계획
        String treatmentPlan,
        // 정정 사유는 실제 변경이 없는 요청에서도 필수
        @NotBlank(message = "정정 사유는 필수입니다.")
        @Size(max = 500, message = "정정 사유는 500자 이하여야 합니다.")
        String changeReason
    ){}
