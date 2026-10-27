package com.example.emr.audit_logs.repository;

import com.example.emr.audit_logs.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
