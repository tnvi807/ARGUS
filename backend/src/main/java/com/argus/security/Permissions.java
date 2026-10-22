package com.argus.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Least-privilege permission catalogue. Roles grant permissions, permissions guard endpoints.
 */
public final class Permissions {

    public static final String DASHBOARD_READ = "dashboard.read";
    public static final String AGENTS_READ = "agents.read";
    public static final String AGENTS_WRITE = "agents.write";
    public static final String POLICIES_WRITE = "policies.write";
    public static final String ALERTS_READ = "alerts.read";
    public static final String ALERTS_ACT = "alerts.act";
    public static final String AUDIT_READ = "audit.read";
    public static final String AUDIT_EXPORT = "audit.export";
    public static final String EVENTS_READ = "events.read";
    public static final String EVIDENCE_UPLOAD = "evidence.upload";
    public static final String ACTIONS_SUBMIT = "actions.submit";
    public static final String DEMOS_RUN = "demos.run";
    public static final String SIM_CONTROL = "sim.control";
    public static final String ADMIN_MANAGE = "admin.manage";
    public static final String APPROVALS_READ = "approvals.read";
    public static final String APPROVALS_ACT = "approvals.act";
    public static final String ANALYSIS_CREATE = "analysis.create";
    public static final String EVIDENCE_READ = "evidence.read";

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_ANALYST = "ANALYST";
    public static final String ROLE_VIEWER = "VIEWER";

    private static final Set<String> VIEWER = Set.of(
            DASHBOARD_READ, AGENTS_READ, ALERTS_READ, AUDIT_READ, EVENTS_READ, ANALYSIS_CREATE);

    private static final Set<String> ANALYST = union(VIEWER, Set.of(
            AGENTS_WRITE, POLICIES_WRITE, ALERTS_ACT, AUDIT_EXPORT,
            EVIDENCE_UPLOAD, EVIDENCE_READ, ACTIONS_SUBMIT, DEMOS_RUN, SIM_CONTROL,
            APPROVALS_READ));

    private static final Set<String> ADMIN = union(ANALYST, Set.of(ADMIN_MANAGE, APPROVALS_ACT));

    private Permissions() {
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return Set.copyOf(out);
    }

    public static Set<String> forRole(String role) {
        if (role == null) {
            return Set.of();
        }
        return switch (role.toUpperCase()) {
            case ROLE_ADMIN -> ADMIN;
            case ROLE_ANALYST -> ANALYST;
            case ROLE_VIEWER -> VIEWER;
            default -> Set.of();
        };
    }

    public static Set<String> forRoles(Set<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        if (roles != null) {
            for (String r : roles) {
                out.addAll(forRole(r));
            }
        }
        return out;
    }

    public static boolean isKnownRole(String role) {
        return ROLE_ADMIN.equals(role) || ROLE_ANALYST.equals(role) || ROLE_VIEWER.equals(role);
    }

    public static Set<String> allRoles() {
        return Set.of(ROLE_ADMIN, ROLE_ANALYST, ROLE_VIEWER);
    }
}
