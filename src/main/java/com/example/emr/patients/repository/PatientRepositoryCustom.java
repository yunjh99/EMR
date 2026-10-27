package com.example.emr.patients.repository;

import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.dto.PatientSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PatientRepositoryCustom {
    Page<Patient> findAllPatientByCondition(PatientSearchCondition condition, Pageable pageable);
}
