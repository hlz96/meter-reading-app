package com.meter.app.reading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "period")
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** 本期电价,手动输入(元/度)。可空,未定价时费用列显示未定价。 */
    @Column(name = "elec_price", precision = 10, scale = 4)
    private BigDecimal elecPrice;

    /** 本期水价,手动输入(元/吨)。 */
    @Column(name = "water_price", precision = 10, scale = 4)
    private BigDecimal waterPrice;

    /** 1进行中 2已结算 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
