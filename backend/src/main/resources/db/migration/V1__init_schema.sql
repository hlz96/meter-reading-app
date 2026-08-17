-- ============================================================
-- V1 初始表结构 — 对应 TRD 3.2 / 3.3
-- 引擎 InnoDB,字符集 utf8mb4
-- ============================================================

-- 组织
CREATE TABLE organization (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '组织名称',
    contact     VARCHAR(50)  NULL COMMENT '联系人',
    phone       VARCHAR(20)  NULL COMMENT '联系电话',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织';

-- 用户
CREATE TABLE `user` (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    phone          VARCHAR(20)  NOT NULL COMMENT '手机号,登录标识',
    email          VARCHAR(100) NULL,
    password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    nickname       VARCHAR(50)  NULL,
    status         INT          NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 组织成员(用户在组织中的角色)
CREATE TABLE member (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    org_id      BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    role        VARCHAR(20) NOT NULL COMMENT 'ADMIN/READER/VIEWER',
    status      INT         NOT NULL DEFAULT 1 COMMENT '0邀请中 1已加入 2停用',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_org_user (org_id, user_id),
    KEY idx_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织成员';

-- 公司(租户)
CREATE TABLE company (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL COMMENT '公司名称',
    contact     VARCHAR(50)  NULL COMMENT '联系人',
    phone       VARCHAR(20)  NULL COMMENT '联系电话',
    remark      VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_company_org_name (org_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司';

-- 表计
CREATE TABLE meter (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    org_id           BIGINT        NOT NULL,
    company_id       BIGINT        NOT NULL COMMENT '所属公司',
    name             VARCHAR(100)  NOT NULL COMMENT '表名/编号',
    type             INT           NOT NULL COMMENT '1电表 2水表',
    initial_reading  DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '初始底数',
    ratio            DECIMAL(10,3) NOT NULL DEFAULT 1 COMMENT '倍率',
    location         VARCHAR(100)  NULL COMMENT '安装位置',
    status           INT           NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_meter_org_company (org_id, company_id),
    KEY idx_meter_org_type_status (org_id, type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表计';

-- 抄表周期
CREATE TABLE period (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    org_id       BIGINT        NOT NULL,
    name         VARCHAR(50)   NOT NULL COMMENT '如 2026-08',
    start_date   DATE          NULL COMMENT '自定义起始日',
    end_date     DATE          NULL COMMENT '自定义结束日',
    elec_price   DECIMAL(10,4) NULL COMMENT '本期电价,手动输入(元/度)',
    water_price  DECIMAL(10,4) NULL COMMENT '本期水价,手动输入(元/吨)',
    status       INT           NOT NULL DEFAULT 1 COMMENT '1进行中 2已结算',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_period_org_status (org_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄表周期';

-- 抄表记录
CREATE TABLE reading (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    org_id        BIGINT        NOT NULL,
    meter_id      BIGINT        NOT NULL,
    period_id     BIGINT        NOT NULL,
    company_id    BIGINT        NOT NULL COMMENT '冗余,避免汇总 join',
    type          INT           NOT NULL COMMENT '冗余,1电 2水',
    prev_reading  DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '上期读数',
    curr_reading  DECIMAL(14,3) NOT NULL COMMENT '本期读数',
    usage_amount  DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '用量=(curr-prev)*ratio',
    photo_url     VARCHAR(255)  NULL,
    reader_id     BIGINT        NULL COMMENT '抄表人',
    read_at       DATETIME      NULL COMMENT '抄表时间',
    is_abnormal   INT           NOT NULL DEFAULT 0 COMMENT '1异常',
    abnormal_type VARCHAR(20)   NULL COMMENT 'BACKWARD/SPIKE',
    audit_status  INT           NOT NULL DEFAULT 1 COMMENT '审核状态 1待审核 2已通过 3已驳回',
    auditor_id    BIGINT        NULL COMMENT '审核人',
    audited_at    DATETIME      NULL COMMENT '审核时间',
    client_uuid   VARCHAR(64)   NULL COMMENT '离线幂等键',
    remark        VARCHAR(255)  NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reading_meter_period (meter_id, period_id),
    UNIQUE KEY uk_reading_org_client_uuid (org_id, client_uuid),
    KEY idx_reading_org_period (org_id, period_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄表记录';

-- 可配置导入模板
CREATE TABLE import_template (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    org_id         BIGINT       NOT NULL,
    name           VARCHAR(50)  NOT NULL COMMENT '模板名',
    field_mapping  JSON         NOT NULL COMMENT 'Excel列↔系统字段映射',
    is_default     INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_template_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入模板';

-- 审计日志
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    org_id      BIGINT       NOT NULL,
    user_id     BIGINT       NULL,
    action      VARCHAR(50)  NOT NULL COMMENT '操作类型',
    target      VARCHAR(100) NULL COMMENT '操作对象',
    detail      JSON         NULL COMMENT '变更前后',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_org_time (org_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';
