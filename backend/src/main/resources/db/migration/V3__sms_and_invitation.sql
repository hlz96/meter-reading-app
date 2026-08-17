-- ============================================================
-- V3 验证码 + 邀请码 — 对应 TRD §4.1 待实现端点
-- 纯新增(additive),不改 V1/V2。测试环境不执行本文件(H2 由 JPA entity 建表)。
-- ============================================================

-- 短信验证码(骨架阶段存 DB,不接短信网关;不依赖 Redis)
CREATE TABLE sms_code (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    phone       VARCHAR(20) NOT NULL COMMENT '手机号',
    code        VARCHAR(10) NOT NULL COMMENT '验证码',
    scene       VARCHAR(20) NOT NULL COMMENT '用途:REGISTER 等',
    expires_at  DATETIME    NOT NULL COMMENT '过期时间(代码显式赋值)',
    used        TINYINT     NOT NULL DEFAULT 0 COMMENT '0未用 1已用',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sms_phone_scene (phone, scene, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码';

-- 组织邀请码(一次性:join 成功即置已用)
CREATE TABLE invitation (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    org_id      BIGINT      NOT NULL,
    code        VARCHAR(32) NOT NULL COMMENT '邀请码,全局唯一',
    role        VARCHAR(20) NOT NULL COMMENT '加入后的角色 ADMIN/READER/VIEWER',
    expires_at  DATETIME    NOT NULL COMMENT '过期时间',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0未用 1已用 2作废',
    created_by  BIGINT      NULL COMMENT '生成人 user_id',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitation_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织邀请码';
