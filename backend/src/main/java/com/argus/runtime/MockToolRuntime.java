package com.argus.runtime;

import com.argus.agent.Agent;
import com.argus.catalog.DataCatalog;
import com.argus.engine.ActionCommand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Protected mock tools &amp; resources. The gateway is the only way to reach them: actions that are
 * {@code PENDING_APPROVAL} or {@code BLOCKED} never touch this store, so blocked actions provably
 * have no side effects. Everything is in-memory mock data - no external service is ever contacted
 * and no real confidential data leaves the process.
 */
@Component
public class MockToolRuntime {

    public enum Mode {
        FULL, REDACTED
    }

    public record ExecOutcome(boolean executed, String detail, String output) {
        static ExecOutcome denied(String decision, String kind) {
            return new ExecOutcome(false,
                    "DENIED (" + decision + "): " + kind + " was not executed - no side effects applied",
                    "");
        }
    }

    private final Map<String, String> files = new LinkedHashMap<>();
    private final List<String> egressLog = new ArrayList<>();
    private final List<String> toolCalls = new ArrayList<>();

    public MockToolRuntime() {
        seed();
    }

    private void seed() {
        files.put("/hr/salaries/fy26_confidential.csv",
                "CONFIDENTIAL,employee,base_bonus\nE-1001,245000,80000\nE-1002,231000,64000");
        files.put("/hr/employee-directory.json", "[{id:E-1001,name:Placeholder Employee}]");
        files.put("/supplier/acme-corp/profile", "Acme Corp - tier 1 supplier - payment terms Net 45");
        files.put("/supplier/globex/pricing", "Globex pricing sheet 2026 - unit costs");
        files.put("/invoices/pending-queue", "INV-8841 EUR 12,400 pending approval");
        files.put("/invoices/approved-2026", "INV-8801 paid, INV-8802 paid");
        files.put("/payments/runs/draft", "PAY-RUN-44 draft: 128 transfers");
        files.put("/po/open-orders", "PO-991 open, PO-992 open");
        files.put("/tickets/batch-open", "T-4411 refund question, T-4412 shipping delay");
        files.put("/inbox/thread-4471", "Customer asking about invoice 8841");
        files.put("/customers/golden_record.csv", "PII,email,phone\nalice@example.com,+15550100");
        files.put("/metrics/q4-revenue", "Q4 revenue: 4.2M (aggregated, no PII)");
        files.put("/metrics/nps-daily", "NPS 41 (aggregate)");
        files.put("/ci/builds/8841/log", "build 8841 OK in 3m12s");
        files.put("/staging/feature-checkout/vars", "STRIPE_KEY=sk_mock_staging_only");
        files.put("/src/payments/ledger.py", "def post(entry): ...");
        files.put("/secrets/prod.env", "DATABASE_PASSWORD=super-secret-value SESSION_SIGNING_KEY=abc123");
        files.put("/leaves/requests-open", "LEAVE-112 approved, LEAVE-113 pending");
    }

    public synchronized ExecOutcome execute(Agent agent, ActionCommand cmd, Mode mode) {
        String kind = cmd.kind() == null ? "" : cmd.kind();
        String target = cmd.target() == null ? "" : cmd.target();
        String category = cmd.category() == null ? "" : cmd.category();
        toolCalls.add(agent.getId() + "|" + kind + "|" + cmd.tool() + "|" + target);

        return switch (kind) {
            case "read", "api_call" -> {
                String path = resolvePath(category, target);
                String content = files.computeIfAbsent(path,
                        p -> DataCatalog.labelOf(category) + " mock record generated for " + agent.getName());
                String out = mode == Mode.REDACTED ? redact(content) : content;
                yield new ExecOutcome(true, "Read from mock resource " + path, out);
            }
            case "write" -> {
                String path = resolvePath(category, target);
                String payload = "written by " + agent.getId() + " at " + java.time.Instant.now();
                files.put(path, payload);
                yield new ExecOutcome(true, "Wrote mock resource " + path, payload);
            }
            case "delete" -> {
                String path = resolvePath(category, target);
                String existed = files.remove(path);
                yield new ExecOutcome(true, existed != null
                        ? "Deleted mock resource " + path
                        : "Delete attempted on missing mock resource " + path, "");
            }
            case "export" -> {
                String sink = target.isBlank() ? "external-drop" : target;
                String record = agent.getId() + "|" + category + "|" + sink;
                egressLog.add(record);
                String out = "Queued " + Math.max(1, files.size() / 4) + " records to mock sink '" + sink + "'";
                yield new ExecOutcome(true, "Exported to mock external sink (no network call)", out);
            }
            case "communicate" -> new ExecOutcome(true, "Message delivered over mock agent channel", "");
            default -> new ExecOutcome(true, "Executed mock operation '" + kind + "'", "");
        };
    }

    public ExecOutcome denied(String decision, String kind) {
        return ExecOutcome.denied(decision, kind);
    }

    private String resolvePath(String category, String target) {
        if (target != null && target.startsWith("/")) {
            return target;
        }
        if (target != null && !target.isBlank()) {
            return "/" + category + "/" + target.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        return "/" + category + "/default-record";
    }

    private String redact(String content) {
        if (content.length() <= 8) {
            return "████";
        }
        return content.substring(0, 8)
                + " … [RESTRICTED: " + (content.length() - 8)
                + " chars withheld by ARGUS restriction policy]";
    }

    public synchronized boolean resourceExists(String path) {
        return files.containsKey(path);
    }

    public synchronized String read(String path) {
        return files.get(path);
    }

    public synchronized int fileCount() {
        return files.size();
    }

    public synchronized List<String> egressLog() {
        return new ArrayList<>(egressLog);
    }

    public synchronized List<String> toolCalls() {
        return new ArrayList<>(toolCalls);
    }

    public synchronized void reset() {
        files.clear();
        egressLog.clear();
        toolCalls.clear();
        seed();
    }

    public static boolean isEgressKind(String kind) {
        return "export".equals(kind) || "external_egress".equals(kind);
    }

    public static String describeKind(String kind) {
        return kind == null ? "" : kind.toLowerCase(Locale.ROOT);
    }
}
