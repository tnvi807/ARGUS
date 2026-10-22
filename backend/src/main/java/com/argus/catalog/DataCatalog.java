package com.argus.catalog;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reference data ported from the original ARGUS Sentinel store: data categories with sensitivity,
 * the enterprise tool catalogue, restricted action kinds and suspicious directive keywords.
 */
public final class DataCatalog {

    public record Category(String code, String label, int sensitivity) {
    }

    private DataCatalog() {
    }

    public static final List<String> TOOLS = List.of(
            "supplier_db", "erp_api", "mail_gateway", "crm_api", "ledger_api", "banking_portal",
            "hris_api", "document_store", "data_warehouse", "bi_dashboard", "git_repo", "ci_pipeline",
            "cloud_console", "webhook_egress", "spreadsheet");

    public static final List<String> RESTRICT_POOL = List.of(
            "export_data", "modify_role_config", "delete_records", "external_egress",
            "wire_payment", "publish_write");

    public static final List<String> ACTION_KINDS = List.of(
            "read", "api_call", "export", "write", "config_change", "communicate", "delete");

    public static final List<String> KEYWORDS = List.of(
            "export", "dump", "forward", "bypass", "confidential", "secret", "personal drive",
            "urgent", "delete logs", "don't tell", "do not tell", "wire", "all records", "everything");

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

    static {
        add("supplier", "Supplier Records", 2);
        add("po", "Purchase Orders", 2);
        add("inbox", "Email Inbox", 2);
        add("tickets", "Customer Tickets", 2);
        add("invoices", "Invoices", 3);
        add("payments", "Payment Runs", 4);
        add("employee", "Employee Profiles", 3);
        add("leave", "Leave Records", 2);
        add("salary", "Salary & Comp Data", 5);
        add("metrics", "Aggregated Metrics", 1);
        add("pii", "Customer PII Tables", 5);
        add("buildlogs", "Build Logs", 1);
        add("staging", "Staging Environment", 2);
        add("secrets", "Prod Secrets", 5);
        add("source", "Source Code", 3);
    }

    private static void add(String code, String label, int sensitivity) {
        CATEGORIES.put(code, new Category(code, label, sensitivity));
    }

    public static Optional<Category> category(String code) {
        return Optional.ofNullable(code == null ? null : CATEGORIES.get(code));
    }

    public static Collection<Category> categories() {
        return CATEGORIES.values();
    }

    public static int sensitivityOf(String code) {
        return category(code).map(Category::sensitivity).orElse(0);
    }

    public static String labelOf(String code) {
        return category(code).map(Category::label).orElse(code);
    }

    public static boolean isKnownCategory(String code) {
        return code != null && CATEGORIES.containsKey(code);
    }
}
