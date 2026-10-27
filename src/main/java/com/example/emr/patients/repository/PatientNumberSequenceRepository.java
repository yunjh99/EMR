package com.example.emr.patients.repository;

import com.example.emr.patients.domain.PatientNumberSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PatientNumberSequenceRepository extends JpaRepository<PatientNumberSequence, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO patient_number_sequences (id, last_number)
            VALUES (1, 1)
            ON DUPLICATE KEY UPDATE last_number = last_number + 1
            """, nativeQuery = true)
    void increment();

    @Query(value = "SELECT last_number FROM patient_number_sequences WHERE id = 1", nativeQuery = true)
    long currentNumber();
}
