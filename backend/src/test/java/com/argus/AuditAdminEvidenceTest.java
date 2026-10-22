package com.argus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.argus.audit.AuditEntry;
import com.argus.audit.AuditEntryRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * Governance guarantees: the append-only audit ledger, its search/export APIs, and validated
 * evidence upload (allow-listed extensions, content sanity checks, RBAC).
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditAdminEvidenceTest extends BaseIntegrationTest {

    @Autowired
    private AuditEntryRepository auditRepository;

    @Test
    @Order(1)
    @Transactional
    void auditEntriesAreAppendOnly() {
        AuditEntry entry = new AuditEntry();
        entry.setActor("test");
        entry.setKind("test");
        entry.setDescription("original");
        entry.setDecision("ALLOWED");
        AuditEntry saved = auditRepository.save(entry);

        saved.setDescription("tampered");
        assertThatThrownBy(() -> auditRepository.saveAndFlush(saved))
                .hasMessageContaining("append-only");
    }

    @Test
    @Order(2)
    void auditSearchReturnsEntriesWithPagination() throws Exception {
        String adminToken = login(ADMIN_USER, ADMIN_PASS);
        mvc.perform(get("/api/audit")
                        .param("size", "5")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @Order(3)
    void auditCsvExportIsAnalystOnly() throws Exception {
        String analystToken = login("analyst", ANALYST_PASS);
        mvc.perform(get("/api/audit/export")
                        .param("format", "csv")
                        .header("Authorization", bearer(analystToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("timestamp,actor")));

        String viewerToken = login("viewer", VIEWER_PASS);
        mvc.perform(get("/api/audit/export")
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void evidenceUploadIsValidatedHashedAndListed() throws Exception {
        String analystToken = login("analyst", ANALYST_PASS);

        byte[] goodJson = "{\"ioc\":\"203.0.113.7\",\"note\":\"suspicious egress target\"}"
                .getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/evidence")
                        .file(new MockMultipartFile("file", "evidence.json",
                                "application/json", goodJson))
                        .header("Authorization", bearer(analystToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("evidence.json"))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.kind").value("JSON"));

        mvc.perform(get("/api/evidence")
                        .header("Authorization", bearer(analystToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.originalName == 'evidence.json')]").exists());
    }

    @Test
    @Order(5)
    void evidenceUploadRejectsDisallowedTypesAndHostileContent() throws Exception {
        String analystToken = login("analyst", ANALYST_PASS);

        mvc.perform(multipart("/api/evidence")
                        .file(new MockMultipartFile("file", "malware.exe",
                                "application/octet-stream", new byte[] {0x4d, 0x5a, 0x00}))
                        .header("Authorization", bearer(analystToken)))
                .andExpect(status().isBadRequest());

        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/evidence")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", script))
                        .header("Authorization", bearer(analystToken)))
                .andExpect(status().isBadRequest());

        byte[] badJson = "{not-json".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/evidence")
                        .file(new MockMultipartFile("file", "broken.json",
                                "application/json", badJson))
                        .header("Authorization", bearer(analystToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void viewerCannotUploadEvidence() throws Exception {
        String viewerToken = login("viewer", VIEWER_PASS);
        mvc.perform(multipart("/api/evidence")
                        .file(new MockMultipartFile("file", "x.txt", "text/plain",
                                "hello".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void adminUserLifecycleEndToEnd() throws Exception {
        String adminToken = login(ADMIN_USER, ADMIN_PASS);

        var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "temp.analyst", "password", "TempAnalyst!2026",
                                "displayName", "Temp Analyst",
                                "roles", java.util.List.of("ANALYST")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("temp.analyst"))
                .andReturn();

        String tempToken = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();
        assertThat(tempToken).isNotBlank();

        String analystToken = login("temp.analyst", "TempAnalyst!2026");
        mvc.perform(get("/api/evidence").header("Authorization", bearer(analystToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/users").header("Authorization", bearer(analystToken)))
                .andExpect(status().isForbidden());
    }
}
