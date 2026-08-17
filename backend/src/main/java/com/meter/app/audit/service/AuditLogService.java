package com.meter.app.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meter.app.audit.entity.AuditLog;
import com.meter.app.audit.repository.AuditLogRepository;
import com.meter.app.auth.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 审计写入(TRD §5.6/§7.2)。自动带当前登录态的 orgId/userId。
 * 审计失败不应影响主业务流程,内部吞异常记 warn。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    // 审计动作类型
    public static final String READING_AUDIT = "READING_AUDIT";        // 读数审核(通过/驳回)
    public static final String READING_OVERWRITE = "READING_OVERWRITE"; // 离线最新覆盖旧读数
    public static final String READER_ASSIGN = "READER_ASSIGN";        // 抄表员公司分配变更
    public static final String MEMBER_ROLE_CHANGE = "MEMBER_ROLE_CHANGE"; // 成员角色变更
    public static final String MEMBER_JOIN = "MEMBER_JOIN";            // 凭邀请码加入组织

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /** 记录一条审计。detail 会被序列化成 JSON;传 null 则不写 detail。 */
    public void record(String action, String target, Object detail) {
        try {
            AuditLog logEntry = new AuditLog();
            logEntry.setOrgId(CurrentUser.orgId());
            logEntry.setUserId(CurrentUser.userId());
            logEntry.setAction(action);
            logEntry.setTarget(target);
            if (detail != null) {
                logEntry.setDetail(objectMapper.writeValueAsString(detail));
            }
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("写审计日志失败 action={} target={}: {}", action, target, e.getMessage());
        }
    }
}
