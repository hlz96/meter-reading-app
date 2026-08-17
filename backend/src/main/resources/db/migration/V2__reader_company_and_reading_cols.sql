-- ============================================================
-- V2 抄表员按公司分配 + reading 审核备注/索引 — 对应 TRD 决策⑤、§3.2、§3.3
-- 纯新增(additive),不改 V1。测试环境不执行本文件(H2 由 JPA entity 建表)。
-- ============================================================

-- 抄表员 ←→ 公司 分配(决策⑤:READER 只能看/写被分配公司下的表计)
-- 注意 member_id 指向 member.id(组织成员),不是 user.id
CREATE TABLE reader_company (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    org_id      BIGINT   NOT NULL,
    member_id   BIGINT   NOT NULL COMMENT '抄表员成员 member.id',
    company_id  BIGINT   NOT NULL COMMENT '分配到的公司',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reader_company_member_company (member_id, company_id),
    KEY idx_reader_company_org_company (org_id, company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄表员公司分配';

-- reading 审核备注(驳回原因),独立于抄表员填的 remark
ALTER TABLE reading ADD COLUMN audit_remark VARCHAR(255) NULL COMMENT '审核意见/驳回原因' AFTER audited_at;

-- TRD §3.3 汇总/审核加速索引
ALTER TABLE reading ADD KEY idx_reading_org_period_audit (org_id, period_id, audit_status);
ALTER TABLE reading ADD KEY idx_reading_org_period_company_type (org_id, period_id, company_id, type);
