package com.example.emr.doctors.service;

import com.example.emr.audit_logs.annotation.Audit;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository doctors;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService audit;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "CREATE_DOCTOR", targetTable = "doctors", targetId = "#result", logSuccess = false)
    public Long create(DoctorCreateRequest request) {
        var department = activeDepartment(request.departmentId());
        if (doctors.existsByLicenseNo(request.licenseNo())) throw new DuplicateDoctorException();
        User user;
        if (request.userId() != null) {
            user = users.findByIdForDoctorAssignment(request.userId()).orElseThrow(UserNotFoundException::new);
            if (!user.isActive() || user.getRole() != Role.DOCTOR) throw new InvalidDoctorAssignmentException();
            if (doctors.existsByUserId(user.getId())) throw new DuplicateDoctorException();
        } else {
            var account = request.account();
            if (users.existsByUsername(account.username())) throw new DuplicateUserNameException();
            user = users.saveAndFlush(new User(account.username(), passwordEncoder.encode(account.password()),
                    account.name(), Role.DOCTOR));
            // 계정도 같은 트랜잭션에서 생성하여 의사 등록 실패 시 함께 롤백한다.
            audit.writeEntry("CREATE", "users", user.getId());
        }
        var doctor = doctors.saveAndFlush(new Doctor(user, department, request.licenseNo(), request.specialty()));
        audit.writeEntry("CREATE_DOCTOR", "doctors", doctor.getId());
        return doctor.getId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','STAFF') and (!#includeInactive or hasRole('ADMIN'))")
    public List<DoctorResponse> list(Long departmentId, boolean includeInactive) {
        return doctors.search(departmentId, includeInactive).stream().map(DoctorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','STAFF') and (!#includeInactive or hasRole('ADMIN'))")
    public DoctorResponse get(Long id, boolean includeInactive) {
        var doctor = doctors.findById(id).orElseThrow(DoctorNotFoundException::new);
        if (!includeInactive && (!doctor.isActive() || !doctor.getDepartment().isActive()
                || !doctor.getUser().isActive() || doctor.getUser().getRole() != Role.DOCTOR)) {
            throw new DoctorNotFoundException();
        }
        return DoctorResponse.from(doctor);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "UPDATE_DOCTOR", targetTable = "doctors", targetId = "#id", logSuccess = false)
    public void update(Long id, DoctorUpdateRequest request) {
        var doctor = doctors.findByIdForUpdate(id).orElseThrow(DoctorNotFoundException::new);
        var department = activeDepartment(request.departmentId());
        if (doctors.existsByLicenseNoAndIdNot(request.licenseNo(), id)) throw new DuplicateDoctorException();
        doctor.update(department, request.licenseNo(), request.specialty());
        doctors.flush();
        audit.writeEntry("UPDATE_DOCTOR", "doctors", id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "DEACTIVATE_DOCTOR", targetTable = "doctors", targetId = "#id", logSuccess = false)
    public void deactivate(Long id) {
        var doctor = doctors.findByIdForUpdate(id).orElseThrow(DoctorNotFoundException::new);
        if (!doctor.isActive()) return;
        doctor.deactivate();
        doctors.flush();
        audit.writeEntry("DEACTIVATE_DOCTOR", "doctors", id);
    }

    private Department activeDepartment(Long id) {
        var department = departments.findByIdForUpdate(id).orElseThrow(DepartmentNotFoundException::new);
        if (!department.isActive()) throw new InvalidDoctorAssignmentException();
        return department;
    }
}
