package com.meter.app.reading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "reading")
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "meter_id", nullable = false)
    private Long meterId;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 冗余:1电 2水,便于汇总不 join */
    @Column(nullable = false)
    private Integer type;

    @Column(name = "prev_reading", nullable = false, precision = 14, scale = 3)
    private BigDecimal prevReading = BigDecimal.ZERO;

    @Column(name = "curr_reading", nullable = false, precision = 14, scale = 3)
    private BigDecimal currReading;

    @Column(name = "usage_amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal usageAmount = BigDecimal.ZERO;

    @Column(name = "photo_url", length = 255)
    private String photoUrl;

    @Column(name = "reader_id")
    private Long readerId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** 1异常 0正常 */
    @Column(name = "is_abnormal", nullable = false)
    private Integer isAbnormal = 0;

    /** BACKWARD 倒退 / SPIKE 突增 */
    @Column(name = "abnormal_type", length = 20)
    private String abnormalType;

    /** 审核状态 1待审核 2已通过 3已驳回 */
    @Column(name = "audit_status", nullable = false)
    private Integer auditStatus = 1;

    @Column(name = "auditor_id")
    private Long auditorId;

    @Column(name = "audited_at")
    private LocalDateTime auditedAt;

    /** 审核意见/驳回原因,独立于抄表员填的 remark */
    @Column(name = "audit_remark", length = 255)
    private String auditRemark;

    @Column(name = "client_uuid", length = 64)
    private String clientUuid;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
