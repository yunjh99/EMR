package com.example.emr.patients.repository;

import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import com.example.emr.patients.dto.PatientSearchCondition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class PatientRepositorySearchTest {
    @Autowired PatientRepository repository;
    @Autowired EntityManager em;
    @Autowired WebApplicationContext context;
    private final LocalDate birthDate = LocalDate.of(1990, 1, 2);

    @BeforeEach
    void setUp() {
        repository.saveAllAndFlush(List.of(
                new Patient("P001", "김하늘", "010-1111-2222", "서울 강남구", Gender.F, birthDate),
                new Patient("P002", "김서준", "010-3333-4444", "부산 해운대구", Gender.M, birthDate),
                new Patient("P003", "이%지우", "010-5555-6666", "서울 마포구", Gender.F, LocalDate.of(2000, 3, 4))
        ));
        em.createQuery("update Patient p set p.isActive=false where p.patientNo='P002'").executeUpdate();
        em.clear();
    }

    @Test
    void noConditionsReturnsAllWithPageAndCount() {
        var page = repository.findAllPatientByCondition(new PatientSearchCondition(null, null, null, null),
                PageRequest.of(1, 2, Sort.by("patientNo")));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Patient::getPatientNo).containsExactly("P003");
    }

    @Test
    void onlySuppliedConditionsAreCombinedWithAnd() {
        var condition = new PatientSearchCondition(" 김 ", birthDate, " 1111 ", " 강남 ");
        var page = repository.findAllPatientByCondition(condition, PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(Patient::getPatientNo).containsExactly("P001");
        assertThat(repository.findAllPatientByCondition(new PatientSearchCondition(null, birthDate, null, null),
                Pageable.unpaged()).getTotalElements()).isEqualTo(2);
    }

    @Test
    void nullAndBlankConditionsDoNotFilter() {
        assertThat(repository.findAllPatientByCondition(null, Pageable.unpaged()).getTotalElements()).isEqualTo(3);
        var condition = new PatientSearchCondition("  ", null, "", "\t");
        assertThat(repository.findAllPatientByCondition(condition, Pageable.unpaged()).getTotalElements()).isEqualTo(3);
    }

    @Test
    void patientNumberAndFalseActiveRemainSupported() {
        var condition = new PatientSearchCondition(null, null, null, null, " P00 ", false);
        assertThat(repository.findAllPatientByCondition(condition, Pageable.unpaged()).getContent())
                .extracting(Patient::getPatientNo).containsExactly("P002");
    }

    @Test
    void partialSearchEscapesWildcardAndNoMatchesHaveZeroCount() {
        assertThat(repository.findAllPatientByCondition(new PatientSearchCondition("%", null, null, null),
                Pageable.unpaged()).getContent()).extracting(Patient::getPatientNo).containsExactly("P003");
        assertThat(repository.findAllPatientByCondition(new PatientSearchCondition("없음", null, null, null),
                PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void controllerBindsOptionalConditionsDateAndPageable() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/patients").param("birthDate", "1990-01-02")
                        .param("size", "1").param("page", "1").param("sort", "patientNo,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].patientNo").value("P002"));
        mvc.perform(get("/api/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }
}
