package com.example.emr.departments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "\\S+") String code,
        @NotBlank @Size(max = 100) String name
) {}
