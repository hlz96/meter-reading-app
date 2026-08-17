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
 * 待抄清单(E)、批量提交(F)、修正读数(G)端到端。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadingExtraFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;
    private long meter1;
    private long meter2;

    private void setup(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = om.readTree(resp).get("data").get("accessToken").asText();
        companyId = postForId("/api/v1/companies", "{\"name\":\"A公司\"}");
        meter1 = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"表1\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
        meter2 = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"表2\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    @Test
    void periodTasksMarksDone() throws Exception {
        setup("13940000001");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.6}");
        // 只抄 meter1
        postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":50}".formatted(meter1, p));

        String resp = mvc.perform(get("/api/v1/periods/" + p + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.total", is(2)))
                .andExpect(jsonPath("$.data.doneCount", is(1)))
                .andReturn().getResponse().getContentAsString();
        JsonNode items = om.readTree(resp).get("data").get("items");
        org.junit.jupiter.api.Assertions.assertEquals(2, items.size());
    }

    @Test
    void batchSubmitPartialFailure() throws Exception {
        setup("13940000002");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-07\",\"elecPrice\":0.6}");
        // 两条好的 + 一条坏的(表计不存在)
        String body = """
                {"items":[
                  {"meterId":%d,"periodId":%d,"currReading":10},
                  {"meterId":%d,"periodId":%d,"currReading":20},
                  {"meterId":999999,"periodId":%d,"currReading":30}
                ]}""".formatted(meter1, p, meter2, p, p);

        mvc.perform(post("/api/v1/readings/batch").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.successCount", is(2)))
                .andExpect(jsonPath("$.data.failCount", is(1)))
                .andExpect(jsonPath("$.data.errors", hasSize(1)));
    }

    @Test
    void updateReadingRecomputesAndResetsAudit() throws Exception {
        setup("13940000003");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-08\",\"elecPrice\":0.6}");
        long rid = postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":50}".formatted(meter1, p));
        // 审核通过
        mvc.perform(post("/api/v1/readings/" + rid + "/audit").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"));

        // 修正读数 → 用量重算、审核状态回到待审核(1)
        String resp = mvc.perform(put("/api/v1/readings/" + rid).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":80}".formatted(meter1, p)))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.auditStatus", is(1)))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(resp).get("data");
        // 用量重算 =(80-0)×1 = 80
        org.junit.jupiter.api.Assertions.assertEquals(80.0, data.get("currReading").asDouble(), 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(80.0, data.get("usageAmount").asDouble(), 0.001);
    }

    @Test
    void updateRejectedOnSettledPeriod() throws Exception {
        setup("13940000004");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-09\",\"elecPrice\":0.6}");
        long rid = postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":50}".formatted(meter1, p));
        mvc.perform(post("/api/v1/readings/" + rid + "/audit").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"));
        // 表2 也抄了并通过,才能结算(否则待审阻止);简单起见把 meter2 也处理
        long rid2 = postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":10}".formatted(meter2, p));
        mvc.perform(post("/api/v1/readings/" + rid2 + "/audit").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"));
        // 结算
        mvc.perform(post("/api/v1/periods/" + p + "/settle").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status", is(2)));

        // 已结算周期不能改读数
        mvc.perform(put("/api/v1/readings/" + rid).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterId\":%d,\"periodId\":%d,\"currReading\":90}".formatted(meter1, p)))
                .andExpect(jsonPath("$.code", is(3002)));
    }
}
