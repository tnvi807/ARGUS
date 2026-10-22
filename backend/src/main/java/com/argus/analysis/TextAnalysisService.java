package com.argus.analysis;

import com.argus.agent.Agent;
import com.argus.agent.AgentRepository;
import com.argus.audit.AuditWriter;
import com.argus.catalog.DataCatalog;
import com.argus.engine.ActionCommand;
import com.argus.engine.RiskEngine;
import com.argus.engine.RiskResult;
import com.argus.event.EventDtos;
import com.argus.stream.EventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local rule-based content analysis engine for untrusted prompts, e-mails and documents.
 *
 * <p>No external AI service is contacted: every indicator is a deterministic, explainable regex /
 * keyword rule with an explicit weight, exactly like the gateway risk engine. Results are advisory
 * only - content analysis NEVER authorises an operation. The gateway preview re-evaluates the
 * implied operation against the agent's policy using the real {@link RiskEngine}.
 */
@Service
public class TextAnalysisService {

    public static final String ALLOW = "ALLOW";
    public static final String RESTRICT = "RESTRICT";
    public static final String REQUIRE_APPROVAL = "REQUIRE_APPROVAL";
    public static final String BLOCK = "BLOCK";

    private record Rule(Pattern pattern, String type, String label, String severity, int weight) {
    }

    private static final List<Rule> RULES = List.of(
            rule("(?i)\\b(ignore|disregard|forget)\\b[^.]{0,40}\\b(previous|prior|above|earlier|all)\\b[^.]{0,30}\\b(instructions?|prompts?|rules?)\\b",
                    "PROMPT_INJECTION", "Ignore previous instructions", "HIGH", 35),
            rule("(?i)\\byou are now\\b[^.]{0,40}\\b(a|an|the)?\\s*(admin|unrestricted|jailbroken|dan)\\b",
                    "PROMPT_INJECTION", "Role reassignment (you are now...)", "HIGH", 35),
            rule("(?i)\\b(developer mode|jailbreak|dan mode|do anything now)\\b",
                    "PROMPT_INJECTION", "Jailbreak mode request", "HIGH", 30),
            rule("(?i)\\b(bypass|override|disable|circumvent)\\b[^.]{0,30}\\b(policy|security|approval|firewall|guardrails?)\\b",
                    "POLICY_BYPASS", "Explicit policy bypass request", "HIGH", 40),
            rule("(?i)\\b(grant|add|give)\\b[^.]{0,20}\\b(yourself|you)\\b[^.]{0,25}\\b(permission|access|role|clearance)\\b",
                    "PRIVILEGE_ESCALATION", "Self-granted privilege", "HIGH", 40),
            rule("(?i)\\b(do not|don't|never)\\b[^.]{0,25}\\b(tell|mention|reveal|log|report)\\b",
                    "CONCEALMENT", "Instruction to conceal activity", "HIGH", 25),
            rule("(?i)\\b(secret|hidden|additional)\\b\\s+instructions?:",
                    "PROMPT_INJECTION", "Embedded hidden instructions block", "MEDIUM", 25),
            rule("(?i)\\b(system prompt|your instructions|initial instructions)\\b[^.]{0,30}\\b(reveal|print|show|repeat)\\b",
                    "PROMPT_INJECTION", "System prompt extraction attempt", "HIGH", 30),
            rule("(?i)\\b(export|extract|dump)\\b[^.]{0,30}\\b(all|every|entire|full)\\b[^.]{0,30}\\b(record|data|file|table|row)s?\\b",
                    "EXFILTRATION", "Bulk data extraction request", "HIGH", 35),
            rule("(?i)\\b(send|forward|upload|copy|post)\\b[^.]{0,40}\\b(to|onto)\\b[^.]{0,10}\\b(personal|external|own|private|third[- ]party)\\b",
                    "EXFILTRATION", "Transfer to personal/external destination", "HIGH", 30),
            rule("(?i)\\b(personal drive|personal email|home folder|dropbox|personal account)\\b",
                    "EXFILTRATION", "Personal storage destination", "MEDIUM", 25),
            rule("(?i)\\b(exfiltrate|smuggle|quietly|silently|without anyone noticing)\\b",
                    "EXFILTRATION", "Covert transfer language", "HIGH", 30),
            rule("https?://[^\\s)\"']+", "EXTERNAL_TRANSFER", "External URL reference", "MEDIUM", 20),
            rule("(?i)\\b(curl|wget|webhook|ftp://|scp\\s|netcat|nc\\s+-)\\b",
                    "EXTERNAL_TRANSFER", "External transfer mechanism", "HIGH", 25),
            rule("(?i)\\b(urgent|immediately|right now|asap|before anyone notices|final warning)\\b",
                    "URGENCY_PRESSURE", "Urgency / social pressure", "LOW", 10),
            rule("(?i)\\b(cfo|ceo|boss|director|head of)\\b[^.]{0,30}\\b(requested|asked|approved|instructed)\\b",
                    "URGENCY_PRESSURE", "Appeal to authority", "MEDIUM", 15),
            rule("(?i)\\b(wire|transfer|payment)\\b[^.]{0,25}\\b(immediately|now|today)\\b",
                    "FINANCIAL_ABUSE", "Immediate payment instruction", "HIGH", 35),
            rule("(?i)\\b(delete|erase|wipe)\\b[^.]{0,25}\\b(log|audit|history|evidence)s?\\b",
                    "ANTI_FORENSICS", "Log / evidence destruction request", "HIGH", 35),
            rule("(?i)\\b(call|invoke|use|run)\\b[^.]{0,25}\\b(tool|function|api|webhook)\\b[^.]{0,30}\\b(webhook_egress|external|unknown)\\b",
                    "TOOL_ABUSE", "Unapproved external tool usage", "HIGH", 30),
            rule("[\\u200b-\\u200f\\ufeff\\u2060]", "HIDDEN_CONTENT", "Zero-width / hidden characters", "MEDIUM", 20),
            rule("(?i)\\b(base64|rot13|encoded|obfuscated)\\b[^.]{0,20}\\b(instruction|command|payload)\\b",
                    "HIDDEN_CONTENT", "Encoded payload request", "HIGH", 25)
    );

