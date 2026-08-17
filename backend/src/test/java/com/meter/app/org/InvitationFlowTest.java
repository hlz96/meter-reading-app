package com.meter.app.org;

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
 * 邀请码流程(Gap B):管理员生成一次性邀请码,他人凭码加入;重复/非法码被拒。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvitationFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String register(String phone) throws Exception {
        String resp = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"abc12345\",\"orgName\":\"园区%s\"}"
                                .formatted(phone, phone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("accessToken").asText();
    }

    private String invite(String adminToken, String role) throws Exception {
        String resp = mvc.perform(post("/api/v1/members/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\"}"))
                .andExpect(jsonPath("$.code", is(0)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("data").get("code").asText();
    }

    @Test
    void adminInvitesAndUserJoins() throws Exception {
        String adminToken = register("13920000001");
        String code = invite(adminToken, "READER");

        String bToken = register("13920000002");
        mvc.perform(post("/api/v1/members/join")
                        .header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.role", is("READER")));

        // A 的成员列表现在能看到 2 人(A 自己 + B)
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void reusedCodeRejected() throws Exception {
        String adminToken = register("13920000003");
        String code = invite(adminToken, "READER");

        String bToken = register("13920000004");
        mvc.perform(post("/api/v1/members/join").header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.code", is(0)));

        // 同码第二次(另一人)→ 3003 已使用
        String cToken = register("13920000005");
        mvc.perform(post("/api/v1/members/join").header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.code", is(3003)));
    }

    @Test
    void invalidCodeRejected() throws Exception {
        String token = register("13920000006");
        mvc.perform(post("/api/v1/members/join").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"nonexistent\"}"))
                .andExpect(jsonPath("$.code", is(3003)));
    }

    @Test
    void unauthenticatedCannotInvite() throws Exception {
        mvc.perform(post("/api/v1/members/invite")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"READER\"}"))
                .andExpect(status().isUnauthorized());
    }
}
