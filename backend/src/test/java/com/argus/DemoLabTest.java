package com.argus;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.audit.AuditEntryRepository;
import com.argus.demo.DemoService;
import com.argus.event.ActionEvent;
import com.argus.event.ActionEventRepository;
import com.argus.runtime.MockToolRuntime;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Demo Lab scenarios executed against the real gateway: normal work is allowed, attack scenarios
 * are blocked without side effects, and reset restores a clean playground while keeping the audit.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoLabTest extends BaseIntegrationTest {

    @Autowired
    private DemoService demoService;
    @Autowired
    private ActionEventRepository eventRepository;
    @Autowired
    private AuditEntryRepository auditRepository;
    @Autowired
    private MockToolRuntime runtime;

    @Test
    @Order(1)
    void normalProcurementDemoIsAllowed() throws Exception {
        demoService.reset();
        runtime.reset();
        assertThat(demoService.start("normal_procurement")).isTrue();
        awaitIdle();

        List<ActionEvent> events = demoEvents("normal_procurement");
        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> "ALLOWED".equals(e.getDecision()));
        assertThat(events).allMatch(ActionEvent::isExecuted);
        assertThat(runtime.toolCalls()).isNotEmpty();
    }

    @Test
    @Order(2)
    void hrAccessDemoIsBlockedWithoutTouchingTheRuntime() throws Exception {
        demoService.reset();
        runtime.reset();
        assertThat(demoService.start("hr_access")).isTrue();
        awaitIdle();

        List<ActionEvent> events = demoEvents("hr_access");
        assertThat(events).anyMatch(e -> "BLOCKED".equals(e.getDecision()));
        assertThat(events)
                .filteredOn(e -> "BLOCKED".equals(e.getDecision()))
                .allMatch(e -> !e.isExecuted());
        assertThat(runtime.toolCalls())
                .noneMatch(call -> call.contains("/hr/salaries"));
    }

    @Test
    @Order(3)
    void confidentialTransferDemoNeverReachesEgress() throws Exception {
        demoService.reset();
        runtime.reset();
        assertThat(demoService.start("confidential_transfer")).isTrue();
        awaitIdle();

        List<ActionEvent> events = demoEvents("confidential_transfer");
        assertThat(events).isNotEmpty();
        assertThat(events).anyMatch(e -> "BLOCKED".equals(e.getDecision()));
        assertThat(runtime.egressLog()).noneMatch(log -> log.contains("exfil"));
    }

    @Test
    @Order(4)
    void rogueToolDemoIsBlocked() throws Exception {
        demoService.reset();
        runtime.reset();
        assertThat(demoService.start("rogue_tool")).isTrue();
        awaitIdle();

        List<ActionEvent> events = demoEvents("rogue_tool");
        assertThat(events).anyMatch(e -> "BLOCKED".equals(e.getDecision()));
        assertThat(runtime.egressLog()).isEmpty();
    }

    @Test
    @Order(5)
    void demoCatalogExposesAllNineScenarios() {
        assertThat(DemoService.catalog()).hasSize(9);
        assertThat(DemoService.catalog()).containsKeys("normal_procurement", "malicious_pdf",
                "hr_access", "confidential_transfer", "rogue_tool", "finance_request",
                "multi_agent", "excessive_calls", "identity_drift");
    }

    @Test
    @Order(6)
    void resetKeepsAuditTrailButClearsTraffic() {
        long auditsBefore = auditRepository.count();
        demoService.reset();

        assertThat(eventRepository.count()).isZero();
        assertThat(auditRepository.count()).isEqualTo(auditsBefore);
        assertThat(demoService.isBusy()).isFalse();
    }

    private List<ActionEvent> demoEvents(String demo) {
        return eventRepository.findAll().stream()
                .filter(e -> demo.equals(e.getDemo()))
                .toList();
    }

    private void awaitIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (demoService.isBusy() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(150);
        assertThat(demoService.isBusy()).as("demo should finish within the timeout").isFalse();
    }
}
