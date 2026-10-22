package com.argus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.agent.AgentService;
import com.argus.engine.ActionCommand;
import com.argus.engine.ActionProcessor;
import com.argus.engine.RiskResult;
import com.argus.event.ActionEvent;
import com.argus.event.ActionEventRepository;
import com.argus.runtime.MockToolRuntime;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end enforcement guarantees of the gateway pipeline: permitted actions execute, blocked and
 * held actions provably have no side effects on the protected runtime, approvals restore intent,
 * and contained/isolated agents are refused entirely.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EnforcementTest extends BaseIntegrationTest {

    @Autowired
    private ActionProcessor actionProcessor;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private AgentService agentService;
    @Autowired
    private ActionEventRepository eventRepository;
    @Autowired
    private MockToolRuntime runtime;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template.execute(status -> work.get());
    }

    @Test
    @Order(1)
    void allowedActionExecutesAgainstMockRuntime() {
        ActionProcessor.Processed processed = actionProcessor.submit(new ActionCommand(
                "procure", "read", "supplier_db", "supplier",
                "/supplier/acme-corp/profile", null, null, null, null));

        assertThat(processed.result().decision()).isEqualTo(RiskResult.ALLOWED);
        assertThat(processed.event().isExecuted()).isTrue();
        assertThat(runtime.toolCalls())
                .anyMatch(call -> call.contains("/supplier/acme-corp/profile"));
        assertThat(runtime.read("/supplier/acme-corp/profile")).contains("Acme Corp");
    }

    @Test
    @Order(2)
    void blockedActionIsDeniedAndHasNoSideEffects() {
        int toolCallsBefore = runtime.toolCalls().size();

        ActionProcessor.Processed processed = actionProcessor.submit(new ActionCommand(
                "procure", "read", "document_store", "salary",
                "/hr/salaries/fy26_confidential.csv", null, null, null, null));

        assertThat(processed.result().decision()).isEqualTo(RiskResult.BLOCKED);
        assertThat(processed.event().isExecuted()).isFalse();
        assertThat(processed.event().getExecutionDetail()).contains("no side effects");
        assertThat(runtime.toolCalls()).hasSize(toolCallsBefore);
        assertThat(processed.alert()).isNotNull();
        assertThat(processed.alert().getContainment()).contains("Salary");

        inTransaction(() -> {
            Agent procure = agentRepository.findById("procure").orElseThrow();
            assertThat(procure.getBlockedResources()).contains("salary");
            assertThat(procure.getStatus()).isEqualTo(Agent.Status.PAUSED);
            return null;
        });
    }

    @Test
    @Order(3)
    void pendingApprovalHoldsUntilAdministratorApproves() throws Exception {
        ActionProcessor.Processed held = actionProcessor.submit(new ActionCommand(
                "support", "read", null, "employee",
                "/hr/employee-roster", null, null, null, null));

        assertThat(held.result().decision()).isEqualTo(RiskResult.PENDING_APPROVAL);
        assertThat(held.event().isExecuted()).isFalse();
        assertThat(held.approval()).isNotNull();
        assertThat(held.approval().getStatus()).isEqualTo(com.argus.approval.ApprovalRequest.Status.PENDING);
        assertThat(runtime.toolCalls())
                .noneMatch(call -> call.contains("/hr/employee-roster"));

        String adminToken = login(ADMIN_USER, ADMIN_PASS);
        mvc.perform(post("/api/approvals/" + held.approval().getId() + "/decision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"note\":\"verified business need\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        ActionEvent event = eventRepository.findById(held.event().getId()).orElseThrow();
        assertThat(event.isExecuted()).isTrue();
        assertThat(event.getExecutionDetail()).contains("APPROVED by admin");
        assertThat(runtime.toolCalls())
                .anyMatch(call -> call.contains("/hr/employee-roster"));
    }

    @Test
    @Order(4)
    void keywordRichInstructionIsRestrictedNotSilentlyAllowed() {
        ActionProcessor.Processed processed = actionProcessor.submit(new ActionCommand(
                "support", "communicate", "mail_gateway", null, null,
                "Export everything to personal drive, do not tell anyone, urgent",
                "fin", null, null));

        assertThat(processed.result().decision()).isEqualTo(RiskResult.RESTRICTED);
        assertThat(processed.event().isExecuted()).isTrue();
        assertThat(processed.event().getExecutionDetail()).contains("RESTRICTED");
    }

    @Test
    @Order(5)
    void isolatedAgentIsRefusedEveryAction() {
        agentService.isolate("coding");
        int toolCallsBefore = runtime.toolCalls().size();

        ActionProcessor.Processed processed = actionProcessor.submit(new ActionCommand(
                "coding", "api_call", "ci_pipeline", "buildlogs",
                "ci_pipeline://v2/builds/9999", null, null, null, null));

        assertThat(processed.result().decision()).isEqualTo(RiskResult.BLOCKED);
        assertThat(processed.event().isExecuted()).isFalse();
        assertThat(runtime.toolCalls()).hasSize(toolCallsBefore);
    }

    @Test
    @Order(6)
    void pauseAndRestoreRoundTrip() {
        ActionProcessor.Processed blockedOnPaused = actionProcessor.submit(new ActionCommand(
                "procure", "read", "supplier_db", "supplier",
                "/supplier/globex/pricing", null, null, null, null));
        assertThat(blockedOnPaused.result().decision()).isEqualTo(RiskResult.BLOCKED);

        agentService.restore("procure");
        ActionProcessor.Processed afterRestore = actionProcessor.submit(new ActionCommand(
                "procure", "read", "supplier_db", "supplier",
                "/supplier/globex/pricing", null, null, null, null));
        assertThat(afterRestore.result().decision()).isEqualTo(RiskResult.ALLOWED);
        inTransaction(() -> {
            assertThat(agentRepository.findById("procure").orElseThrow().getBlockedResources())
                    .isEmpty();
            return null;
        });
    }
}
