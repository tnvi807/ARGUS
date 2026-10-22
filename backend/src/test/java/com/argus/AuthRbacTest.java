package com.argus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Authentication and RBAC guarantees: generic failures, lockout, token revocation and the
 * least-privilege permission matrix across the three seeded roles.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRbacTest extends BaseIntegrationTest {

    @Test
    @Order(1)
    void adminLoginReturnsTokenRolesAndPermissions() throws Exception {
        String token = login(ADMIN_USER, ADMIN_PASS);
        assertThat(token).isNotBlank();

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions[?(@ == 'admin.manage')]").exists());
    }

    @Test
    @Order(2)
    void wrongPasswordIsGenericUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "admin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @Order(3)
    void unknownUserYieldsSameMessageAsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "ghost-user-xyz", "password", "whatever12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @Order(4)
    void accountLocksAfterFiveFailedAttempts() throws Exception {
        String adminToken = login(ADMIN_USER, ADMIN_PASS);
        mvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "lock.me", "password", "LockMe!Pass2026",
                                "displayName", "Lock Target", "roles", java.util.List.of("VIEWER")))))
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("username", "lock.me", "password", "bad-password-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "lock.me", "password", "LockMe!Pass2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Account temporarily locked after repeated failures"));
    }

    @Test
    @Order(5)
    void viewerCannotReachAdminApis() throws Exception {
        String viewerToken = login("viewer", VIEWER_PASS);
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void analystCannotReachAdminApis() throws Exception {
        String analystToken = login("analyst", ANALYST_PASS);
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(analystToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void viewerCannotSubmitActions() throws Exception {
        String viewerToken = login("viewer", VIEWER_PASS);
        mvc.perform(post("/api/actions")
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "agentId", "fin", "kind", "read", "category", "invoices"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void analystCannotApproveHeldActions() throws Exception {
        String analystToken = login("analyst", ANALYST_PASS);
        mvc.perform(post("/api/approvals/1/decision")
                        .header("Authorization", bearer(analystToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void viewerCanReadDashboardButNotAct() throws Exception {
        String viewerToken = login("viewer", VIEWER_PASS);
        mvc.perform(get("/api/dashboard").header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/agents/procure/status")
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void logoutRevokesOutstandingTokens() throws Exception {
        String token = login(ADMIN_USER, ADMIN_PASS);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    void weakPasswordAndDuplicateUsersAreRejected() throws Exception {
        String adminToken = login(ADMIN_USER, ADMIN_PASS);

        mvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "weak.pass", "password", "short",
                                "roles", java.util.List.of("VIEWER")))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "analyst", "password", "Another!Pass2026",
                                "roles", java.util.List.of("VIEWER")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    void adminCannotStripOwnAdminRole() throws Exception {
        String adminToken = login(ADMIN_USER, ADMIN_PASS);
        JsonNode users = objectMapper.readTree(mvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long adminId = -1;
        for (JsonNode user : users) {
            if ("admin".equals(user.get("username").asText())) {
                adminId = user.get("id").asLong();
            }
        }
        assertThat(adminId).isPositive();

        mvc.perform(put("/api/admin/users/" + adminId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ANALYST\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    void analystCanSubmitActionsThroughTheGateway() throws Exception {
        String analystToken = login("analyst", ANALYST_PASS);
        mvc.perform(post("/api/actions")
                        .header("Authorization", bearer(analystToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "agentId", "fin", "kind", "read", "tool", "ledger_api",
                                "category", "invoices", "target", "/invoices/pending-queue"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOWED"))
                .andExpect(jsonPath("$.executed").value(true));
    }

    @Test
    @Order(14)
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
