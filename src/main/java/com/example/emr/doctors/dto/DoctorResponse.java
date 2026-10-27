package com.example.emr.doctors.dto;

import com.example.emr.doctors.domain.Doctor;

public record DoctorResponse(Long id, Long userId, String name, Long departmentId,
                             String departmentName, String licenseNo, String specialty, boolean active) {
    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getUser().getId(), doctor.getUser().getName(),
                doctor.getDepartment().getId(), doctor.getDepartment().getName(), doctor.getLicenseNo(),
                doctor.getSpecialty(), doctor.isActive());
    }
}
