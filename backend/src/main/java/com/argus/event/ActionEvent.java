package com.argus.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** One evaluated agent action as displayed on the live feed. */
@Entity
@Table(name = "action_events", indexes = {
        @Index(name = "idx_action_ts", columnList = "ts"),
        @Index(name = "idx_action_decision", columnList = "decision")
})
public class ActionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "agent_id", nullable = false, length = 40)
    private String agentId;

    @Column(name = "agent_name", nullable = false, length = 80)
    private String agentName;

    @Column(name = "agent_role", length = 80)
    private String agentRole;

    @Column(name = "agent_icon", length = 8)
    private String agentIcon;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(length = 60)
    private String tool;

    @Column(length = 60)
    private String category;

    @Column(length = 400)
    private String target;

    @Column(length = 500)
    private String message;

    @Column(name = "to_agent", length = 40)
    private String toAgent;

    @Column(nullable = false)
    private double sensitivity;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(length = 300)
    private String reason;

    @Column(name = "factors_json", length = 4000)
    private String factorsJson;

    @Column(length = 40)
    private String demo;

    /** True only when the gateway actually executed the action (side effects applied). */
    @Column(nullable = false)
    private boolean executed;

    @Column(name = "execution_detail", length = 2000)
    private String executionDetail;

    @Column(name = "approval_id")
    private Long approvalId;

    @PrePersist
    void onCreate() {
        if (ts == null) {
            ts = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getTs() {
        return ts;
    }

    public void setTs(Instant ts) {
        this.ts = ts;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentRole() {
        return agentRole;
    }

    public void setAgentRole(String agentRole) {
        this.agentRole = agentRole;
    }

    public String getAgentIcon() {
        return agentIcon;
    }

    public void setAgentIcon(String agentIcon) {
        this.agentIcon = agentIcon;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToAgent() {
        return toAgent;
    }

    public void setToAgent(String toAgent) {
        this.toAgent = toAgent;
    }

    public double getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(double sensitivity) {
        this.sensitivity = sensitivity;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getFactorsJson() {
        return factorsJson;
    }

    public void setFactorsJson(String factorsJson) {
        this.factorsJson = factorsJson;
    }

    public String getDemo() {
        return demo;
    }

    public void setDemo(String demo) {
        this.demo = demo;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public String getExecutionDetail() {
        return executionDetail;
    }

    public void setExecutionDetail(String executionDetail) {
        this.executionDetail = executionDetail;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }
}
