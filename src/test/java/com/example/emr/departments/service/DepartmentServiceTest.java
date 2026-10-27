package com.example.emr.departments.service;

import com.example.emr.audit_logs.service.AuditLogService;
import com.example.emr.departments.domain.Department;
import com.example.emr.departments.dto.DepartmentRequest;
import com.example.emr.departments.repository.DepartmentRepository;
import com.example.emr.global.error.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
    @Mock DepartmentRepository departments;
    @Mock AuditLogService audit;
    @InjectMocks DepartmentService service;

    @Test
    void duplicateCodeCannotBeCreated() {
        when(departments.existsByCode("IM")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new DepartmentRequest("IM", "내과")))
                .isInstanceOf(DuplicateDepartmentCodeException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void updateRejectsCodeOwnedByAnotherDepartment() {
        var department = new Department("IM", "내과");
        when(departments.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        when(departments.existsByCodeAndIdNot("OS", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, new DepartmentRequest("OS", "정형외과")))
                .isInstanceOf(DuplicateDepartmentCodeException.class);
        assertThat(department.getCode()).isEqualTo("IM");
    }

    @Test
    void defaultListUsesOnlyActiveDepartments() {
        when(departments.findByIsActiveTrueOrderByCodeAsc()).thenReturn(List.of(new Department("IM", "내과")));
        assertThat(service.list(false)).extracting("code").containsExactly("IM");
        verify(departments, never()).findAllByOrderByCodeAsc();
    }

    @Test
    void inactiveDetailIsHiddenByDefault() {
        var department = new Department("IM", "내과");
        department.deactivate();
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        assertThatThrownBy(() -> service.get(1L, false)).isInstanceOf(DepartmentNotFoundException.class);
        assertThat(service.get(1L, true).active()).isFalse();
    }

    @Test
    void repeatedDeactivationDoesNotDeleteOrAuditAgain() {
        var department = new Department("IM", "내과");
        department.deactivate();
        when(departments.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        service.deactivate(1L);
        verifyNoInteractions(audit);
        verify(departments, never()).delete(any());
    }
}
