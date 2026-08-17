package com.meter.app.dataimport.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 可配置导入模板(TRD §6.4,决策⑥)。
 * fieldMapping 存 Excel 列名 → 系统字段 的映射(JSON),如 {"表名":"meterName","本期读数":"currReading"}。
 */
@Getter
@Setter
@Entity
@Table(name = "import_template")
public class ImportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false, length = 50)
    private String name;

    /** MySQL 侧为 JSON 列;Hibernate 6 用 @JdbcTypeCode 做 String↔JSON 映射 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_mapping", columnDefinition = "json", nullable = false)
    private String fieldMapping;

    @Column(name = "is_default", nullable = false)
    private Integer isDefault = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
