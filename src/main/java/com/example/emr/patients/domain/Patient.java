package com.example.emr.patients.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// patients 테이블: 환자 기본 정보
@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    // 환자 데이터의 내부 ID. DB에서 자동 생성
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 화면과 업무에서 사용하는 환자 번호
    @Column(name = "patient_no", length = 20, nullable = false)
    private String patientNo;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // 성별 코드 한 글자. 허용 코드에 대한 검증은 요청 DTO에서 정의
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", columnDefinition = "CHAR(1)", nullable = false)
    private Gender gender;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "address", length = 255, nullable = false)
    private String address;

    // 신규 환자는 기본 활성 상태
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 최초 저장 시 null, 정보가 수정되면 갱신
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Patient(String patientNo, String name, String phone, String address, Gender gender, LocalDate birthDate) {
        this.patientNo = patientNo;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.gender = gender;
        this.birthDate = birthDate;
    }

    public void update(String name, LocalDate birthDate, Gender gender, String phone, String address) {
        if (name != null) this.name = name;
        if (birthDate != null) this.birthDate = birthDate;
        if (gender != null) this.gender = gender;
        if (phone != null) this.phone = phone;
        if (address != null) this.address = address;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
