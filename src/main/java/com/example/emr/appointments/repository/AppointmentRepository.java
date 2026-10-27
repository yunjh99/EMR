package com.example.emr.appointments.repository;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.appointments.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    interface RegistrationInfo { Long getPatientId(); Long getDoctorId(); AppointmentStatus getStatus(); }

    @Query("select a.patient.id as patientId, a.doctor.id as doctorId, a.status as status from Appointment a where a.id = :id")
    Optional<RegistrationInfo> findRegistrationInfo(@Param("id") Long id);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "patient"})
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "patient"})
    @Query(value = """
            select a from Appointment a
            where (:from is null or a.scheduledAt >= :from)
              and (:until is null or a.scheduledAt < :until)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:status is null or a.status = :status)
            """, countQuery = """
            select count(a) from Appointment a
            where (:from is null or a.scheduledAt >= :from)
              and (:until is null or a.scheduledAt < :until)
              and (:doctorId is null or a.doctor.id = :doctorId)
              and (:status is null or a.status = :status)
            """)
    Page<Appointment> search(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
                             @Param("doctorId") Long doctorId, @Param("status") AppointmentStatus status,
                             Pageable pageable);

    @Query("""
            select new com.example.emr.appointments.repository.AppointmentSlotKey(a.doctor.id, a.scheduledAt)
            from Appointment a where a.id = :id
            """)
    Optional<AppointmentSlotKey> findSlotKeyById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    boolean existsByDoctor_IdAndScheduledAtAndStatus(
            Long doctorId,
            LocalDateTime scheduledAt,
            AppointmentStatus status
    );
}
