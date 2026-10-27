package com.example.emr.patient_histories.dto;

import com.example.emr.patients.domain.Patient;

import java.util.LinkedHashMap;
import java.util.Map;

public record PatientSnapshot(Map<String, String> fields) {
    public PatientSnapshot {
        fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static PatientSnapshot of(Patient patient) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("patient_no", patient.getPatientNo());
        fields.put("name", patient.getName());
        fields.put("phone", patient.getPhone());
        fields.put("address", patient.getAddress());
        fields.put("gender", patient.getGender().name());
        fields.put("birth_date", patient.getBirthDate().toString());
        fields.put("is_active", Boolean.toString(patient.isActive()));
        return new PatientSnapshot(fields);
    }
}
