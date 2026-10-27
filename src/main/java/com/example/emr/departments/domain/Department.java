package com.example.emr.departments.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(name = "uk_departments_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Department(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void deactivate() { isActive = false; }

    @PrePersist
    private void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate
    private void onUpdate() { updatedAt = LocalDateTime.now(); }
}
