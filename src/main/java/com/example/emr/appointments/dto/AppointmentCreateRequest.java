package com.example.emr.appointments.dto;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.patients.domain.Patient;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AppointmentCreateRequest (
        @NotNull
        Doctor doctor,
        @NotNull
        Patient patient,
        @NotNull
        LocalDateTime scheduledAt,
        String reson
){
    public Appointment toEntity(){
        return new Appointment(
                doctor,
                patient,
                scheduledAt,
                reson);
    }
}
