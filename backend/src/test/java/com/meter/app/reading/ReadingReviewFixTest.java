package com.meter.app.reading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meter.app.auth.JwtService;
import com.meter.app.auth.entity.User;
import com.meter.app.auth.repository.UserRepository;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.MemberRepository;
import com.meter.app.reader.entity.ReaderCompany;
import com.meter.app.reader.repository.ReaderCompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 回归测试:验证 code-review 修复的问题。
 * - #1 权限:READER 不能删公司/审核读数
 * - #2 已结算周期禁止录入
 * - #3 乱序周期(先建晚周期再补早周期)上期读数按日期正确衔接
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadingReviewFixTest {

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
    ReaderCompanyRepository readerCompanyRepository;

    private String adminToken;
    private long adminOrgId;
    private long companyId;
    private long meterId;

    private String register(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(resp).get("data");
        adminOrgId = data.get("orgId").asLong();
        return data.get("accessToken").asText();
    }

    private long postForId(String url, String token, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    private void setupLedger(String phone) throws Exception {
        adminToken = register(phone);
        companyId = postForId("/api/v1/companies", adminToken, "{\"name\":\"A公司\"}");
        meterId = postForId("/api/v1/meters", adminToken,
                "{\"companyId\":%d,\"name\":\"电表\",\"type\":1,\"initialReading\":100,\"ratio\":2}"
                        .formatted(companyId));
    }

    /**
     * 在同组织下 seed 一个真实的 READER 成员,并分配到 companyId,返回其 token。
     * (邀请接口尚未实现,测试直接持久化 User+Member+ReaderCompany。)
     */
    private String readerTokenSameOrg(String phone) {
        User u = new User();
        u.setPhone(phone);
        u.setPasswordHash("x");
        u.setNickname(phone);
        u = userRepository.save(u);

        Member m = new Member();
        m.setOrgId(adminOrgId);
        m.setUserId(u.getId());
        m.setRole(Role.READER);
        m.setStatus(1);
        m = memberRepository.save(m);

        ReaderCompany rc = new ReaderCompany();
        rc.setOrgId(adminOrgId);
        rc.setMemberId(m.getId());
        rc.setCompanyId(companyId);
        readerCompanyRepository.save(rc);

        return jwtService.generateAccessToken(u.getId(), adminOrgId, "READER");
    }

    @Test
    void readerCannotDeleteCompanyOrAudit() throws Exception {
        setupLedger("13820000001");
        String readerToken = readerTokenSameOrg("13820000091");

        // READER 删公司 → 2003 无权限
        mvc.perform(delete("/api/v1/companies/" + companyId)
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(2003)));

        // READER 建周期 → 2003(周期管理是 ADMIN)
        mvc.perform(post("/api/v1/periods")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2026-05\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(2003)));

        // READER 可以提交读数(这是抄表员的职责)
        long periodId = postForId("/api/v1/periods", adminToken, "{\"name\":\"2026-05\",\"elecPrice\":0.6}");
        String rResp = mvc.perform(post("/api/v1/readings")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":150}"
                                .formatted(meterId, periodId)))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        long readingId = om.readTree(rResp).get("data").get("id").asLong();

        // READER 审核 → 2003(审核仅 ADMIN)
        mvc.perform(post("/api/v1/readings/" + readingId + "/audit")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(2003)));
    }

    @Test
    void settledPeriodRejectsSubmit() throws Exception {
        setupLedger("13820000002");
        long periodId = postForId("/api/v1/periods", adminToken, "{\"name\":\"2026-06\",\"elecPrice\":0.6}");

        // 结算周期
        mvc.perform(post("/api/v1/periods/" + periodId + "/settle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.status", is(2)));

        // 已结算周期录入 → 3002 冲突
        mvc.perform(post("/api/v1/readings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":150}"
                                .formatted(meterId, periodId)))
                .andExpect(jsonPath("$.code", is(3002)));
    }

    @Test
    void outOfOrderPeriodsUseDateForPrevReading() throws Exception {
        setupLedger("13820000003");

        // 先创建"6月"周期(start_date 靠后),录数 300
        long june = postForId("/api/v1/periods", adminToken,
                "{\"name\":\"2026-06\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-30\",\"elecPrice\":0.6}");
        mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":300}".formatted(meterId, june)))
                .andExpect(jsonPath("$.code", is(0)));

        // 后创建"5月"周期(start_date 靠前,但 period_id 更大),录数 200
        long may = postForId("/api/v1/periods", adminToken,
                "{\"name\":\"2026-05\",\"startDate\":\"2026-05-01\",\"endDate\":\"2026-05-31\",\"elecPrice\":0.6}");
        String mayResp = mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":200}".formatted(meterId, may)))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        JsonNode mayData = om.readTree(mayResp).get("data");

        // 关键断言:5月的上期读数应取"初始底数100"(5月之前无周期),
        // 而不是错误地取到 period_id 更小但日期更晚的...等等——
        // 5月之前没有任何周期,所以上期=初始底数100,用量=(200-100)*2=200
        org.junit.jupiter.api.Assertions.assertEquals(100.0, mayData.get("prevReading").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(200.0, mayData.get("usageAmount").asDouble(), 0.001);

        // 5月读数须审核通过后,才能作为6月的上期基准(TRD §5.3)
        long mayReadingId = mayData.get("id").asLong();
        mvc.perform(post("/api/v1/readings/" + mayReadingId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(jsonPath("$.data.auditStatus", is(2)));

        // 反过来验证:6月的上期应能正确取到已通过的5月读数200(按日期,5月<6月)
        // 重新提交6月(最新覆盖),应重新计算上期为200
        String juneResp = mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":350}".formatted(meterId, june)))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        JsonNode juneData = om.readTree(juneResp).get("data");
        // 6月上期=5月的200,用量=(350-200)*2=300
        org.junit.jupiter.api.Assertions.assertEquals(200.0, juneData.get("prevReading").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(300.0, juneData.get("usageAmount").asDouble(), 0.001);
    }
}
