package com.example.emr.appointments.repository;

import com.example.emr.appointments.domain.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AppointmentSlotRepository
        extends JpaRepository<AppointmentSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM AppointmentSlot s
        WHERE s.doctorId = :doctorId
          AND s.slotAt = :slotAt
    """)
    Optional<AppointmentSlot> findForUpdate(
            @Param("doctorId") Long doctorId,
            @Param("slotAt") LocalDateTime slotAt
    );
}