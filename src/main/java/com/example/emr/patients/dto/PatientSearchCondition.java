package com.example.emr.patients.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;


public record PatientSearchCondition(
        String name,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
        String phone,
        String address,
        String patientNo,
        Boolean active
) {
    public PatientSearchCondition(String name, LocalDate birthDate, String phone, String address) {
        this(name, birthDate, phone, address, null, null);
    }
}
