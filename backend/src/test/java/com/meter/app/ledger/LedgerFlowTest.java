package com.meter.app.ledger;

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
 * ledger 模块端到端:公司/表计 CRUD + 筛选 + 跨组织隔离。
 * 用 H2 真实跑通 HTTP + JPA + orgId 数据隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    /** 注册一个组织,返回其 accessToken。 */
    private String registerAndToken(String phone, String orgName) throws Exception {
        String body = """
                {"phone":"%s","password":"abc12345","orgName":"%s"}
                """.formatted(phone, orgName);
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("accessToken").asText();
    }

    private long createCompany(String token, String name) throws Exception {
        String resp = mvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("id").asLong();
    }

    @Test
    void companyAndMeterCrud() throws Exception {
        String token = registerAndToken("13800138001", "园区A");

        // 建公司
        long companyId = createCompany(token, "A公司");

        // 建电表
        String meterBody = """
                {"companyId":%d,"name":"1号电表","type":1,"initialReading":100.5,"ratio":1}
                """.formatted(companyId);
        String meterResp = mvc.perform(post("/api/v1/meters")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(meterBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type", is(1)))
                .andExpect(jsonPath("$.data.status", is(1)))
                .andReturn().getResponse().getContentAsString();
        long meterId = om.readTree(meterResp).get("data").get("id").asLong();

        // 建水表
        mvc.perform(post("/api/v1/meters")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"companyId\":%d,\"name\":\"1号水表\",\"type\":2," +
                                "\"initialReading\":0,\"ratio\":1}").formatted(companyId)))
                .andExpect(status().isOk());

        // 列表:该组织应有 2 块表
        mvc.perform(get("/api/v1/meters").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(2)));

        // 按类型筛选:电表只 1 块
        mvc.perform(get("/api/v1/meters?type=1").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("1号电表")));

        // 改表计
        mvc.perform(put("/api/v1/meters/" + meterId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"companyId\":%d,\"name\":\"1号电表改\",\"type\":1," +
                                "\"initialReading\":100.5,\"ratio\":2,\"status\":0}").formatted(companyId)))
                .andExpect(jsonPath("$.data.name", is("1号电表改")))
                .andExpect(jsonPath("$.data.ratio", is(2)))
                .andExpect(jsonPath("$.data.status", is(0)));

        // 有表计时删公司应被拒(3002)
        mvc.perform(delete("/api/v1/companies/" + companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code", is(3002)));
    }

    @Test
    void crossOrgIsolation() throws Exception {
        String tokenA = registerAndToken("13800138002", "园区A2");
        String tokenB = registerAndToken("13800138003", "园区B2");

        long companyA = createCompany(tokenA, "A公司");

        // B 组织看不到 A 的公司
        mvc.perform(get("/api/v1/companies").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // B 组织不能改 A 的公司 → 404 NOT_FOUND(3001)
        mvc.perform(put("/api/v1/companies/" + companyA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"恶意改名\"}"))
                .andExpect(jsonPath("$.code", is(3001)));

        // B 组织不能把表计挂到 A 的公司上 → 参数错误(1001)
        mvc.perform(post("/api/v1/meters")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"companyId\":%d,\"name\":\"越权表\",\"type\":1," +
                                "\"initialReading\":0,\"ratio\":1}").formatted(companyA)))
                .andExpect(jsonPath("$.code", is(1001)));
    }
}
