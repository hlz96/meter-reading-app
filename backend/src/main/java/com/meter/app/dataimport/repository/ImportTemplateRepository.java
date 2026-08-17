package com.meter.app.dataimport.repository;

import com.meter.app.dataimport.entity.ImportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportTemplateRepository extends JpaRepository<ImportTemplate, Long> {

    List<ImportTemplate> findByOrgIdOrderByIdDesc(Long orgId);

    Optional<ImportTemplate> findByIdAndOrgId(Long id, Long orgId);
}
