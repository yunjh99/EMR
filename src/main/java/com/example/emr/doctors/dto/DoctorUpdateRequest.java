package com.example.emr.doctors.dto;

import jakarta.validation.constraints.*;

public record DoctorUpdateRequest(
        @NotNull @Positive Long departmentId,
        @NotBlank @Size(max = 30) @Pattern(regexp = "\\S+") String licenseNo,
        @Size(max = 100) String specialty
) {}
