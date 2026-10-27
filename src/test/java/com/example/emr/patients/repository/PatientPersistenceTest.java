package com.example.emr.patients.repository;

import com.example.emr.patient_histories.domain.PatientHistory;
import com.example.emr.patient_histories.repository.PatientHistoryRepository;
import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.domain.Gender;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PatientPersistenceTest {
    @Autowired
    private PatientRepository patients;
    @Autowired
    private PatientHistoryRepository histories;
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesPatientAndFieldHistory() {
        var patient = patients.saveAndFlush(new Patient("P0001", "테스트 환자",
                "010-0000-0000", "테스트 주소", Gender.F, LocalDate.of(1990, 1, 2)));
        var history = histories.saveAndFlush(new PatientHistory(patient.getId(), 1L,
                "phone", null, "010-0000-0000"));
        entityManager.clear();

        var savedPatient = patients.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.getId()).isPositive();
        assertThat(savedPatient.getPatientNo()).isEqualTo("P0001");
        assertThat(savedPatient.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
        assertThat(savedPatient.getGender()).isEqualTo(Gender.F);
        assertThat(savedPatient.isActive()).isTrue();
        assertThat(savedPatient.getCreatedAt()).isNotNull();
        assertThat(savedPatient.getUpdatedAt()).isNotNull();

        var savedHistory = histories.findById(history.getId()).orElseThrow();
        assertThat(savedHistory.getId()).isPositive();
        assertThat(savedHistory.getPatientId()).isEqualTo(patient.getId());
        assertThat(savedHistory.getAuditLogId()).isEqualTo(1L);
        assertThat(savedHistory.getOldValue()).isNull();
        assertThat(savedHistory.getNewValue()).isEqualTo("010-0000-0000");
        assertThat(savedHistory.getChangedAt()).isNotNull();
    }
}
