package com.example.emr.patient_histories.service;

import com.example.emr.audit_logs.service.AuditLogService;
import com.example.emr.patient_histories.domain.PatientHistory;
import com.example.emr.patient_histories.dto.PatientSnapshot;
import com.example.emr.patient_histories.repository.PatientHistoryRepository;
import com.example.emr.patients.domain.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PatientHistoryService {

    private final PatientHistoryRepository repository;
    private final AuditLogService audit;

    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasRole('STAFF')")
    public void recordCreation(Patient patient) {
        append(patient, "CREATE_PATIENT", null, PatientSnapshot.of(patient));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasRole('STAFF')")
    public void recordChange(Patient patient, String action, PatientSnapshot before) {
        var after = PatientSnapshot.of(patient);
        if (before.equals(after)) return;
        append(patient, action, before, after);
    }

    private void append(Patient patient, String action, PatientSnapshot before, PatientSnapshot after) {
        var changed = after.fields().keySet().stream()
                .filter(field -> before == null
                        || !Objects.equals(before.fields().get(field), after.fields().get(field)))
                .toList();
        if (changed.isEmpty()) return;
        var entry = audit.writeEntry(action, "patients", patient.getId());
        var histories = changed.stream()
                .map(field -> new PatientHistory(patient.getId(), entry.getId(), field,
                        before == null ? null : before.fields().get(field),
                        after.fields().get(field)))
                .toList();
        repository.saveAll(histories);
        // 환자 수정도 함께 flush된다. DB 오류를 전파하여 전체 트랜잭션을 롤백한다.
        repository.flush();
    }

}
