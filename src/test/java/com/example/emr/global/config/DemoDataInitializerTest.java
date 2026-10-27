package com.example.emr.global.config;

import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.demo-data.enabled=true")
@DirtiesContext
class DemoDataInitializerTest {
    @Autowired DemoDataInitializer initializer;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    @Transactional
    void startupCreatesUsableFixturesAndRestartPreservesEditsWithoutDuplicates() {
        for (Role role : Role.values()) {
            User user = em.createQuery("select u from User u where u.username = :name", User.class)
                    .setParameter("name", "demo_" + role.name().toLowerCase()).getSingleResult();
            assertThat(user.getRole()).isEqualTo(role);
            assertThat(user.isActive()).isTrue();
            assertThat(user.getPasswordHash()).isNotEqualTo("star");
            assertThat(passwords.matches("star", user.getPasswordHash())).isTrue();
        }
        assertThat(count("appointment_slots")).isEqualTo(540);
        assertThat(count("patients")).isEqualTo(7);
        assertThat(count("appointments")).isEqualTo(3);
        assertThat(count("visits")).isEqualTo(4);
        assertThat(count("encounters")).isEqualTo(1);
        assertThat(count("encounter_histories")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from encounters", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select version from encounter_histories", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from visits where status='COMPLETED' and completed_at is not null", Long.class)).isEqualTo(1);

        User staff = em.createQuery("select u from User u where u.username='demo_staff'", User.class).getSingleResult();
        staff.update("수정한 이름 유지", null, false);
        String hash = staff.getPasswordHash();
        initializer.run(new DefaultApplicationArguments());

        assertThat(staff.getName()).isEqualTo("수정한 이름 유지");
        assertThat(staff.isActive()).isFalse();
        assertThat(staff.getPasswordHash()).isEqualTo(hash);
        assertThat(count("users")).isEqualTo(4);
        assertThat(count("departments")).isEqualTo(1);
        assertThat(count("doctors")).isEqualTo(1);
        assertThat(count("patients")).isEqualTo(7);
        assertThat(count("appointment_slots")).isEqualTo(540);
        assertThat(count("appointments")).isEqualTo(3);
        assertThat(count("visits")).isEqualTo(4);
        assertThat(count("encounter_histories")).isEqualTo(1);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
