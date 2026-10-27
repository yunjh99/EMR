package com.example.emr.doctors.service;

import com.example.emr.audit_logs.service.AuditLogService;
import com.example.emr.departments.domain.Department;
import com.example.emr.departments.repository.DepartmentRepository;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.doctors.dto.*;
import com.example.emr.doctors.repository.DoctorRepository;
import com.example.emr.global.error.exception.*;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {
    @Mock DoctorRepository doctors;
    @Mock DepartmentRepository departments;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditLogService audit;
    @InjectMocks DoctorService service;

    private DoctorCreateRequest request() { return new DoctorCreateRequest(2L, null, 1L, "LIC-1", null); }

    @Test
    void nonexistentDepartmentIsRejectedBeforeAccountWork() {
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(DepartmentNotFoundException.class);
        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void inactiveDepartmentIsRejectedBeforeAccountWork() {
        var department = new Department("IM", "내과");
        department.deactivate();
        when(departments.findByIdForUpdate(1L)).thenReturn(Optional.of(department));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(InvalidDoctorAssignmentException.class);
        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void duplicateLicenseIsRejected() {
        when(departments.findByIdForUpdate(1L)).thenReturn(Optional.of(new Department("IM", "내과")));
        when(doctors.existsByLicenseNo("LIC-1")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(DuplicateDoctorException.class);
        verifyNoInteractions(users, passwordEncoder, audit);
    }

    @Test
    void nonDoctorAccountIsNotSilentlyPromoted() {
        when(departments.findByIdForUpdate(1L)).thenReturn(Optional.of(new Department("IM", "내과")));
        var user = new User("staff", "hash", "직원", Role.STAFF);
        when(users.findByIdForDoctorAssignment(2L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(InvalidDoctorAssignmentException.class);
        assertThat(user.getRole()).isEqualTo(Role.STAFF);
        verify(doctors, never()).saveAndFlush(any());
    }

    @Test
    void inactiveAccountIsRejected() {
        when(departments.findByIdForUpdate(1L)).thenReturn(Optional.of(new Department("IM", "내과")));
        var user = new User("doctor", "hash", "의사", Role.DOCTOR);
        user.update(null, null, false);
        when(users.findByIdForDoctorAssignment(2L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(InvalidDoctorAssignmentException.class);
    }

    @Test
    void movingToInactiveDepartmentKeepsOriginalAssignment() {
        var original = new Department("IM", "내과");
        var inactive = new Department("OS", "정형외과");
        inactive.deactivate();
        var doctor = new Doctor(new User("doctor", "hash", "의사", Role.DOCTOR), original, "LIC-1", null);
        when(doctors.findByIdForUpdate(3L)).thenReturn(Optional.of(doctor));
        when(departments.findByIdForUpdate(4L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.update(3L, new DoctorUpdateRequest(4L, "LIC-1", null)))
                .isInstanceOf(InvalidDoctorAssignmentException.class);
        assertThat(doctor.getDepartment()).isSameAs(original);
    }

    @Test
    void doctorDeactivationKeepsUserAndDepartment() {
        var user = new User("doctor", "hash", "의사", Role.DOCTOR);
        var department = new Department("IM", "내과");
        var doctor = new Doctor(user, department, "LIC-1", null);
        when(doctors.findByIdForUpdate(3L)).thenReturn(Optional.of(doctor));
        service.deactivate(3L);
        assertThat(doctor.isActive()).isFalse();
        assertThat(user.isActive()).isTrue();
        assertThat(doctor.getDepartment()).isSameAs(department);
        verify(audit).writeEntry("DEACTIVATE_DOCTOR", "doctors", 3L);
        verify(doctors, never()).delete(any());
    }
}
