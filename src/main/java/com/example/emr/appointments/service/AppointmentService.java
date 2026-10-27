package com.example.emr.appointments.service;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.appointments.domain.AppointmentStatus;
import com.example.emr.appointments.repository.AppointmentSlotRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.example.emr.appointments.domain.AppointmentConstraintViolations;
import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.example.emr.appointments.dto.AppointmentResponse;
import com.example.emr.appointments.repository.AppointmentRepository;
import com.example.emr.audit_logs.annotation.Audit;
import com.example.emr.global.error.exception.DuplicateAppointmentException;
import com.example.emr.global.error.exception.InvalidAppointmentTimeException;
import com.example.emr.global.error.exception.AppointmentNotFoundException;
import com.example.emr.global.error.exception.AppointmentSlotNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSING_TIME = LocalTime.of(18, 0);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;

    @Transactional
    @Audit(action = "CREATE_APPOINTMENT", targetTable = "appointments", targetId = "#result")
    @PreAuthorize("hasRole('STAFF')")
    public Long createAppointment(AppointmentCreateRequest request){

        // 1. 예약 시간 검증
        validateScheduledAt(request.scheduledAt());

        Long doctorId = request.doctor().getId();
        LocalDateTime scheduledAt = request.scheduledAt();

        // 1. 예약 슬롯 잠금
        appointmentSlotRepository.findForUpdate(
                doctorId,
                scheduledAt
        ).orElseThrow(() -> new IllegalStateException("예약 슬롯이 존재하지 않습니다."));

        // 3. 활성 예약 중복 확인
        boolean exists = appointmentRepository
                .existsByDoctor_IdAndScheduledAtAndStatus(
                        doctorId,
                        scheduledAt,
                        AppointmentStatus.SCHEDULED
                );
        if (exists) {throw new DuplicateAppointmentException();}

        // 3. 예약 생성
        Appointment appointment = request.toEntity();
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','NURSE')")
    public AppointmentResponse getAppointment(Long id) {
        return AppointmentResponse.from(appointmentRepository.findWithDetailsById(id)
                .orElseThrow(AppointmentNotFoundException::new));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','NURSE')")
    public Page<AppointmentResponse> getAppointments(LocalDate date, Long doctorId,
                                                    AppointmentStatus status, Pageable pageable) {
        LocalDateTime from = date == null ? null : date.atStartOfDay();
        LocalDateTime until = date == null ? null : date.plusDays(1).atStartOfDay();
        return appointmentRepository.search(from, until, doctorId, status, pageable)
                .map(AppointmentResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audit(action = "CANCEL_APPOINTMENT", targetTable = "appointments", targetId = "#id")
    @PreAuthorize("hasRole('STAFF')")
    public void cancelAppointment(Long id) {
        var key = appointmentRepository.findSlotKeyById(id)
                .orElseThrow(AppointmentNotFoundException::new);
        // 생성과 동일한 순서: 슬롯을 먼저 잠근 뒤 최신 예약 상태를 읽는다.
        appointmentSlotRepository.findForUpdate(key.doctorId(), key.scheduledAt())
                .orElseThrow(AppointmentSlotNotFoundException::new);
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(AppointmentNotFoundException::new);
        appointment.cancel();
        appointmentRepository.flush();
    }

    @Transactional
    public void checkInAppointment(Long appointmentId, Long patientId) {

        // 1. 예약 슬롯 정보 조회
        var key = appointmentRepository.findSlotKeyById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);

        // 2. 슬롯 잠금
        appointmentSlotRepository.findForUpdate(
                key.doctorId(),
                key.scheduledAt()
        ).orElseThrow(AppointmentSlotNotFoundException::new);

        // 3. 예약 행 잠금
        Appointment appointment = appointmentRepository
                .findByIdForUpdate(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);

        // 4. 예약 환자 일치 여부 확인
        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new IllegalArgumentException("예약 환자 정보가 일치하지 않습니다.");
        }

        // 5. 예약 상태 변경
        appointment.checkIn();
    }

    private void validateScheduledAt(LocalDateTime scheduledAt) {
        if (scheduledAt == null) {
            throw new InvalidAppointmentTimeException();
        }

        LocalTime time = scheduledAt.toLocalTime();
        if (time.isBefore(OPENING_TIME) || !time.isBefore(CLOSING_TIME)
                || time.getMinute() % 30 != 0
                || time.getSecond() != 0 || time.getNano() != 0) {
            throw new InvalidAppointmentTimeException();
        }
    }

}
