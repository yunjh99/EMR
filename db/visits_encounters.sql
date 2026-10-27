-- Prerequisite only when visits/encounters do not yet exist. Existing tables must not be recreated.
-- MySQL 8. Existing users/patients/departments/doctors/appointments tables are required.
CREATE TABLE visits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    appointment_id BIGINT NULL,
    department_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    visit_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    checked_in_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_visits_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_visits_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_visits_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_visits_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE encounters (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    chief_complaint TEXT NOT NULL,
    clinical_note TEXT NOT NULL,
    treatment_plan TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_encounters_visit FOREIGN KEY (visit_id) REFERENCES visits(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
