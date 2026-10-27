package com.example.emr.users.repository;

import com.example.emr.users.domain.User;
import com.example.emr.users.dto.UserSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForDoctorAssignment(@Param("id") Long id);

    @Query("""
            SELECT u FROM User u
            WHERE (:#{#condition.username()} IS NULL
                   OR u.username LIKE CONCAT('%', :#{#condition.username()}, '%'))
              AND (:#{#condition.name()} IS NULL
                   OR u.name LIKE CONCAT('%', :#{#condition.name()}, '%'))
              AND (:#{#condition.role()} IS NULL OR u.role = :#{#condition.role()})
              AND (:#{#condition.active()} IS NULL OR u.isActive = :#{#condition.active()})
            """)
    Page<User> search(@Param("condition") UserSearchCondition condition, Pageable pageable);
}
