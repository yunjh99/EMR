-- MySQL schema snapshot; new empty demo database only.
-- No account/patient data. Existing constraints are preserved.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE `appointment_slots` (
  `doctor_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `slot_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_slot` (`doctor_id`,`slot_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `appointments` (
  `created_at` datetime(6) NOT NULL,
  `doctor_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL,
  `scheduled_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `status` enum('CANCELLED','CHECKED_IN','SCHEDULED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doctor_scheduled_at` (`doctor_id`,`scheduled_at`),
  KEY `FK8exap5wmg8kmb1g1rx3by21yt` (`patient_id`),
  CONSTRAINT `FK8exap5wmg8kmb1g1rx3by21yt` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`),
  CONSTRAINT `FKmujeo4tymoo98cmf7uj3vsv76` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_logs` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `action` varchar(30) NOT NULL,
  `failure_reason` varchar(100) DEFAULT NULL,
  `target_table` varchar(100) NOT NULL,
  `result` enum('DENIED','FAILURE','SUCCESS') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `departments` (
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `code` varchar(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_departments_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `doctors` (
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `department_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `license_no` varchar(30) NOT NULL,
  `specialty` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doctors_user` (`user_id`),
  UNIQUE KEY `uk_doctors_license` (`license_no`),
  KEY `fk_doctors_department` (`department_id`),
  CONSTRAINT `fk_doctors_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `fk_doctors_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `encounter_histories` (
  `changed_at` datetime(6) NOT NULL,
  `changed_by` bigint NOT NULL,
  `encounter_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version` bigint NOT NULL,
  `change_reason` varchar(500) NOT NULL,
  `chief_complaint` text,
  `clinical_note` text,
  `treatment_plan` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_encounter_history_version` (`encounter_id`,`version`),
  KEY `fk_encounter_history_user` (`changed_by`),
  CONSTRAINT `fk_encounter_history_encounter` FOREIGN KEY (`encounter_id`) REFERENCES `encounters` (`id`),
  CONSTRAINT `fk_encounter_history_user` FOREIGN KEY (`changed_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `encounters` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `visit_id` bigint NOT NULL,
  `chief_complaint` text NOT NULL,
  `clinical_note` text NOT NULL,
  `treatment_plan` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK734g8rw5wdedls1nmfwv87d5k` (`visit_id`),
  CONSTRAINT `FK734g8rw5wdedls1nmfwv87d5k` FOREIGN KEY (`visit_id`) REFERENCES `visits` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `patient_histories` (
  `audit_log_id` bigint NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` bigint NOT NULL,
  `field_name` varchar(50) NOT NULL,
  `new_value` text,
  `old_value` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `patient_number_sequences` (
  `id` bigint NOT NULL,
  `last_number` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `patients` (
  `birth_date` date NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `patient_no` varchar(20) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `address` varchar(255) NOT NULL,
  `gender` char(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_login_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('ADMIN','DOCTOR','NURSE','STAFF') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `visits` (
  `appointment_id` bigint DEFAULT NULL,
  `checked_in_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `department_id` bigint NOT NULL,
  `doctor_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `in_progress_doctor_id` bigint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'IN_PROGRESS') then `doctor_id` else NULL end)) STORED,
  `patient_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','IN_PROGRESS','WAITING') NOT NULL,
  `visit_type` enum('APPOINTMENT','WALK_IN') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_one_in_progress_per_doctor` (`in_progress_doctor_id`),
  KEY `FKfpy5talf3e95w9iie6gr8emsy` (`appointment_id`),
  KEY `FKh88ac3nnsfroplfhj358blrej` (`department_id`),
  KEY `FKth95fndjk3y3nepjfu3f66r63` (`doctor_id`),
  KEY `FKra5p2e0tp6djolm46kdr42cyt` (`patient_id`),
  CONSTRAINT `FKfpy5talf3e95w9iie6gr8emsy` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`),
  CONSTRAINT `FKh88ac3nnsfroplfhj358blrej` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `FKra5p2e0tp6djolm46kdr42cyt` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`),
  CONSTRAINT `FKth95fndjk3y3nepjfu3f66r63` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS=1;
