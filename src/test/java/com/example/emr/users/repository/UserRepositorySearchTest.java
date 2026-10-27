package com.example.emr.users.repository;

import com.example.emr.users.domain.Role;
import com.example.emr.users.domain.User;
import com.example.emr.users.dto.UserSearchCondition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jwt.secret-key=01234567890123456789012345678901")
@Transactional
class UserRepositorySearchTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.saveAllAndFlush(List.of(
                new User("doctor1", "hash", "김의사", Role.DOCTOR),
                new User("doctor2", "hash", "이의사", Role.DOCTOR),
                new User("nurse1", "hash", "김간호", Role.NURSE)
        ));
        entityManager.createQuery("update User u set u.isActive = false where u.username = 'doctor2'")
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    void noConditionsReturnsPaginatedResultsAndTotalCount() {
        var page = repository.search(new UserSearchCondition(null, null, null, null),
                PageRequest.of(0, 2, Sort.by("username")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(User::getUsername)
                .containsExactly("doctor1", "doctor2");
    }

    @Test
    void combinesAllFourConditions() {
        var page = repository.search(new UserSearchCondition(" doctor ", " 김 ", Role.DOCTOR, true),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("doctor1");
    }

    @Test
    void falseSelectsOnlyInactiveAccounts() {
        var page = repository.search(new UserSearchCondition(null, null, null, false),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(User::getUsername).containsExactly("doctor2");
    }

    @Test
    void blankStringsDoNotFilterResults() {
        var page = repository.search(new UserSearchCondition("  ", "", null, null),
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
