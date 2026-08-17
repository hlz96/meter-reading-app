package com.meter.app.reader.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 抄表员 ←→ 公司 分配(TRD 决策⑤)。
 * member_id 指向 member.id(组织成员),不是 user.id。
 */
@Getter
@Setter
@Entity
@Table(name = "reader_company")
public class ReaderCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
