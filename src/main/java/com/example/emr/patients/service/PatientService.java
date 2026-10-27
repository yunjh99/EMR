package com.example.emr.patients.service;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.global.error.exception.PatientNotFoundException;
import com.example.emr.patient_histories.dto.PatientSnapshot;
import com.example.emr.patient_histories.service.PatientHistoryService;
import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.dto.PatientCreateRequest;
import com.example.emr.patients.dto.PatientSearchCondition;
import com.example.emr.patients.dto.PatientUpdateRequest;
import com.example.emr.patients.dto.PatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.emr.patients.repository.PatientRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientNumberGenerator patientNumberGenerator;
    private final PatientHistoryService patientHistoryService;

    //환자 생성
    @Audit(action = "CREATE_PATIENT", targetTable = "patients", targetId = "#result", logSuccess = false)
    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    public Long createPatient(PatientCreateRequest request) {
        String patientNo = patientNumberGenerator.next();
        Patient patient = request.toEntity(patientNo);
        patientRepository.save(patient);
        patientHistoryService.recordCreation(patient);
        return patient.getId();
    }

    //환자 수정
    @Audit(action = "UPDATE_PATIENT", targetTable = "patients", targetId = "#patientId", logSuccess = false)
    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    public void updatePatient(Long patientId, PatientUpdateRequest request) {
        Patient patient = patientRepository.findByIdForUpdate(patientId)
                .orElseThrow(PatientNotFoundException::new);
        var before = PatientSnapshot.of(patient);
        patient.update(request.getName(), request.getBirthDate(), request.getGender(),
                request.getPhone(), request.getAddress());
        patientHistoryService.recordChange(patient, "UPDATE_PATIENT", before);
    }

    //단건 조회
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','NURSE')")
    public PatientResponse getPatient(Long id) {
        return PatientResponse.from(patientRepository.findById(id).orElseThrow(PatientNotFoundException::new));
    }

    //전체 목록 조회
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','NURSE')")
    public Page<PatientResponse> getPatientsByCondition(PatientSearchCondition condition, Pageable pageable) {
        return patientRepository.findAllPatientByCondition(condition, pageable)
                .map(PatientResponse::from);
    }
}
