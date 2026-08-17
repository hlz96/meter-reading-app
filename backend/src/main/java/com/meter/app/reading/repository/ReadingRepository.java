package com.meter.app.reading.repository;

import com.meter.app.reading.entity.Reading;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long>,
        JpaSpecificationExecutor<Reading> {

    Optional<Reading> findByIdAndOrgId(Long id, Long orgId);

    // 批量审核:只取本组织的记录(越权 id 自然被过滤)
    List<Reading> findByOrgIdAndIdIn(Long orgId, List<Long> ids);

    // 结算校验:该周期内非"已通过"的读数数量(>0 则不可结算)
    long countByOrgIdAndPeriodIdAndAuditStatusNot(Long orgId, Long periodId, Integer auditStatus);

    // 该周期是否有某类型(1电/2水)的读数(判断是否必须填对应费率)
    boolean existsByOrgIdAndPeriodIdAndType(Long orgId, Long periodId, Integer type);

    // 该周期指定审核状态的读数数量(报表 pendingCount 用)
    long countByOrgIdAndPeriodIdAndAuditStatus(Long orgId, Long periodId, Integer auditStatus);

    // 某表某周期是否已抄(唯一约束对应)
    Optional<Reading> findByMeterIdAndPeriodId(Long meterId, Long periodId);

    // 上期读数(兜底):周期未填日期时,退回按 period_id 排序。只取已通过审核的记录作基准(TRD §5.3)
    Optional<Reading> findFirstByMeterIdAndAuditStatusAndPeriodIdLessThanOrderByPeriodIdDesc(
            Long meterId, Integer auditStatus, Long periodId);

    List<Reading> findByOrgIdAndPeriodId(Long orgId, Long periodId);

    /**
     * 按公司+类型汇总用量,只统计已通过审核(audit_status=2)的读数(TRD §5.5)。
     * 直接用 reading 冗余的 company_id/type,免 join meter。
     */
    @Query("""
            SELECT new com.meter.app.report.dto.UsageAgg(r.companyId, r.type, SUM(r.usageAmount))
            FROM Reading r
            WHERE r.orgId = :orgId AND r.periodId = :periodId AND r.auditStatus = 2
            GROUP BY r.companyId, r.type
            """)
    List<com.meter.app.report.dto.UsageAgg> sumUsageByCompanyType(@Param("orgId") Long orgId,
                                                                  @Param("periodId") Long periodId);

    /**
     * 某公司跨周期用量趋势(TRD §4.5 图表 trend)。只算已通过审核。
     * type 为 null 时不限类型(电+水合计);按周期起始日排序,null 起始日用 period_id 兜底。
     */
    @Query("""
            SELECT new com.meter.app.report.dto.TrendPoint(p.name, SUM(r.usageAmount))
            FROM Reading r, Period p
            WHERE r.periodId = p.id
              AND r.orgId = :orgId
              AND r.companyId = :companyId
              AND r.auditStatus = 2
              AND (:type IS NULL OR r.type = :type)
            GROUP BY p.id, p.name, p.startDate
            ORDER BY p.startDate, p.id
            """)
    List<com.meter.app.report.dto.TrendPoint> usageTrendByCompany(@Param("orgId") Long orgId,
                                                                  @Param("companyId") Long companyId,
                                                                  @Param("type") Integer type);

    /**
     * 上期读数(首选):按周期 start_date 排序,取当前周期之前最近一次读数。
     * 解决"先建8月再补录7月"时 period_id 顺序 != 时间顺序的用量算错问题。
     * 只取 audit_status=2(已通过)的记录作基准,避免拿未审数据当上期(TRD §5.3)。
     */
    @Query("""
            SELECT r FROM Reading r, Period p
            WHERE r.periodId = p.id
              AND r.meterId = :meterId
              AND p.startDate < :startDate
              AND r.auditStatus = 2
            ORDER BY p.startDate DESC, r.periodId DESC
            """)
    List<Reading> findPrevByStartDate(@Param("meterId") Long meterId,
                                      @Param("startDate") LocalDate startDate,
                                      Pageable pageable);

    /**
     * 该表历史用量均值,DB 端聚合,排除倒退(usage=0)与未通过审核的记录。
     * 只统计 audit_status=2(已通过)且用量>0 的记录,避免误报突增。
     */
    @Query("""
            SELECT AVG(r.usageAmount) FROM Reading r
            WHERE r.meterId = :meterId
              AND r.periodId <> :excludePeriodId
              AND r.auditStatus = 2
              AND r.usageAmount > 0
            """)
    BigDecimal avgUsageExcludingPeriod(@Param("meterId") Long meterId,
                                       @Param("excludePeriodId") Long excludePeriodId);
}

