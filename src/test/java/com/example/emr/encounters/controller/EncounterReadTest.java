package com.example.emr.encounters.controller;

import com.example.emr.departments.domain.Department;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.encounters.domain.Encounter;
import com.example.emr.encounters.dto.EncounterUpdateRequest;
import com.example.emr.encounters.repository.EncounterRepository;
import com.example.emr.encounters.service.EncounterService;
import com.example.emr.encounter_histories.repository.EncounterHistoryRepository;
import com.example.emr.visits.service.VisitService;
import com.example.emr.global.error.ApiExceptionHandler;
import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.repository.UserRepository;
import com.example.emr.visits.domain.Visit;
import com.example.emr.visits.domain.VisitType;
import com.example.emr.visits.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real method-security proxy and MVC serialization; repositories are mocked, no DB changes. */
@SpringJUnitConfig(EncounterReadTest.Config.class)
class EncounterReadTest {
    @Autowired EncounterService service;
    @Autowired EncounterRepository encounters;
    @Autowired VisitRepository visits;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(encounters, visits);
        mvc = MockMvcBuilders.standaloneSetup(new EncounterController(service), new PatientEncounterController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void otherDoctorCanReadCompletedVisitDetailsWithoutExposingEntities() throws Exception {
        Encounter encounter = fixture();
        var reader = new LoginUser(99L, Role.DOCTOR); // assigned doctor's users.id is 10
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                reader, null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        when(encounters.findWithDetailsById(1L)).thenReturn(Optional.of(encounter));

        mvc.perform(get("/api/encounters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.visitId").value(2))
                .andExpect(jsonPath("$.data.patientName").value("환자"))
                .andExpect(jsonPath("$.data.doctorName").value("담당 의사"))
                .andExpect(jsonPath("$.data.visitStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.chiefComplaint").value("기침"))
                .andExpect(jsonPath("$.data.clinicalNote").value("진찰 소견"))
                .andExpect(jsonPath("$.data.treatmentPlan").value("추후 방문"))
                .andExpect(jsonPath("$.data.visit").doesNotExist())
                .andExpect(jsonPath("$.data.patient").doesNotExist())
                .andExpect(jsonPath("$.data.doctor").doesNotExist());
        verifyNoInteractions(visits); // no ownership validation or write lock for reads
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void missingEncounterReturns404() throws Exception {
        when(encounters.findWithDetailsById(404L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/encounters/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("진료 기록을 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void patientHistoryAcceptsPageAndSortWithoutAssignedDoctorRestriction() throws Exception {
        var reader = new LoginUser(99L, Role.DOCTOR);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                reader, null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        var pageable = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "id"));
        when(encounters.findAllByPatientId(5L, pageable))
                .thenReturn(new PageImpl<>(List.of(fixture()), pageable, 3));

        mvc.perform(get("/api/patients/5/encounters")
                        .param("page", "1").param("size", "1").param("sort", "id,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].patientId").value(5))
                .andExpect(jsonPath("$.data.content[0].visitStatus").value("COMPLETED"));
        verify(encounters).findAllByPatientId(5L, pageable);
        verifyNoInteractions(visits);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void patientWithoutMatchingRecordsReturnsEmptyPageWithDefaults() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(encounters.findAllByPatientId(6L, pageable)).thenReturn(Page.empty(pageable));
        mvc.perform(get("/api/patients/6/encounters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isEmpty());
        verify(encounters).findAllByPatientId(6L, pageable);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void noGlobalEncounterListRouteIsExposed() throws Exception {
        mvc.perform(get("/api/encounters")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(encounters);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF", "NURSE"})
    void nonDoctorsCannotReadClinicalRecords(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reader", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        try {
            assertThatThrownBy(() -> service.getEncounter(1L)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.getPatientEncounters(5L, PageRequest.of(0, 20)))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(encounters);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unauthenticatedReadIsRejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.getEncounter(1L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.getPatientEncounters(5L, PageRequest.of(0, 20)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(encounters);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void relaxedReadPermissionDoesNotAllowAnotherDoctorToEdit() {
        var encounter = fixture();
        when(encounters.findVisitIdById(1L)).thenReturn(Optional.of(2L));
        when(visits.findByIdForUpdate(2L)).thenReturn(Optional.of(encounter.getVisit()));

        assertThatThrownBy(() -> service.updateEncounter(1L,
                new EncounterUpdateRequest(null, "수정 시도", null, "소견 정정"), new LoginUser(99L, Role.DOCTOR)))
                .isInstanceOf(AccessDeniedException.class);
        verify(encounters, never()).findByIdForUpdate(anyLong());
    }

    private Encounter fixture() {
        var user = new User("doctor", "hash", "담당 의사", Role.DOCTOR);
        ReflectionTestUtils.setField(user, "id", 10L);
        var department = new Department("IM", "내과");
        ReflectionTestUtils.setField(department, "id", 3L);
        var doctor = new Doctor(user, department, "LICENSE", null);
        ReflectionTestUtils.setField(doctor, "id", 4L);
        var patient = new Patient("P001", "환자", "01000000000", "주소", Gender.values()[0], LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(patient, "id", 5L);
        var visit = new Visit(patient, null, department, doctor, VisitType.WALK_IN);
        ReflectionTestUtils.setField(visit, "id", 2L);
        visit.start();
        visit.complete();
        var encounter = new Encounter(visit, "기침", "진찰 소견", "추후 방문");
        ReflectionTestUtils.setField(encounter, "id", 1L);
        return encounter;
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean EncounterRepository encounters() { return mock(EncounterRepository.class); }
        @Bean VisitRepository visits() { return mock(VisitRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean VisitService visitService() { return mock(VisitService.class); }
        @Bean EncounterHistoryRepository histories() { return mock(EncounterHistoryRepository.class); }
        @Bean EncounterService service(EncounterRepository encounters, VisitRepository visits, UserRepository users,
                                      VisitService visitService, EncounterHistoryRepository histories) {
            return new EncounterService(encounters, visits, users, histories);
        }
    }
}
