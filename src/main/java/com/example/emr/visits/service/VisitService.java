package com.example.emr.visits.service;

import com.example.emr.appointments.service.AppointmentService;
import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.global.error.exception.DuplicateVisitException;
import com.example.emr.global.error.exception.DoctorAlreadyInProgressException;
import com.example.emr.global.error.exception.PatientNotFoundException;
import com.example.emr.global.error.exception.VisitNotFoundException;
import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.repository.PatientRepository;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.visits.domain.Visit;
import com.example.emr.visits.domain.VisitStatus;
import com.example.emr.visits.dto.VisitCreateRequest;
import com.example.emr.visits.dto.VisitRegistrationRequest;
import com.example.emr.visits.dto.VisitResponse;
import com.example.emr.global.error.exception.InvalidVisitRegistrationException;
import com.example.emr.global.error.exception.DoctorNotFoundException;
import com.example.emr.global.error.exception.AppointmentNotFoundException;
import com.example.emr.doctors.repository.DoctorRepository;
import com.example.emr.appointments.repository.AppointmentRepository;
import com.example.emr.appointments.domain.AppointmentStatus;
import com.example.emr.encounters.repository.EncounterRepository;
import com.example.emr.users.domain.Role;
import com.example.emr.visits.domain.VisitType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.emr.visits.repository.VisitRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;
    private final AppointmentService appointmentService;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final EncounterRepository encounterRepository;

    // HTTP 등록은 ID를 받아 검증한 엔티티로 기존 생성/잠금 로직에 위임한다.
    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    @Audit(action = "CREATE_VISIT", targetTable = "visits", targetId = "#result")
    public Long registerVisit(VisitRegistrationRequest request) {
        // Keep the patient lock as the first data read, including under MySQL REPEATABLE_READ.
        var patient = patientRepository.findByIdForUpdate(request.patientId()).orElseThrow(PatientNotFoundException::new);
        var doctor = doctorRepository.findById(request.doctorId()).orElseThrow(DoctorNotFoundException::new);
        if (!patient.isActive() || !doctor.isActive() || !doctor.getDepartment().isActive()
                || !doctor.getUser().isActive() || doctor.getUser().getRole() != Role.DOCTOR) {
            throw new InvalidVisitRegistrationException();
        }
        var info = request.appointmentId() == null ? null : appointmentRepository.findRegistrationInfo(request.appointmentId())
                .orElseThrow(AppointmentNotFoundException::new);
        if (info != null && (!info.getPatientId().equals(patient.getId())
                || !info.getDoctorId().equals(doctor.getId())
                || info.getStatus() != AppointmentStatus.SCHEDULED)) {
            throw new InvalidVisitRegistrationException();
        }
        // Do not put an Appointment snapshot in the persistence context before its existing slot/row locks.
        var appointment = info == null ? null : appointmentRepository.getReferenceById(request.appointmentId());
        try {
            // Self invocation deliberately shares this transaction/audit event; createVisit's body stays unchanged.
            return createVisit(new VisitCreateRequest(patient, appointment, doctor.getDepartment(), doctor,
                    appointment == null ? VisitType.WALK_IN : VisitType.APPOINTMENT));
        } catch (IllegalStateException exception) {
            // A concurrent cancellation/check-in can invalidate the pre-read appointment.
            throw new InvalidVisitRegistrationException();
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','NURSE')")
    public VisitResponse getVisit(Long id, LoginUser actor) {
        Visit visit = visitRepository.findWithDetailsById(id).orElseThrow(VisitNotFoundException::new);
        if (actor.role() == Role.DOCTOR && !visit.isAssignedDoctor(actor.userId())) {
            throw new AccessDeniedException("담당 의사가 아닙니다.");
        }
        return VisitResponse.from(visit, encounterIds(List.of(id)).get(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','NURSE')")
    public Page<VisitResponse> getVisits(LocalDate date, Long doctorId, Long patientId, VisitStatus status,
                                        Pageable pageable, LoginUser actor) {
        LocalDateTime from = (date == null ? LocalDate.now() : date).atStartOfDay();
        var visits = visitRepository.search(from, from.plusDays(1), doctorId, patientId, status,
                actor.role() == Role.DOCTOR ? actor.userId() : null, pageable);
        var ids = encounterIds(visits.getContent().stream().map(Visit::getId).toList());
        return visits.map(visit -> VisitResponse.from(visit, ids.get(visit.getId())));
    }

    private Map<Long, Long> encounterIds(List<Long> visits) {
        return visits.isEmpty() ? Map.of() : encounterRepository.findIdsByVisitIds(visits).stream()
                .collect(Collectors.toMap(EncounterRepository.VisitEncounterId::getVisitId, EncounterRepository.VisitEncounterId::getId));
    }

    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    @Audit(action = "CREATE_VISIT", targetTable = "visits", targetId = "#result")
    public Long createVisit(VisitCreateRequest request) {

        // 1. 환자 조회 및 잠금
        Patient patient = patientRepository
                .findByIdForUpdate(request.patient().getId())
                .orElseThrow(PatientNotFoundException::new);

        // 2. 당일 활성 접수 중복 검사
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime until = from.plusDays(1);
        boolean exists = visitRepository
                .existsActiveVisit(
                        patient.getId(),
                        request.department().getId(),
                        from,
                        until,
                        List.of(
                                VisitStatus.WAITING,
                                VisitStatus.IN_PROGRESS
                        )
                );

        if (exists) {
            throw new DuplicateVisitException();
        }

        // 3. 예약 환자라면 예약 상태 변경
        if (request.appointment() != null) {
            appointmentService.checkInAppointment(
                    request.appointment().getId(),
                    patient.getId()
            );
        }

        // 4. 접수 생성
        Visit visit = request.toEntity(patient);

        return visitRepository.save(visit).getId();
    }




    @Transactional
    @PreAuthorize("hasRole('STAFF')")
    @Audit(action = "CANCEL_VISIT", targetTable = "visits", targetId = "#visitId")
    public void cancelVisit(Long visitId) {
        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(VisitNotFoundException::new);
        visit.cancel();
    }

    @Transactional
    @PreAuthorize("hasRole('DOCTOR')")
    @Audit(action = "START_VISIT", targetTable = "visits", targetId = "#visitId")
    public void startVisit(Long visitId, LoginUser loginUser) {
        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(VisitNotFoundException::new);

        validateAssignedDoctor(visit, loginUser);

        boolean hasInProgress = visitRepository
                .existsByDoctor_IdAndStatus(
                        visit.getDoctor().getId(),
                        VisitStatus.IN_PROGRESS
                );

        if (hasInProgress) {
            throw new DoctorAlreadyInProgressException();
        }


        visit.start();
    }

    @Transactional
    @PreAuthorize("hasRole('DOCTOR')")
    @Audit(action = "COMPLETE_VISIT", targetTable = "visits", targetId = "#visitId")
    public void completeVisit(Long visitId) {

        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(VisitNotFoundException::new);

        visit.complete();
    }

    // 로그인 사용자가 담당 의사인지 확인
    private void validateAssignedDoctor(Visit visit, LoginUser loginUser) {
        if(!visit.isAssignedDoctor(loginUser.userId())) {
            throw new AccessDeniedException("담당 의사가 아닙니다.");
        }
    }

}
