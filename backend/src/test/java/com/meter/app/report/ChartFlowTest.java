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
 * 图表(Gap I)端到端:trend / ratio / elec-water 三维度,只算已通过读数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChartFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;
    private long elecMeter;
    private long waterMeter;

    private void setup(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = om.readTree(resp).get("data").get("accessToken").asText();
        companyId = postForId("/api/v1/companies", "{\"name\":\"A公司\"}");
        elecMeter = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"电表\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
        waterMeter = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"水表\",\"type\":2,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    /** 抄表并审核通过。 */
    private void submitApproved(long meterId, long periodId, String curr) throws Exception {
        long rid = postForId("/api/v1/readings",
                "{\"meterId\":%d,\"periodId\":%d,\"currReading\":%s}".formatted(meterId, periodId, curr));
        mvc.perform(post("/api/v1/readings/" + rid + "/audit").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(jsonPath("$.data.auditStatus", is(2)));
    }

    @Test
    void trendAcrossPeriods() throws Exception {
        setup("13950000001");
        long p1 = postForId("/api/v1/periods", "{\"name\":\"2026-01\",\"startDate\":\"2026-01-01\",\"elecPrice\":0.6}");
        submitApproved(elecMeter, p1, "100");
        long p2 = postForId("/api/v1/periods", "{\"name\":\"2026-02\",\"startDate\":\"2026-02-01\",\"elecPrice\":0.6}");
        submitApproved(elecMeter, p2, "250");

        String resp = mvc.perform(get("/api/v1/reports/charts?type=trend&companyId=" + companyId + "&meterType=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.type", is("trend")))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(resp).get("data").get("data");
        // 两个周期两个点
        org.junit.jupiter.api.Assertions.assertEquals(2, data.size());
    }

    @Test
    void ratioPercentsSumTo100() throws Exception {
        setup("13950000002");
        long company2 = postForId("/api/v1/companies", "{\"name\":\"B公司\"}");
        long meterB = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"B电表\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(company2));
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.6}");
        submitApproved(elecMeter, p, "100");  // A 用量 100
        submitApproved(meterB, p, "300");      // B 用量 300

        String resp = mvc.perform(get("/api/v1/reports/charts?type=ratio&periodId=" + p)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.type", is("ratio")))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(resp).get("data").get("data");
        org.junit.jupiter.api.Assertions.assertEquals(2, data.size());
        double sum = 0;
        for (JsonNode slice : data) sum += slice.get("percent").asDouble();
        org.junit.jupiter.api.Assertions.assertEquals(100.0, sum, 0.5);
    }

    @Test
    void elecWaterSplit() throws Exception {
        setup("13950000003");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-07\",\"elecPrice\":0.6,\"waterPrice\":2}");
        submitApproved(elecMeter, p, "200");   // 电 200
        submitApproved(waterMeter, p, "80");   // 水 80

        String resp = mvc.perform(get("/api/v1/reports/charts?type=elec-water&periodId=" + p)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.type", is("elec-water")))
                .andReturn().getResponse().getContentAsString();
        JsonNode row = om.readTree(resp).get("data").get("data").get(0);
        org.junit.jupiter.api.Assertions.assertEquals(200.0, row.get("elecUsage").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(80.0, row.get("waterUsage").asDouble(), 0.001);
    }

    @Test
    void unpaidReadingsExcludedFromChart() throws Exception {
        setup("13950000004");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-08\",\"elecPrice\":0.6}");
        // 提交但不审核
        postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":100}".formatted(elecMeter, p));

        String resp = mvc.perform(get("/api/v1/reports/charts?type=elec-water&periodId=" + p)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        // 未审通过 → 无数据
        org.junit.jupiter.api.Assertions.assertEquals(0, om.readTree(resp).get("data").get("data").size());
    }
}
