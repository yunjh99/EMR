package com.example.emr.patient_histories.repository;

import com.example.emr.patient_histories.domain.PatientHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientHistoryRepository extends JpaRepository<PatientHistory, Long> {
}
