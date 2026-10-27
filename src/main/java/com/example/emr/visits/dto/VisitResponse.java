package com.example.emr.visits.dto;

import com.example.emr.visits.domain.*;
import java.time.LocalDateTime;

public record VisitResponse(Long id, Long patientId, String patientNo, String patientName,
                            Long doctorId, Long doctorUserId, String doctorName, Long departmentId, String departmentName,
                            Long appointmentId, VisitType visitType, VisitStatus status, Long encounterId,
                            LocalDateTime checkedInAt, LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static VisitResponse from(Visit visit, Long encounterId) {
        return new VisitResponse(visit.getId(), visit.getPatient().getId(), visit.getPatient().getPatientNo(), visit.getPatient().getName(),
                visit.getDoctor().getId(), visit.getDoctor().getUser().getId(), visit.getDoctor().getUser().getName(),
                visit.getDepartment().getId(), visit.getDepartment().getName(),
                visit.getAppointment() == null ? null : visit.getAppointment().getId(), visit.getVisitType(), visit.getStatus(), encounterId,
                visit.getCheckedInAt(), visit.getCompletedAt(), visit.getCreatedAt(), visit.getUpdatedAt());
    }
}
