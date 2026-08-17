package com.meter.app.export;

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
 * 导出(Gap C/D/H)端到端:断言返回 xlsx content-type 与非空字节。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;
    private long meterId;

    private void setup(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = om.readTree(resp).get("data").get("accessToken").asText();
        companyId = postForId("/api/v1/companies", "{\"name\":\"A公司\"}");
        meterId = postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"电表\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    void exportMeters() throws Exception {
        setup("13930000001");
        byte[] bytes = mvc.perform(get("/api/v1/export/meters").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(XLSX)))
                .andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(bytes.length > 0);
    }

    @Test
    void exportReadings() throws Exception {
        setup("13930000002");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.6}");
        postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":100}".formatted(meterId, p));

        byte[] bytes = mvc.perform(get("/api/v1/export/readings?periodId=" + p)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(XLSX)))
                .andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(bytes.length > 0);
    }

    @Test
    void exportDunning() throws Exception {
        setup("13930000003");
        long p = postForId("/api/v1/periods", "{\"name\":\"2026-07\",\"elecPrice\":0.6}");
        long rid = postForId("/api/v1/readings", "{\"meterId\":%d,\"periodId\":%d,\"currReading\":100}".formatted(meterId, p));
        // 审核通过后催缴单才有数据
        mvc.perform(post("/api/v1/readings/" + rid + "/audit").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"));

        byte[] bytes = mvc.perform(get("/api/v1/dunning/" + p + "/company/" + companyId + "/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(XLSX)))
                .andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(bytes.length > 0);
    }

    @Test
    void importSample() throws Exception {
        setup("13930000004");
        long templateId = postForId("/api/v1/import/templates",
                "{\"name\":\"默认\",\"fieldMapping\":{\"表名\":\"meterName\",\"本期读数\":\"currReading\"}}");
        byte[] bytes = mvc.perform(get("/api/v1/import/templates/" + templateId + "/sample")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(XLSX)))
                .andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(bytes.length > 0);
    }
}
