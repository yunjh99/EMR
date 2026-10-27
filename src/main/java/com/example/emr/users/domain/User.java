package com.example.emr.users.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 20, nullable = false)
    private String username;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Role role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public User(String username, String passwordHash, String name, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
    }

    public void update(String name, Role role, Boolean active) {
        if (name != null) this.name = name;
        if (role != null) this.role = role;
        if (active != null) this.isActive = active;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    // 생성 시 생성일, 수정일 현재 시각으로 저장
    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 수정 시 수정일 현재 시각으로 저장
    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
