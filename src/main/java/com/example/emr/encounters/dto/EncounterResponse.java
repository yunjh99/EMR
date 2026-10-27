package com.example.emr.encounters.dto;

import com.example.emr.encounters.domain.Encounter;
import com.example.emr.visits.domain.VisitStatus;

import java.time.LocalDateTime;

public record EncounterResponse(
        Long id,
        Long visitId,
        Long patientId,
        String patientNo,
        String patientName,
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName,
        VisitStatus visitStatus,
        String chiefComplaint,
        String clinicalNote,
        String treatmentPlan,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EncounterResponse from(Encounter encounter) {
        var visit = encounter.getVisit();
        return new EncounterResponse(
                encounter.getId(), visit.getId(), visit.getPatient().getId(),
                visit.getPatient().getPatientNo(), visit.getPatient().getName(),
                visit.getDoctor().getId(), visit.getDoctor().getUser().getName(),
                visit.getDepartment().getId(), visit.getDepartment().getName(), visit.getStatus(),
                encounter.getChiefComplaint(), encounter.getClinicalNote(), encounter.getTreatmentPlan(),
                encounter.getVersion(),
                encounter.getCreatedAt(), encounter.getUpdatedAt());
    }
}
