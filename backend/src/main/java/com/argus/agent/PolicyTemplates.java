package com.argus.agent;

import com.argus.catalog.DataCatalog;
import java.util.List;
import java.util.Map;

/** Least-privilege role templates ported from the Sentinel "template library". */
public final class PolicyTemplates {

    private PolicyTemplates() {
    }

    public record Template(String key, String name, String roleTitle, String description,
                           String icon, int clearance, List<String> tools, List<String> allowed,
                           List<String> restricted, List<String> restrictActions) {
    }

    public static final List<Template> ALL = List.of(
            new Template("procurement", "Procurement Specialist", "Procurement Agent",
                    "Reviews suppliers, raises POs, keeps vendor data fresh.", "📦", 2,
                    List.of("supplier_db", "erp_api", "mail_gateway", "spreadsheet"),
                    List.of("supplier", "po", "invoices"),
                    List.of("salary", "pii", "secrets"),
                    List.of("export_data", "modify_role_config", "wire_payment")),
            new Template("email_triage", "Email Triage Assistant", "Email Triage Agent",
                    "Sorts inbox, drafts replies, files tickets into CRM.", "📧", 2,
                    List.of("mail_gateway", "crm_api"),
                    List.of("inbox", "tickets"),
                    List.of("pii", "salary", "secrets", "payments"),
                    List.of("export_data", "delete_records", "modify_role_config")),
            new Template("finance", "Finance & Billing Bot", "Finance Agent",
                    "Processes invoices, prepares payment runs, reconciles ledger.", "💰", 3,
                    List.of("ledger_api", "banking_portal", "spreadsheet", "document_store"),
                    List.of("invoices", "payments", "po"),
                    List.of("salary", "source", "secrets"),
                    List.of("modify_role_config", "external_egress")),
            new Template("hr", "HR & Benefits Helper", "HR Agent",
                    "Answers employee questions, manages leave requests.", "🤝", 3,
                    List.of("hris_api", "document_store", "mail_gateway"),
                    List.of("employee", "leave"),
                    List.of("salary", "payments", "pii", "supplier"),
                    List.of("export_data", "wire_payment", "modify_role_config")),
            new Template("analyst", "Data Analyst & BI", "Analytics Agent",
                    "Builds dashboards from aggregated business metrics.", "📊", 2,
                    List.of("data_warehouse", "bi_dashboard"),
                    List.of("metrics"),
                    List.of("pii", "salary", "secrets", "payments"),
                    List.of("export_data", "external_egress", "delete_records")),
            new Template("devops", "DevOps & CI/CD Engineer", "CI/CD Agent",
                    "Runs builds, deploys to staging, watches pipelines.", "🔧", 3,
                    List.of("git_repo", "ci_pipeline", "cloud_console"),
                    List.of("buildlogs", "staging", "source"),
                    List.of("secrets", "payments", "salary", "pii"),
                    List.of("wire_payment", "modify_role_config", "delete_records")));

    public static List<String> referenceData() {
        return List.of();
    }

    public static Map<String, Object> catalog() {
        return Map.of(
                "tools", DataCatalog.TOOLS,
                "categories", DataCatalog.categories().stream()
                        .map(c -> Map.of("code", c.code(), "label", c.label(),
                                "sensitivity", c.sensitivity()))
                        .toList(),
                "restrictActions", DataCatalog.RESTRICT_POOL,
                "actionKinds", DataCatalog.ACTION_KINDS);
    }
}
