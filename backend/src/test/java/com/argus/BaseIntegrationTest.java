package com.argus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared scaffolding for full-stack integration tests: boots the complete application against an
 * in-memory H2 database (Flyway-managed) with seeded users/agents and provides login helpers.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASS = "AdminTest!2026";
    protected static final String ANALYST_PASS = "AnalystTest!2026";
    protected static final String VIEWER_PASS = "ViewerTest!2026";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
