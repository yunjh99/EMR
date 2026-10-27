package com.example.emr.patients.dto;

import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientResponse(Long id, String patientNo, String name, LocalDate birthDate, Gender gender,
                              String phone, String address, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getPatientNo(), patient.getName(), patient.getBirthDate(),
                patient.getGender(), patient.getPhone(), patient.getAddress(), patient.isActive(), patient.getCreatedAt(), patient.getUpdatedAt());
    }
}
