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
 * 周期结算校验(Gap3)与批量审核(Gap4)端到端。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PeriodSettleAndBatchAuditTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;
    private long elecMeterId;

    private void setup(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = om.readTree(resp).get("data").get("accessToken").asText();
        companyId = postForId("/api/v1/companies", "{\"name\":\"A公司\"}");
        elecMeterId = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"电表1\",\"type\":1,\"initialReading\":100,\"ratio\":1}"
                        .formatted(companyId));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    private long submitReading(long periodId, long meterId, String curr) throws Exception {
        String resp = mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":%s}"
                                .formatted(meterId, periodId, curr)))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    @Test
    void settleRejectedWhenPendingReadings() throws Exception {
        setup("13830000001");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.6}");
        submitReading(p, elecMeterId, "200"); // 待审核

        // 有待审读数 → 结算被拒 4003
        mvc.perform(post("/api/v1/periods/" + p + "/settle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(4003)));
    }

    @Test
    void settleRejectedWhenPriceMissing() throws Exception {
        setup("13830000002");
        // 建周期时不填电价
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-07\"}");
        long rid = submitReading(p, elecMeterId, "200");
        approve(rid);

        // 全通过但电价未填 → 4003
        mvc.perform(post("/api/v1/periods/" + p + "/settle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(4003)));
    }

    @Test
    void settleSucceedsWhenAllApprovedAndPriced() throws Exception {
        setup("13830000003");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-08\",\"elecPrice\":0.6}");
        long rid = submitReading(p, elecMeterId, "200");
        approve(rid);

        mvc.perform(post("/api/v1/periods/" + p + "/settle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.status", is(2)));
    }

    @Test
    void emptyPeriodCanSettle() throws Exception {
        setup("13830000004");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-09\"}");
        // 无任何读数 → 允许结算
        mvc.perform(post("/api/v1/periods/" + p + "/settle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.status", is(2)));
    }

    @Test
    void batchAuditApprovesMultiple() throws Exception {
        setup("13830000005");
        long meter2 = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"电表2\",\"type\":1,\"initialReading\":0,\"ratio\":1}"
                        .formatted(companyId));
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-10\",\"elecPrice\":0.6}");
        long r1 = submitReading(p, elecMeterId, "200");
        long r2 = submitReading(p, meter2, "50");

        // 批量通过两条
        mvc.perform(post("/api/v1/readings/audit/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d],\"approved\":true}".formatted(r1, r2)))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.processed", is(2)));

        // 列表按 auditStatus=2 应看到两条
        mvc.perform(get("/api/v1/readings?periodId=" + p + "&auditStatus=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    private void approve(long readingId) throws Exception {
        mvc.perform(post("/api/v1/readings/" + readingId + "/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(jsonPath("$.data.auditStatus", is(2)));
    }
}
