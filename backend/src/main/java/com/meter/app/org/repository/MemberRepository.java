package com.meter.app.org.repository;

import com.meter.app.org.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByUserId(Long userId);

    Optional<Member> findByOrgIdAndUserId(Long orgId, Long userId);

    Optional<Member> findByIdAndOrgId(Long id, Long orgId);

    List<Member> findByOrgId(Long orgId);
}
