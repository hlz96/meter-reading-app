package com.meter.app.reader.repository;

import com.meter.app.reader.entity.ReaderCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReaderCompanyRepository extends JpaRepository<ReaderCompany, Long> {

    List<ReaderCompany> findByOrgIdAndMemberId(Long orgId, Long memberId);

    void deleteByOrgIdAndMemberId(Long orgId, Long memberId);
}
