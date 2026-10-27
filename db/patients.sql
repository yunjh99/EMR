-- 신규 테이블 생성용 MySQL DDL. 기존 엔티티와 동일하게 ID는 AUTO_INCREMENT 사용.
CREATE TABLE patients (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_no VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    gender CHAR(1) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL
);

CREATE TABLE patient_histories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    audit_log_id BIGINT NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    change_reason VARCHAR(500) NULL,
    changed_at DATETIME NOT NULL
);