    private static final List<Rule> SENSITIVE_RULES = List.of(
            rule("\\bAKIA[0-9A-Z]{16}\\b", "SENSITIVE_DATA", "AWS access key id", "HIGH", 25),
            rule("-----BEGIN [A-Z ]*PRIVATE KEY-----", "SENSITIVE_DATA", "Private key material", "HIGH", 35),
            rule("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\b",
                    "SENSITIVE_DATA", "JSON Web Token", "MEDIUM", 20),
            rule("\\b(?:\\d[ -]?){13,16}\\b", "SENSITIVE_DATA", "Possible payment card number", "HIGH", 25),
            rule("\\b\\d{3}-\\d{2}-\\d{4}\\b", "SENSITIVE_DATA", "National id number pattern", "HIGH", 25),
            rule("(?i)\\b(password|passwd|pwd|api[_-]?key|secret|access[_-]?token)\\s*[:=]\\s*[\"']?[^\\s\"']{6,}",
                    "SENSITIVE_DATA", "Inline credential / secret", "HIGH", 25),
            rule("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "SENSITIVE_DATA", "E-mail address", "LOW", 8)
    );

    private final AgentRepository agentRepository;
    private final RiskEngine riskEngine;
    private final ContentAnalysisRepository analysisRepository;
    private final AuditWriter auditWriter;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public TextAnalysisService(AgentRepository agentRepository, RiskEngine riskEngine,
                               ContentAnalysisRepository analysisRepository, AuditWriter auditWriter,
                               EventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.riskEngine = riskEngine;
        this.analysisRepository = analysisRepository;
        this.auditWriter = auditWriter;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    private static Rule rule(String regex, String type, String label, String severity, int weight) {
        return new Rule(Pattern.compile(regex), type, label, severity, weight);
    }

    @Transactional
    public AnalysisDtos.AnalysisResult analyse(String rawText, String source, String filename, String actor) {
        String text = rawText == null ? "" : rawText;
        List<AnalysisDtos.Indicator> indicators = new ArrayList<>();
        int score = 0;
        int sensitiveValues = 0;

        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            int hits = 0;
            int lastOffset = -1;
            while (m.find() && hits < 3) {
                hits++;
                lastOffset = m.start();
                indicators.add(new AnalysisDtos.Indicator(rule.type(), rule.label(), rule.severity(),
                        rule.weight(), snippet(text, m.start(), m.end()), m.start()));
            }
            if (hits > 0) {
                score += rule.weight() + Math.max(0, hits - 1) * 5;
            }
        }
        for (Rule rule : SENSITIVE_RULES) {
            Matcher m = rule.pattern().matcher(text);
            int hits = 0;
            while (m.find() && hits < 5) {
                hits++;
                sensitiveValues++;
                indicators.add(new AnalysisDtos.Indicator(rule.type(), rule.label(), rule.severity(),
                        rule.weight(), snippet(text, m.start(), m.end()), m.start()));
            }
            if (hits > 0) {
                score += rule.weight();
            }
        }

        score = Math.min(100, score);
        String recommendation = recommendationFor(score);
        String reason = reasonFor(indicators, score);

        Set<String> categoryHints = categoryHints(text);
        Set<String> agentHints = agentHints(text);
        List<Agent> agents = agentRepository.findAllByOrderByIdAsc();
        List<AnalysisDtos.AffectedAgent> affected = affectedAgents(agents, agentHints, categoryHints);

        AnalysisDtos.GatewayPreview gateway = gatewayPreview(agents, agentHints, categoryHints, text, score);

        ContentAnalysis entity = new ContentAnalysis();
        entity.setTs(Instant.now());
        entity.setActor(actor);
        entity.setSourceType(source);
        entity.setFilename(filename);
        entity.setContentHash(sha256(text));
        entity.setTextExcerpt(abbreviate(text.replaceAll("\\s+", " ").trim(), 600));
        entity.setTextLength(text.length());
        entity.setIndicatorCount(indicators.size());
        entity.setScore(score);
        entity.setRecommendation(recommendation);
        entity.setReason(reason);
        entity.setIndicatorsJson(toJson(indicators));
        entity.setAffectedAgentsJson(toJson(affected));
        entity.setGatewayJson(toJson(gateway));
        entity.setSensitiveValues(sensitiveValues);
        ContentAnalysis saved = analysisRepository.save(entity);

        auditWriter.record("analysis", "analyse", null, null,
                "Content analysis of " + (filename == null ? "prompt input" : filename) + ": "
                        + indicators.size() + " indicator(s), score " + score + "/100",
                score >= 70 ? RiskResult.BLOCKED : score >= 40 ? RiskResult.RESTRICTED : RiskResult.ALLOWED,
                score, reason, indicators.stream()
                        .map(i -> new com.argus.engine.RiskResult.RiskFactor(
                                i.type() + ": " + i.indicator(), i.weight(), i.evidence()))
                        .toList(),
                actor, filename == null ? "prompt input" : filename);

        Map<String, Object> policyImpact = new LinkedHashMap<>();
        policyImpact.put("categoriesMentioned", List.copyOf(categoryHints));
        policyImpact.put("agentsMentioned", List.copyOf(agentHints));
        policyImpact.put("restrictedCategoriesTriggered", categoryHints.stream()
                .filter(c -> agents.stream().anyMatch(a -> a.getRestricted().contains(c)))
                .toList());
        policyImpact.put("note", "Content risk never authorises an operation - the gateway "
                + "independently evaluates every implied action against the target agent policy.");

        AnalysisDtos.AnalysisResult result = new AnalysisDtos.AnalysisResult(
                saved.getId(), saved.getTs(), source, filename, actor, indicators, score,
                recommendation, reason, sensitiveValues > 0, sensitiveValues, affected, gateway,
                saved.getTextExcerpt(), text.length(), policyImpact);
        eventPublisher.publish("analysis", Map.of("analysis", result));
        return result;
    }

    private AnalysisDtos.GatewayPreview gatewayPreview(List<Agent> agents, Set<String> agentHints,
                                                       Set<String> categoryHints, String text, int contentScore) {
        Agent target = pickAgent(agents, agentHints, categoryHints);
        if (target == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String kind = "read";
        if (Pattern.compile("\\b(export|send|forward|upload|dump|transfer)\\b").matcher(lower).find()) {
            kind = "export";
        } else if (Pattern.compile("\\b(delete|erase|wipe|remove)\\b").matcher(lower).find()) {
            kind = "delete";
        } else if (Pattern.compile("\\b(write|update|save|store|create)\\b").matcher(lower).find()) {
            kind = "write";
        } else if (Pattern.compile("\\b(call|invoke|request|api)\\b").matcher(lower).find()) {
            kind = "api_call";
        } else if (Pattern.compile("\\b(change|modify|reconfigure|grant)\\b").matcher(lower).find()) {
            kind = "config_change";
        }

        String category = categoryHints.stream()
                .filter(target.getRestricted()::contains)
                .findFirst()
                .orElseGet(() -> categoryHints.stream()
                        .filter(target.getAllowed()::contains)
                        .findFirst()
                        .orElseGet(() -> target.getAllowed().stream().findFirst().orElse(null)));

        String tool = DataCatalog.TOOLS.stream()
                .filter(t -> lower.contains(t.replace('_', ' ')) || lower.contains(t))
                .findFirst()
                .orElseGet(() -> target.getTools().stream().findFirst().orElse(null));

        String targetPath = null;
        Matcher url = Pattern.compile("https?://[^\\s)\"']+").matcher(text);
        if (url.find()) {
            targetPath = url.group();
        } else {
            Matcher path = Pattern.compile("/[A-Za-z0-9_./-]{3,}").matcher(text);
            if (path.find()) {
                targetPath = path.group();
            }
        }

        ActionCommand command = new ActionCommand(target.getId(), kind, tool, category, targetPath,
                null, null, null, null);
        RiskResult result = riskEngine.evaluate(
                com.argus.agent.AgentViews.view(target), command, List.of(),
                com.argus.agent.AgentViews.index(agents), Instant.now(),
                RiskEngine.EngineSignals.of(0));

        String note = "Analysis produced risk " + contentScore
                + "/100 for the content itself. The gateway independently scored the implied "
                + kind + " as " + result.score() + "/100 -> " + result.decision()
                + ". Only the gateway decision decides whether the operation may run.";
        return new AnalysisDtos.GatewayPreview(target.getId(), target.getName(), kind, tool, category,
                targetPath, result.score(), result.decision(), result.factors(), note);
    }

    private Agent pickAgent(List<Agent> agents, Set<String> agentHints, Set<String> categoryHints) {
        for (Agent a : agents) {
            String key = (a.getId() + " " + a.getName() + " " + a.getRoleTitle()).toLowerCase(Locale.ROOT);
            if (agentHints.stream().anyMatch(key::contains)) {
                return a;
            }
        }
        for (Agent a : agents) {
            for (String cat : categoryHints) {
                if (a.getRestricted().contains(cat)) {
                    return a;
                }
            }
        }
        return agents.isEmpty() ? null : agents.get(0);
    }

    private List<AnalysisDtos.AffectedAgent> affectedAgents(List<Agent> agents, Set<String> agentHints,
                                                            Set<String> categoryHints) {
        List<AnalysisDtos.AffectedAgent> out = new ArrayList<>();
        for (Agent a : agents) {
            List<String> relations = new ArrayList<>();
            String key = (a.getId() + " " + a.getName() + " " + a.getRoleTitle()).toLowerCase(Locale.ROOT);
            boolean named = agentHints.stream().anyMatch(key::contains);
            if (named) {
                relations.add("agent named in the input");
            }
            for (String cat : categoryHints) {
                if (a.getRestricted().contains(cat)) {
                    relations.add("policy DENIES " + DataCatalog.labelOf(cat));
                } else if (a.getAllowed().contains(cat)) {
                    relations.add("role GRANTS access to " + DataCatalog.labelOf(cat));
                }
            }
            String lower = String.join(" ", relations);
            if (named || (!relations.isEmpty() && (lower.contains("DENIES") || named))) {
                out.add(new AnalysisDtos.AffectedAgent(a.getId(), a.getName(), a.getIcon(),
                        a.getRoleTitle(), abbreviate(String.join("; ", relations), 300)));
            }
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    private Set<String> categoryHints(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, String> hints = Map.ofEntries(
                Map.entry("salary", "salary"), Map.entry("payroll", "salary"),
                Map.entry("compensation", "salary"), Map.entry("pii", "pii"),
                Map.entry("personal data", "pii"), Map.entry("customer data", "pii"),
                Map.entry("phone number", "pii"), Map.entry("home address", "pii"),
                Map.entry("secret", "secrets"), Map.entry("credential", "secrets"),
                Map.entry("password", "secrets"), Map.entry("api key", "secrets"),
                Map.entry("payment", "payments"), Map.entry("bank", "payments"),
                Map.entry("wire", "payments"), Map.entry("invoice", "invoices"),
                Map.entry("supplier", "supplier"), Map.entry("vendor", "supplier"),
                Map.entry("source code", "source"), Map.entry("repository", "source"),
                Map.entry("ticket", "tickets"), Map.entry("leave", "leave"),
                Map.entry("employee", "employee"), Map.entry("roster", "employee"),
                Map.entry("metric", "metrics"), Map.entry("build log", "buildlogs"),
                Map.entry("staging", "staging"), Map.entry("hr record", "employee"));
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : hints.entrySet()) {
            if (lower.contains(e.getKey())) {
                found.add(e.getValue());
            }
        }
        return found;
    }

    private Set<String> agentHints(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, String> hints = Map.ofEntries(
                Map.entry("procure", "procure"), Map.entry("procurement", "procure"),
                Map.entry("finbot", "fin"), Map.entry("finance", "fin"),
                Map.entry("billing", "fin"), Map.entry("support", "support"),
                Map.entry("inbox", "support"), Map.entry("email agent", "support"),
                Map.entry("codebot", "coding"), Map.entry("devops", "coding"),
                Map.entry("ci/cd", "coding"));
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : hints.entrySet()) {
            if (lower.contains(e.getKey())) {
                found.add(e.getValue());
            }
        }
        return found;
    }

    static String recommendationFor(int score) {
        if (score >= 70) {
            return BLOCK;
        }
        if (score >= 60) {
            return REQUIRE_APPROVAL;
        }
        if (score >= 30) {
            return RESTRICT;
        }
        return ALLOW;
    }

    private String reasonFor(List<AnalysisDtos.Indicator> indicators, int score) {
        if (indicators.isEmpty()) {
            return "No prompt-injection, exfiltration or sensitive-data indicators matched";
        }
        return indicators.stream()
                .sorted((a, b) -> Integer.compare(b.weight(), a.weight()))
                .limit(2)
                .map(i -> i.indicator() + " (+" + i.weight() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("") + " -> risk " + score + "/100";
    }

    private String snippet(String text, int start, int end) {
        int from = Math.max(0, start - 30);
        int to = Math.min(text.length(), end + 30);
        return abbreviate(text.substring(from, to).replaceAll("\\s+", " ").trim(), 140);
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return json.length() > 8000 ? json.substring(0, 7997) + "..." : json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
