package com.meter.app.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meter.app.audit.service.AuditLogService;
import com.meter.app.auth.JwtService;
import com.meter.app.auth.entity.User;
import com.meter.app.auth.repository.UserRepository;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 成员角色变更(Gap7 配套接口)端到端:改角色成功并写审计;不能改自己;非管理员拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberRoleTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    JwtService jwtService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    AuditLogService auditLogService; // 确保 bean 装配
    @Autowired
    com.meter.app.audit.repository.AuditLogRepository auditLogRepository;

    private String adminToken;
    private long orgId;

    private void setup(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(resp).get("data");
        adminToken = data.get("accessToken").asText();
        orgId = data.get("orgId").asLong();
    }

    private long seedMember(String phone, Role role) {
        User u = new User();
        u.setPhone(phone);
        u.setPasswordHash("x");
        u.setNickname(phone);
        u = userRepository.save(u);
        Member m = new Member();
        m.setOrgId(orgId);
        m.setUserId(u.getId());
        m.setRole(role);
        m.setStatus(1);
        return memberRepository.save(m).getId();
    }

    @Test
    void adminChangesMemberRoleAndAudits() throws Exception {
        setup("13860000001");
        long memberId = seedMember("13860000091", Role.VIEWER);

        mvc.perform(patch("/api/v1/members/" + memberId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"READER\"}"))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.role", is("READER")));

        // 审计应有一条 MEMBER_ROLE_CHANGE
        long count = auditLogRepository.findByOrgIdAndActionOrderByIdDesc(orgId, "MEMBER_ROLE_CHANGE")
                .stream().filter(a -> ("member:" + memberId).equals(a.getTarget())).count();
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void cannotChangeOwnRole() throws Exception {
        setup("13860000002");
        // 管理员自己的 memberId
        long selfMemberId = memberRepository.findByOrgId(orgId).get(0).getId();

        mvc.perform(patch("/api/v1/members/" + selfMemberId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(jsonPath("$.code", is(1001)));
    }

    @Test
    void nonAdminCannotChangeRole() throws Exception {
        setup("13860000003");
        long memberId = seedMember("13860000093", Role.VIEWER);
        // 用一个 READER token 尝试改角色 → 2003
        long readerMemberId = seedMember("13860000094", Role.READER);
        long readerUserId = memberRepository.findById(readerMemberId).orElseThrow().getUserId();
        String readerToken = jwtService.generateAccessToken(readerUserId, orgId, "READER");

        mvc.perform(patch("/api/v1/members/" + memberId + "/role")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(2003)));
    }
}
