package com.example.emr.patients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "patient_number_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientNumberSequence {

    @Id
    private Long id;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;
}
