package com.argus.seed;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.admin.PermissionEntity;
import com.argus.admin.PermissionEntityRepository;
import com.argus.admin.RoleEntity;
import com.argus.admin.RoleEntityRepository;
import com.argus.config.AppProperties;
import com.argus.security.Permissions;
import com.argus.user.AppUser;
import com.argus.user.AppUserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap data: the permission catalogue, the three least-privilege roles, the demo operator
 * accounts and the four baseline agents with their behavioural fingerprints. Idempotent - it only
 * ever inserts what is missing, and {@link #seedAgents()} can be re-run after a demo reset.
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final Map<String, String> PERMISSION_DESCRIPTIONS = catalogue();

    private static final Map<String, String> ROLE_DESCRIPTIONS = Map.of(
            Permissions.ROLE_ADMIN, "Full administration: users, policies, approvals, containment",
            Permissions.ROLE_ANALYST, "Security analyst: investigate, contain, approve demos, analyse content",
            Permissions.ROLE_VIEWER, "Read-only: dashboard, alerts, audit trail");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final RoleEntityRepository roleRepository;
    private final PermissionEntityRepository permissionRepository;
    private final AgentRepository agentRepository;

    public DataSeeder(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                      AppProperties props, RoleEntityRepository roleRepository,
                      PermissionEntityRepository permissionRepository,
                      AgentRepository agentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.agentRepository = agentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        seedPermissions();
        seedRoles();
        seedUsers();
        if (agentRepository.count() == 0) {
            seedAgents();
        }
    }

    @Transactional
    public void seedAgents() {
        agentRepository.save(procure());
        agentRepository.save(finance());
        agentRepository.save(support());
        agentRepository.save(coding());
    }

    private void seedPermissions() {
        PERMISSION_DESCRIPTIONS.forEach((code, description) -> {
            permissionRepository.findById(code).ifPresentOrElse(
                    existing -> {
                        if (!description.equals(existing.getDescription())) {
                            existing.setDescription(description);
                            permissionRepository.save(existing);
                        }
                    },
                    () -> permissionRepository.save(new PermissionEntity(code, description)));
        });
    }

    private void seedRoles() {
        for (String roleName : Permissions.allRoles()) {
            RoleEntity role = roleRepository.findByName(roleName)
                    .orElseGet(() -> new RoleEntity(roleName, ROLE_DESCRIPTIONS.get(roleName)));
            role.setDescription(ROLE_DESCRIPTIONS.get(roleName));
            role.getPermissions().clear();
            role.getPermissions().addAll(Permissions.forRole(roleName));
            roleRepository.save(role);
        }
    }

    private void seedUsers() {
        seedUser(props.getSecurity().getAdminUsername(), props.getSecurity().getAdminPassword(),
                "ARGUS Administrator", Permissions.ROLE_ADMIN);
        seedUser("analyst", props.getSecurity().getAnalystPassword(),
                "Security Analyst", Permissions.ROLE_ANALYST);
        seedUser("viewer", props.getSecurity().getViewerPassword(),
                "Read-only Viewer", Permissions.ROLE_VIEWER);
    }

    private void seedUser(String username, String password, String displayName, String role) {
        if (username == null || username.isBlank()) {
            return;
        }
        String name = username.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(name)) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Skipping bootstrap user '{}' - no password configured", name);
            return;
        }
        AppUser user = new AppUser();
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.getRoles().add(role);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Bootstrap user '{}' created with role {}", name, role);
    }

    /* ------------------------------------------------------------------ agents */

    private Agent procure() {
        Agent a = base("procure", "ProcureBot", "📦", "Procurement Agent",
                "Reviews suppliers, raises POs, keeps vendor data fresh.", 2,
                List.of("supplier_db", "erp_api", "mail_gateway", "spreadsheet"),
                List.of("supplier", "po", "invoices", "metrics"),
                List.of("salary", "pii", "secrets", "payments"),
                List.of("export_data", "modify_role_config", "wire_payment"),
                1.8,
                Map.of("supplier", 0.40, "po", 0.30, "invoices", 0.20, "metrics", 0.10));
        record(a, "read", "supplier", 2, 3);
        record(a, "read", "invoices", 3, 2);
        record(a, "write", "po", 2, 2);
        record(a, "api_call", "supplier", 2, 1);
        drift(a, 1.8);
        return a;
    }

    private Agent finance() {
        Agent a = base("fin", "LedgerBot", "💰", "Finance Agent",
                "Processes invoices, prepares payment runs, reconciles the ledger.", 3,
                List.of("ledger_api", "banking_portal", "spreadsheet", "document_store"),
                List.of("invoices", "payments", "po"),
                List.of("salary", "source", "secrets", "pii"),
                List.of("modify_role_config", "external_egress"),
                2.4,
                Map.of("invoices", 0.40, "payments", 0.35, "po", 0.25));
        record(a, "read", "invoices", 3, 3);
        record(a, "read", "payments", 4, 2);
        record(a, "api_call", "payments", 4, 2);
        drift(a, 2.4);
        return a;
    }

    private Agent support() {
        Agent a = base("support", "AtlasSupport", "🎧", "Customer Support Agent",
                "Answers customer questions, triages tickets, drafts replies.", 2,
                List.of("mail_gateway", "crm_api", "document_store"),
                List.of("tickets", "inbox", "metrics", "invoices"),
                List.of("salary", "payments", "secrets", "pii"),
                List.of("export_data", "delete_records", "modify_role_config"),
                1.5,
                Map.of("tickets", 0.50, "inbox", 0.25, "invoices", 0.15, "metrics", 0.10));
        record(a, "read", "tickets", 2, 4);
        record(a, "read", "inbox", 2, 2);
        record(a, "read", "invoices", 3, 1);
        drift(a, 1.5);
        return a;
    }

    private Agent coding() {
        Agent a = base("coding", "ForgeBot", "🔧", "CI/CD Agent",
                "Runs builds, deploys to staging, watches pipelines.", 3,
                List.of("git_repo", "ci_pipeline", "cloud_console"),
                List.of("buildlogs", "staging", "source"),
                List.of("secrets", "payments", "salary", "pii"),
                List.of("wire_payment", "modify_role_config", "delete_records"),
                1.2,
                Map.of("buildlogs", 0.50, "staging", 0.30, "source", 0.20));
        record(a, "api_call", "buildlogs", 1, 4);
        record(a, "read", "source", 3, 2);
        record(a, "write", "staging", 2, 1);
        drift(a, 1.2);
        return a;
    }

    private Agent base(String id, String name, String icon, String roleTitle, String description,
                       int clearance, java.util.List<String> tools, java.util.List<String> allowed,
                       java.util.List<String> restricted, java.util.List<String> restrictActions,
                       double baselineAvgSens, Map<String, Double> baselineCats) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setIcon(icon);
        agent.setRoleTitle(roleTitle);
        agent.setDescription(description);
        agent.setClearance(clearance);
        agent.setStatus(Agent.Status.ACTIVE);
        agent.setTools(new java.util.LinkedHashSet<>(tools));
        agent.setAllowed(new java.util.LinkedHashSet<>(allowed));
        agent.setRestricted(new java.util.LinkedHashSet<>(restricted));
        agent.setRestrictActions(new java.util.LinkedHashSet<>(restrictActions));
        agent.setBaselineRate(8);
        agent.setBaselineOffHours(0.05);
        agent.setBaselineAvgSens(baselineAvgSens);
        agent.setBaselineCats(new LinkedHashMap<>(baselineCats));
        return agent;
    }

    /** Marks a category as historically familiar so normal work is not scored as "novel". */
    private void record(Agent agent, String kind, String category, double sensitivity, int times) {
        for (int i = 0; i < times; i++) {
            agent.recordAction(kind, category, sensitivity, false);
        }
    }

    /** Ten flat history points around the baseline - stable identity, no false drift at boot. */
    private void drift(Agent agent, double base) {
        double[] series = {base - 0.2, base - 0.1, base + 0.1, base, base - 0.1,
                base + 0.2, base, base + 0.1, base - 0.1, base};
        String[] labels = {"D-9", "D-8", "D-7", "D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "Now"};
        for (int i = 0; i < series.length; i++) {
            agent.addDriftPoint(labels[i], series[i]);
        }
    }

    private static Map<String, String> catalogue() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(Permissions.DASHBOARD_READ, "View the live dashboard");
        map.put(Permissions.AGENTS_READ, "View agents, policies and drift");
        map.put(Permissions.AGENTS_WRITE, "Create and edit agent policies; pause/resume");
        map.put(Permissions.POLICIES_WRITE, "Edit least-privilege policy templates");
        map.put(Permissions.ALERTS_READ, "View alerts and containment history");
        map.put(Permissions.ALERTS_ACT, "Review and resolve alerts");
        map.put(Permissions.AUDIT_READ, "Read the append-only audit trail");
        map.put(Permissions.AUDIT_EXPORT, "Export the audit trail (JSON/CSV)");
        map.put(Permissions.EVENTS_READ, "Read the live event feed and stream");
        map.put(Permissions.EVIDENCE_UPLOAD, "Upload and delete evidence files");
        map.put(Permissions.EVIDENCE_READ, "List and download evidence files");
        map.put(Permissions.ACTIONS_SUBMIT, "Submit actions through the gateway");
        map.put(Permissions.DEMOS_RUN, "Run Demo Lab scenarios");
        map.put(Permissions.SIM_CONTROL, "Start/stop the autonomous traffic simulation");
        map.put(Permissions.ADMIN_MANAGE, "Manage users, roles and passwords");
        map.put(Permissions.APPROVALS_READ, "View pending approvals");
        map.put(Permissions.APPROVALS_ACT, "Approve or deny held actions");
        map.put(Permissions.ANALYSIS_CREATE, "Analyse prompts and PDFs");
        return map;
    }
}
