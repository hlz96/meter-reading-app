package com.meter.app.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 抄表员按公司分配(Gap1/决策⑤)端到端:
 * - 管理员设置/查询分配
 * - READER 只能为被分配公司抄表(超范围 → 2003)
 * - READER 列表只见被分配公司的读数
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReaderScopeFlowTest {

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

    private String adminToken;
    private long orgId;
    private long companyA;
    private long companyB;
    private long meterA;  // 属 A 公司
    private long meterB;  // 属 B 公司

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
        companyA = postForId("/api/v1/companies", "{\"name\":\"A公司\"}");
        companyB = postForId("/api/v1/companies", "{\"name\":\"B公司\"}");
        meterA = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"A表\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyA));
        meterB = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"B表\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyB));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    /** seed 一个真实 READER 成员,返回 [memberId, token]。 */
    private Object[] seedReader(String phone) {
        User u = new User();
        u.setPhone(phone);
        u.setPasswordHash("x");
        u.setNickname(phone);
        u = userRepository.save(u);
        Member m = new Member();
        m.setOrgId(orgId);
        m.setUserId(u.getId());
        m.setRole(Role.READER);
        m.setStatus(1);
        m = memberRepository.save(m);
        String token = jwtService.generateAccessToken(u.getId(), orgId, "READER");
        return new Object[]{m.getId(), token};
    }

    private void assignCompanies(long memberId, String jsonArray) throws Exception {
        mvc.perform(post("/api/v1/readers/" + memberId + "/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyIds\":" + jsonArray + "}"))
                .andExpect(jsonPath("$.code", is(0)));
    }

    @Test
    void adminAssignsAndQueriesCompanies() throws Exception {
        setup("13850000001");
        Object[] reader = seedReader("13850000091");
        long memberId = (long) reader[0];

        assignCompanies(memberId, "[" + companyA + "]");

        mvc.perform(get("/api/v1/readers/" + memberId + "/companies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0]", is((int) companyA)));
    }

    @Test
    void readerCanSubmitOnlyForAssignedCompany() throws Exception {
        setup("13850000002");
        Object[] reader = seedReader("13850000092");
        long memberId = (long) reader[0];
        String readerToken = (String) reader[1];
        assignCompanies(memberId, "[" + companyA + "]");

        long period = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.6}");

        // 给已分配的 A 表抄表 → 成功
        mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":50}".formatted(meterA, period)))
                .andExpect(jsonPath("$.code", is(0)));

        // 给未分配的 B 表抄表 → 2003 无权限
        mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":50}".formatted(meterB, period)))
                .andExpect(jsonPath("$.code", is(2003)));
    }

    @Test
    void readerListSeesOnlyAssignedCompany() throws Exception {
        setup("13850000003");
        Object[] reader = seedReader("13850000093");
        long memberId = (long) reader[0];
        String readerToken = (String) reader[1];
        assignCompanies(memberId, "[" + companyA + "]");

        long period = postForId("/api/v1/periods", "{\"name\":\"2026-07\",\"elecPrice\":0.6}");
        // 管理员给两个公司都录读数
        submitAsAdmin(meterA, period);
        submitAsAdmin(meterB, period);

        // 管理员看到 2 条
        mvc.perform(get("/api/v1/readings?periodId=" + period)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data", hasSize(2)));

        // READER 只看到 A 公司的 1 条
        mvc.perform(get("/api/v1/readings?periodId=" + period)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].meterId", is((int) meterA)));
    }

    @Test
    void assignRejectsCompanyFromOtherOrg() throws Exception {
        setup("13850000004");
        Object[] reader = seedReader("13850000094");
        long memberId = (long) reader[0];

        // 用一个不存在的 companyId → 参数错误
        mvc.perform(post("/api/v1/readers/" + memberId + "/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyIds\":[999999]}"))
                .andExpect(jsonPath("$.code", is(1001)));
    }

    private void submitAsAdmin(long meterId, long periodId) throws Exception {
        mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":50}".formatted(meterId, periodId)))
                .andExpect(jsonPath("$.code", is(0)));
    }
}
