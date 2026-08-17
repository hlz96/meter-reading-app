package com.meter.app.reading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * reading 模块端到端:周期、用量计算、上期衔接、倒退异常、审核流转与权限。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadingFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;
    private long meterId;   // 倍率 2 的电表

    private String register(String phone, String org) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"%s\"}"
                                .formatted(phone, org)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("accessToken").asText();
    }

    private long postForId(String url, String token, String body, String dataIdPath) throws Exception {
        String resp = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get(dataIdPath).asLong();
    }

    private void setup(String phone) throws Exception {
        token = register(phone, "抄表园区" + phone);
        companyId = postForId("/api/v1/companies", token, "{\"name\":\"A公司\"}", "id");
        // 电表,初始底数 100,倍率 2
        meterId = postForId("/api/v1/meters", token,
                "{\"companyId\":%d,\"name\":\"电表\",\"type\":1,\"initialReading\":100,\"ratio\":2}"
                        .formatted(companyId), "id");
    }

    private long createPeriod(String name) throws Exception {
        return postForId("/api/v1/periods", token,
                "{\"name\":\"%s\",\"elecPrice\":0.6}".formatted(name), "id");
    }

    private JsonNode submitReading(long periodId, String currReading) throws Exception {
        String resp = mvc.perform(post("/api/v1/readings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":%s}"
                                .formatted(meterId, periodId, currReading)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data");
    }

    @Test
    void usageCalculationAndPrevChaining() throws Exception {
        setup("13811000001");
        long p1 = createPeriod("2026-01");

        // 首期:上期取初始底数 100,本期 150,倍率 2 → 用量 (150-100)*2 = 100
        JsonNode r1 = submitReading(p1, "150");
        org.junit.jupiter.api.Assertions.assertEquals(100.0, r1.get("prevReading").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(100.0, r1.get("usageAmount").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(1, r1.get("auditStatus").asInt()); // 待审核
        org.junit.jupiter.api.Assertions.assertFalse(r1.get("isAbnormal").asBoolean());

        // 上期读数须审核通过后才能作为下期基准(TRD §5.3),先审核 p1
        approve(r1.get("id").asLong());

        // 次期:上期应衔接到已通过的 150,本期 200 → 用量 (200-150)*2 = 100
        long p2 = createPeriod("2026-02");
        JsonNode r2 = submitReading(p2, "200");
        org.junit.jupiter.api.Assertions.assertEquals(150.0, r2.get("prevReading").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(100.0, r2.get("usageAmount").asDouble(), 0.001);
    }

    /** 管理员审核通过某条读数。 */
    private void approve(long readingId) throws Exception {
        mvc.perform(post("/api/v1/readings/" + readingId + "/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.auditStatus", is(2)));
    }

    @Test
    void backwardReadingFlaggedAbnormal() throws Exception {
        setup("13811000002");
        long p1 = createPeriod("2026-03");
        // 本期 50 < 初始底数 100 → 倒退异常,用量记 0
        JsonNode r = submitReading(p1, "50");
        org.junit.jupiter.api.Assertions.assertTrue(r.get("isAbnormal").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("BACKWARD", r.get("abnormalType").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0.0, r.get("usageAmount").asDouble(), 0.001);
    }

    @Test
    void auditFlowAndPermission() throws Exception {
        setup("13811000003");
        long p1 = createPeriod("2026-04");
        long readingId = submitReading(p1, "150").get("id").asLong();

        // 管理员审核通过 → auditStatus=2
        mvc.perform(post("/api/v1/readings/" + readingId + "/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.auditStatus", is(2)));

        // 非管理员(另一组织的普通登录)审核 → 这里用 READER 角色场景:
        // 由于当前仅管理员注册,构造一个只有 VIEWER/READER 的场景较复杂,
        // 改为验证:另一组织管理员无法看到/审核本组织读数 → 404
        String otherToken = register("13800138011", "别的园区");
        mvc.perform(post("/api/v1/readings/" + readingId + "/audit")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(jsonPath("$.code", is(3001)));
    }
}
