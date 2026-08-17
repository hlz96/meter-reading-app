package com.meter.app.dataimport;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 可配置导入(Gap9)端到端:建模板→上传 Excel→读数落库为待审;坏行计入失败明细。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String token;
    private long companyId;

    private void setup(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = om.readTree(resp).get("data").get("accessToken").asText();
        companyId = postForId("/api/v1/companies", "{\"name\":\"A公司\"}");
        // 建两个表:电表1、电表2
        postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"电表1\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
        postForId("/api/v1/meters",
                "{\"companyId\":%d,\"name\":\"电表2\",\"type\":1,\"initialReading\":0,\"ratio\":1}".formatted(companyId));
    }

    private long postForId(String url, String body) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    private long createTemplate() throws Exception {
        // Excel 列名 → 系统字段
        String body = "{\"name\":\"默认模板\",\"fieldMapping\":{\"表名\":\"meterName\",\"本期读数\":\"currReading\"}}";
        return postForId("/api/v1/import/templates", body);
    }

    /** 用 EasyExcel 生成一个含表头的 xlsx 字节。rows 每项 = {表名, 读数}。 */
    private byte[] buildExcel(List<String[]> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("表名"));
        head.add(List.of("本期读数"));
        List<List<String>> data = new ArrayList<>();
        for (String[] r : rows) {
            List<String> line = new ArrayList<>();
            line.add(r[0]);
            line.add(r[1]);
            data.add(line);
        }
        EasyExcel.write(bos).head(head).sheet("读数").doWrite(data);
        return bos.toByteArray();
    }

    @Test
    void importValidRowsCreatesPendingReadings() throws Exception {
        setup("13870000001");
        long templateId = createTemplate();
        long period = postForId("/api/v1/periods", "{\"name\":\"2026-06\",\"elecPrice\":0.6}");

        byte[] xlsx = buildExcel(List.of(
                new String[]{"电表1", "100"},
                new String[]{"电表2", "200"}));
        MockMultipartFile file = new MockMultipartFile("file", "readings.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mvc.perform(multipart("/api/v1/import/readings")
                        .file(file)
                        .param("templateId", String.valueOf(templateId))
                        .param("periodId", String.valueOf(period))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.successCount", is(2)))
                .andExpect(jsonPath("$.data.failCount", is(0)));

        // 读数已落库,且为待审核(auditStatus=1)
        mvc.perform(get("/api/v1/readings?periodId=" + period)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].auditStatus", is(1)));
    }

    @Test
    void importReportsBadRows() throws Exception {
        setup("13870000002");
        long templateId = createTemplate();
        long period = postForId("/api/v1/periods", "{\"name\":\"2026-07\",\"elecPrice\":0.6}");

        // 第二行表名不存在 → 该行失败
        byte[] xlsx = buildExcel(List.of(
                new String[]{"电表1", "100"},
                new String[]{"不存在的表", "200"}));
        MockMultipartFile file = new MockMultipartFile("file", "readings.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mvc.perform(multipart("/api/v1/import/readings")
                        .file(file)
                        .param("templateId", String.valueOf(templateId))
                        .param("periodId", String.valueOf(period))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.successCount", is(1)))
                .andExpect(jsonPath("$.data.failCount", is(1)))
                .andExpect(jsonPath("$.data.errors", hasSize(1)));
    }
}
