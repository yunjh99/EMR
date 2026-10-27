package com.example.emr.doctors.repository;

import com.example.emr.doctors.domain.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    boolean existsByUserId(Long userId);
    boolean existsByLicenseNo(String licenseNo);
    boolean existsByLicenseNoAndIdNot(String licenseNo, Long id);


    @Query("""
            select d from Doctor d join fetch d.user u join fetch d.department dep
            where (:departmentId is null or dep.id = :departmentId)
              and (:includeInactive = true or
                   (d.isActive = true and dep.isActive = true and u.isActive = true
                    and u.role = com.example.emr.users.domain.Role.DOCTOR))
            order by d.id asc
            """)
    List<Doctor> search(@Param("departmentId") Long departmentId, @Param("includeInactive") boolean includeInactive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
