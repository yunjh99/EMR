package com.example.emr.departments.service;

import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.audit_logs.service.AuditLogService;
import com.example.emr.departments.domain.Department;
import com.example.emr.departments.dto.*;
import com.example.emr.departments.repository.DepartmentRepository;
import com.example.emr.global.error.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departments;
    private final AuditLogService audit;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "CREATE_DEPARTMENT", targetTable = "departments", targetId = "#result", logSuccess = false)
    public Long create(DepartmentRequest request) {
        if (departments.existsByCode(request.code())) throw new DuplicateDepartmentCodeException();
        var department = departments.saveAndFlush(new Department(request.code(), request.name()));
        audit.writeEntry("CREATE_DEPARTMENT", "departments", department.getId());
        return department.getId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','STAFF') and (!#includeInactive or hasRole('ADMIN'))")
    public List<DepartmentResponse> list(boolean includeInactive) {
        var result = includeInactive ? departments.findAllByOrderByCodeAsc() : departments.findByIsActiveTrueOrderByCodeAsc();
        return result.stream().map(DepartmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','STAFF') and (!#includeInactive or hasRole('ADMIN'))")
    public DepartmentResponse get(Long id, boolean includeInactive) {
        var department = departments.findById(id).orElseThrow(DepartmentNotFoundException::new);
        if (!includeInactive && !department.isActive()) throw new DepartmentNotFoundException();
        return DepartmentResponse.from(department);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "UPDATE_DEPARTMENT", targetTable = "departments", targetId = "#id", logSuccess = false)
    public void update(Long id, DepartmentRequest request) {
        var department = departments.findByIdForUpdate(id).orElseThrow(DepartmentNotFoundException::new);
        if (departments.existsByCodeAndIdNot(request.code(), id)) throw new DuplicateDepartmentCodeException();
        department.update(request.code(), request.name());
        departments.flush();
        audit.writeEntry("UPDATE_DEPARTMENT", "departments", id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "DEACTIVATE_DEPARTMENT", targetTable = "departments", targetId = "#id", logSuccess = false)
    public void deactivate(Long id) {
        var department = departments.findByIdForUpdate(id).orElseThrow(DepartmentNotFoundException::new);
        if (!department.isActive()) return;
        department.deactivate();
        departments.flush();
        audit.writeEntry("DEACTIVATE_DEPARTMENT", "departments", id);
    }
}
