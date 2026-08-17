package com.meter.app.audit.repository;

import com.meter.app.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOrgIdAndActionOrderByIdDesc(Long orgId, String action);

    List<AuditLog> findByOrgIdOrderByIdDesc(Long orgId);
}
