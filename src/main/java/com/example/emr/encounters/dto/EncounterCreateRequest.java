package com.example.emr.encounters.dto;

import com.example.emr.encounters.domain.Encounter;
import com.example.emr.visits.domain.Visit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EncounterCreateRequest(
        @NotNull(message = "접수 ID는 필수입니다.")
        Long visitId,

        @NotBlank(message = "주호소는 필수입니다.")
        @Size(max = 2000)
        String chiefComplaint,

        @NotBlank(message = "진료 소견은 필수입니다.")
        @Size(max = 5000)
        String clinicalNote,

        @NotBlank(message = "치료 계획은 필수입니다.")
        @Size(max = 5000)
        String treatmentPlan
){
    public Encounter toEntity(Visit visit){
        return new Encounter(
                visit,
                chiefComplaint,
                clinicalNote,
                treatmentPlan
        );
    }
}
