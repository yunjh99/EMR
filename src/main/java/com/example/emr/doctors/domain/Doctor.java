package com.example.emr.doctors.domain;

import com.example.emr.departments.domain.Department;
import com.example.emr.users.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctors", uniqueConstraints = {
        @UniqueConstraint(name = "uk_doctors_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_doctors_license", columnNames = "license_no")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_doctors_user"))
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_doctors_department"))
    private Department department;
    @Column(name = "license_no", nullable = false, length = 30)
    private String licenseNo;
    @Column(length = 100)
    private String specialty;
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Doctor(User user, Department department, String licenseNo, String specialty) {
        this.user = user;
        this.department = department;
        this.licenseNo = licenseNo;
        this.specialty = specialty;
    }

    public void update(Department department, String licenseNo, String specialty) {
        this.department = department;
        this.licenseNo = licenseNo;
        this.specialty = specialty;
    }
    public void deactivate() { isActive = false; }
    @PrePersist
    private void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate
    private void onUpdate() { updatedAt = LocalDateTime.now(); }
}
