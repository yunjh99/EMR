-- MySQL: 두 테이블이 아직 없는 환경 전용. users 테이블은 기존 것을 사용한다.
-- 이 파일은 애플리케이션에서 자동 실행하지 않는다.
CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT uk_departments_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE doctors (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    license_no VARCHAR(30) NOT NULL,
    specialty VARCHAR(100) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    CONSTRAINT uk_doctors_user UNIQUE (user_id),
    CONSTRAINT uk_doctors_license UNIQUE (license_no),
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT
) ENGINE=InnoDB;
