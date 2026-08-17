package com.meter.app.report;

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
 * 报表/催单端到端(Gap2):只算已通过读数、费用计算、未定价 fee 为 null。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;
    private long elecMeterId;
    private long waterMeterId;

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
                "{\"companyId\":%d,\"name\":\"电表\",\"type\":1,\"initialReading\":100,\"ratio\":1}"
                        .formatted(companyId));
        waterMeterId = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"水表\",\"type\":2,\"initialReading\":0,\"ratio\":1}"
                        .formatted(companyId));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    private long submit(long periodId, long meterId, String curr) throws Exception {
        String resp = mvc.perform(post("/api/v1/readings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":%s}"
                                .formatted(meterId, periodId, curr)))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    private void approve(long id) throws Exception {
        mvc.perform(post("/api/v1/readings/" + id + "/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(jsonPath("$.data.auditStatus", is(2)));
    }

    @Test
    void summaryOnlyCountsApprovedAndComputesFee() throws Exception {
        setup("13840000001");
        // 只填电价,不填水价
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.5}");
        long er = submit(p, elecMeterId, "300");  // 电用量 200
        submit(p, waterMeterId, "80");            // 水用量 80,但不审核

        approve(er); // 只通过电表

        String resp = mvc.perform(get("/api/v1/reports/summary?periodId=" + p)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(resp).get("data");

        // 只有电表已通过 → 1 行;水表未审核不计入
        org.junit.jupiter.api.Assertions.assertEquals(1, data.get("rows").size());
        org.junit.jupiter.api.Assertions.assertEquals(1, data.get("pendingCount").asInt()); // 水表待审
        JsonNode row = data.get("rows").get(0);
        org.junit.jupiter.api.Assertions.assertEquals(1, row.get("type").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(200.0, row.get("usage").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(100.0, row.get("fee").asDouble(), 0.001); // 200*0.5
    }

    @Test
    void unpricedTypeReturnsNullFee() throws Exception {
        setup("13840000002");
        // 不填任何费率
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-07\"}");
        long wr = submit(p, waterMeterId, "80");
        approve(wr);

        String resp = mvc.perform(get("/api/v1/reports/summary?periodId=" + p)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        JsonNode row = om.readTree(resp).get("data").get("rows").get(0);
        // 水价未定 → fee 为 null
        org.junit.jupiter.api.Assertions.assertTrue(row.get("fee").isNull());
        org.junit.jupiter.api.Assertions.assertEquals(80.0, row.get("usage").asDouble(), 0.001);
    }

    @Test
    void dunningAggregatesByCompany() throws Exception {
        setup("13840000003");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-08\",\"elecPrice\":0.5,\"waterPrice\":2}");
        approve(submit(p, elecMeterId, "300"));   // 电 200 * 0.5 = 100
        approve(submit(p, waterMeterId, "80"));   // 水 80 * 2 = 160

        String resp = mvc.perform(get("/api/v1/dunning/" + p)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        JsonNode row = om.readTree(resp).get("data").get("rows").get(0);
        // 合计 = 100 + 160 = 260
        org.junit.jupiter.api.Assertions.assertEquals(260.0, row.get("totalFee").asDouble(), 0.001);
    }
}
