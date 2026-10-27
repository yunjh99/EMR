package com.example.emr.departments.repository;

import com.example.emr.departments.domain.Department;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    List<Department> findByIsActiveTrueOrderByCodeAsc();
    List<Department> findAllByOrderByCodeAsc();

    // 배정과 진료과 비활성화가 동시에 진행되지 않게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") Long id);
}
