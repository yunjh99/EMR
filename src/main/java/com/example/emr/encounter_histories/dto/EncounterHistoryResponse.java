package com.example.emr.encounter_histories.dto;

import com.example.emr.encounter_histories.domain.EncounterHistory;

import java.time.LocalDateTime;

public record EncounterHistoryResponse(Long id, Long encounterId, long version,
                                       String chiefComplaint, String clinicalNote, String treatmentPlan,
                                       Long changedBy, String changeReason, LocalDateTime changedAt) {
    public static EncounterHistoryResponse from(EncounterHistory history) {
        return new EncounterHistoryResponse(history.getId(), history.getEncounter().getId(), history.getVersion(),
                history.getChiefComplaint(), history.getClinicalNote(), history.getTreatmentPlan(),
                history.getChangedBy().getId(), history.getChangeReason(), history.getChangedAt());
    }
}
