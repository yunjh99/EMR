package com.example.emr.encounters.repository;

import com.example.emr.encounters.domain.Encounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {
    interface VisitEncounterId { Long getId(); Long getVisitId(); }

    @Query("select e.id as id, e.visit.id as visitId from Encounter e where e.visit.id in :visitIds")
    List<VisitEncounterId> findIdsByVisitIds(@Param("visitIds") Collection<Long> visitIds);
    boolean existsByVisit_Id(Long visitId);

    @EntityGraph(attributePaths = {"visit", "visit.patient", "visit.doctor", "visit.doctor.user", "visit.department"})
    @Query("select e from Encounter e where e.id = :id")
    Optional<Encounter> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"visit", "visit.patient", "visit.doctor", "visit.doctor.user", "visit.department"})
    @Query(value = "select e from Encounter e where e.visit.patient.id = :patientId",
            countQuery = "select count(e) from Encounter e where e.visit.patient.id = :patientId")
    Page<Encounter> findAllByPatientId(@Param("patientId") Long patientId, Pageable pageable);

    @Query("select e.visit.id from Encounter e where e.id = :id")
    Optional<Long> findVisitIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Encounter e where e.id = :id")
    Optional<Encounter> findByIdForUpdate(@Param("id") Long id);
}
