package com.meter.app.ledger.repository;

import com.meter.app.ledger.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByOrgIdOrderByIdDesc(Long orgId);

    // orgId 一并作为条件,避免越权访问他组织的公司
    Optional<Company> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByOrgIdAndName(Long orgId, String name);
}
