package com.example.emr.visits.dto;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.departments.domain.Department;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.patients.domain.Patient;
import com.example.emr.visits.domain.Visit;
import com.example.emr.visits.domain.VisitType;
import jakarta.validation.constraints.NotNull;

public record VisitCreateRequest(
        @NotNull
        Patient patient,
        Appointment appointment,
        @NotNull
        Department department,
        @NotNull
        Doctor doctor,
        @NotNull
        VisitType visitType
) {
    public Visit toEntity(Patient patient){
        return new Visit(
        patient,
        appointment,
        department,
        doctor,
        visitType);
        }
}

