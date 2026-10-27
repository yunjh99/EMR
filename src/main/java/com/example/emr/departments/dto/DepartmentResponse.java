package com.example.emr.departments.dto;

import com.example.emr.departments.domain.Department;

public record DepartmentResponse(Long id, String code, String name, boolean active) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getCode(), department.getName(), department.isActive());
    }
}
