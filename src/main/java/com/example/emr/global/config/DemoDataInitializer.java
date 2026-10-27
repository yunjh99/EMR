package com.example.emr.global.config;

import com.example.emr.appointments.domain.Appointment;
import com.example.emr.departments.domain.Department;
import com.example.emr.doctors.domain.Doctor;
import com.example.emr.encounter_histories.domain.EncounterHistory;
import com.example.emr.encounters.domain.Encounter;
import com.example.emr.patients.domain.Gender;
import com.example.emr.patients.domain.Patient;
import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.visits.domain.Visit;
import com.example.emr.visits.domain.VisitType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 로컬 포트폴리오 시연용 가상 데이터. 기존 데이터는 수정하거나 삭제하지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        user("demo_admin", "데모 관리자", Role.ADMIN);
        User doctorUser = user("demo_doctor", "데모 김의사", Role.DOCTOR);
        user("demo_staff", "데모 원무직원", Role.STAFF);
        user("demo_nurse", "데모 간호사", Role.NURSE);

        Department department = em.createQuery("select d from Department d where d.code = :code", Department.class)
                .setParameter("code", "DEMO_INTERNAL").getResultStream().findFirst()
                .orElseGet(() -> persist(new Department("DEMO_INTERNAL", "데모 내과")));
        Doctor doctor = em.createQuery("select d from Doctor d where d.user.id = :id", Doctor.class)
                .setParameter("id", doctorUser.getId()).getResultStream().findFirst()
                .orElseGet(() -> persist(new Doctor(doctorUser, department, "DEMO-LICENSE-001", "일반 내과")));

        LocalDate today = LocalDate.now();
        // 기존 슬롯은 유지하고 오늘부터 30일간 누락된 09:00~17:30 슬롯만 보충한다.
        for (int day = 0; day < 30; day++) {
            for (int slot = 0; slot < 18; slot++) {
                LocalDateTime at = today.plusDays(day).atTime(9, 0).plusMinutes(slot * 30L);
                jdbc.update("""
                        insert into appointment_slots (doctor_id, slot_at)
                        select ?, ? where not exists (
                            select 1 from appointment_slots where doctor_id = ? and slot_at = ?)
                        """, doctor.getId(), at, doctor.getId(), at);
            }
        }

        String[] names = {"가상 김하늘", "가상 이서준", "가상 박지우", "가상 최민서", "가상 정다온", "가상 윤도현", "가상 한수빈"};
        for (int i = 1; i <= names.length; i++) {
            String patientNo = "DEMO-P%03d".formatted(i);
            boolean exists = !em.createQuery("select p.id from Patient p where p.patientNo = :no", Long.class)
                    .setParameter("no", patientNo).setMaxResults(1).getResultList().isEmpty();
            // 환자 번호가 있으면 시연 중 변경한 예약/접수/기록도 그대로 둔다.
            if (exists) continue;
            Patient patient = persist(new Patient(patientNo, names[i - 1], "010-0000-%04d".formatted(i),
                    "가상시 데모구 예시로 " + i, i % 2 == 0 ? Gender.M : Gender.F,
                    LocalDate.of(1985 + i, i, 10)));
            if (i == 1) {
                Appointment appointment = appointment(doctor, patient, today.atTime(9, 0), "[데모] 예약 방문 접수");
                appointment.checkIn();
                persist(new Visit(patient, appointment, doctor.getDepartment(), doctor, VisitType.APPOINTMENT));
            } else if (i == 2) {
                Visit visit = walkIn(patient, doctor);
                visit.start();
            } else if (i == 3) {
                Visit visit = walkIn(patient, doctor);
                visit.start();
                Encounter encounter = persist(new Encounter(visit, "[가상] 기침, 인후 불편감",
                        "[가상] 포트폴리오 시연용 진료 소견입니다.", "[가상] 시연용 경과 관찰 계획입니다."));
                visit.complete();
                persist(new EncounterHistory(encounter, doctorUser, "시연용 치료 계획 정정 예시"));
                encounter.update(null, null, "[가상] 시연용 정정된 경과 관찰 계획입니다.");
            } else if (i == 4) {
                walkIn(patient, doctor).cancel();
            } else if (i == 5) {
                appointment(doctor, patient, today.atTime(10, 0), "[데모] 접수 전 예약");
            } else if (i == 7) {
                appointment(doctor, patient, today.atTime(11, 0), "[데모] 취소 이력 예시").cancel();
            }
            // 6번 환자는 예약 없는 신규 접수 시연용으로 비워 둔다.
        }
        em.flush();
        log.info("Demo fixtures ready: demo_admin, demo_doctor, demo_staff, demo_nurse (existing records preserved)");
    }

    private User user(String username, String name, Role role) {
        User existing = em.createQuery("select u from User u where u.username = :name", User.class)
                .setParameter("name", username).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            if (existing.getRole() != role) {
                throw new IllegalStateException("데모 계정 ID가 다른 역할의 기존 계정과 충돌합니다: " + username);
            }
            return existing;
        }
        return persist(new User(username, passwords.encode("star"), name, role));
    }

    private Appointment appointment(Doctor doctor, Patient patient, LocalDateTime at, String reason) {
        return persist(new Appointment(doctor, patient, at, reason));
    }

    private Visit walkIn(Patient patient, Doctor doctor) {
        return persist(new Visit(patient, null, doctor.getDepartment(), doctor, VisitType.WALK_IN));
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
