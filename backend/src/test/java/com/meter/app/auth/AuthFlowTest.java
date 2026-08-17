package com.meter.app.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * auth 模块端到端:注册 → 登录 → 带 token 访问 → 无 token 被拒 → 重复注册冲突。
 * 用 H2 内存库真实跑通 HTTP + JPA + JWT + Security 全链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void fullAuthFlow() throws Exception {
        // 1. 注册
        String registerBody = """
                {"phone":"13800138000","password":"abc12345","orgName":"测试园区"}
                """;
        String regResp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = om.readTree(regResp).get("data");
        long orgId = data.get("orgId").asLong();

        // 2. 登录
        String loginBody = """
                {"phone":"13800138000","password":"abc12345"}
                """;
        String loginResp = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andReturn().getResponse().getContentAsString();

        String accessToken = om.readTree(loginResp).get("data").get("accessToken").asText();

        // 3. 带 token 访问受保护接口 /me
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.orgId", is((int) orgId)));

        // 4. 无 token 访问受保护接口 → 401
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // 5. 重复注册 → 冲突错误码 3002
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(jsonPath("$.code", is(3002)));
    }

    @Test
    void wrongPasswordRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900139000","password":"rightpass1","orgName":"园区B"}
                                """))
                .andExpect(status().isOk());

        // 错误密码 → 2001
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900139000","password":"wrongpass1"}
                                """))
                .andExpect(jsonPath("$.code", is(2001)));
    }
}
