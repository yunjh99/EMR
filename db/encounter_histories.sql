-- MySQL 8, one-time additive migration. Stop application writes before applying.
-- Apply to an existing encounters/users schema; do not run Hibernate ddl-auto=create.
-- Adding NOT NULL DEFAULT 1 initializes every existing Encounter to business version 1.
ALTER TABLE encounters ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE encounter_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    encounter_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    chief_complaint TEXT NULL,
    clinical_note TEXT NULL,
    treatment_plan TEXT NULL,
    changed_by BIGINT NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_encounter_history_version UNIQUE (encounter_id, version),
    CONSTRAINT fk_encounter_history_encounter FOREIGN KEY (encounter_id) REFERENCES encounters (id),
    CONSTRAINT fk_encounter_history_user FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
