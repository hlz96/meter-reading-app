package com.meter.app.ledger.repository;

import com.meter.app.ledger.entity.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MeterRepository extends JpaRepository<Meter, Long>,
        JpaSpecificationExecutor<Meter> {

    Optional<Meter> findByIdAndOrgId(Long id, Long orgId);

    // 导入时按表名定位;用 List 以便检测重名歧义(同名多条→该行报错)
    List<Meter> findByOrgIdAndName(Long orgId, String name);

    boolean existsByCompanyId(Long companyId);

    long countByOrgIdAndCompanyId(Long orgId, Long companyId);
}
