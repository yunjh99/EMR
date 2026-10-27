package com.example.emr.visits.repository;

import com.example.emr.visits.domain.Visit;
import com.example.emr.visits.domain.VisitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    // 해당 의사에게 지정된 상태의 접수가 존재하는지 확인한다.
    boolean existsByDoctor_IdAndStatus(Long doctorId, VisitStatus status);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "department", "appointment"})
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "department", "appointment"})
    @Query(value = """
            select v from Visit v where v.checkedInAt >= :from and v.checkedInAt < :until
              and (:doctorId is null or v.doctor.id = :doctorId)
              and (:patientId is null or v.patient.id = :patientId)
              and (:status is null or v.status = :status)
              and (:ownerId is null or v.doctor.user.id = :ownerId)
            """, countQuery = """
            select count(v) from Visit v where v.checkedInAt >= :from and v.checkedInAt < :until
              and (:doctorId is null or v.doctor.id = :doctorId)
              and (:patientId is null or v.patient.id = :patientId)
              and (:status is null or v.status = :status)
              and (:ownerId is null or v.doctor.user.id = :ownerId)
            """)
    Page<Visit> search(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
                       @Param("doctorId") Long doctorId, @Param("patientId") Long patientId,
                       @Param("status") VisitStatus status, @Param("ownerId") Long ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select (count(v) > 0) from Visit v
            where v.patient.id = :patientId
              and v.department.id = :departmentId
              and v.checkedInAt >= :from
              and v.checkedInAt < :until
              and v.status in :statuses
            """)
    boolean existsActiveVisit(
            @Param("patientId") Long patientId,
            @Param("departmentId") Long departmentId,
            @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until,
            @Param("statuses") Collection<VisitStatus> statuses
    );

}
