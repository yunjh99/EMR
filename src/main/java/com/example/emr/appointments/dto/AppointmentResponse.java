package com.example.emr.appointments.dto;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.appointments.domain.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(Long id, Long doctorId, String doctorName,
                                  Long patientId, String patientName, LocalDateTime scheduledAt,
                                  AppointmentStatus status, String reason,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getDoctor().getId(),
                appointment.getDoctor().getUser().getName(), appointment.getPatient().getId(),
                appointment.getPatient().getName(), appointment.getScheduledAt(), appointment.getStatus(),
                appointment.getReason(), appointment.getCreatedAt(), appointment.getUpdatedAt());
    }
}
