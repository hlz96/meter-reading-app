package com.meter.app.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短信验证码(TRD §4.1)。骨架阶段存 DB,不接短信网关、不依赖 Redis。
 */
@Getter
@Setter
@Entity
@Table(name = "sms_code")
public class SmsCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 20)
    private String scene;

    /** 代码显式赋值,故可插入 + 非空 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 0未用 1已用 */
    @Column(nullable = false)
    private Integer used = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
