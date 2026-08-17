package com.meter.app.reading.repository;

import com.meter.app.reading.entity.Period;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodRepository extends JpaRepository<Period, Long> {

    List<Period> findByOrgIdOrderByIdDesc(Long orgId);

    Optional<Period> findByIdAndOrgId(Long id, Long orgId);
}
