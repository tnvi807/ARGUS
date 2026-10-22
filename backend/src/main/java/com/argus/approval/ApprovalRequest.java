package com.argus.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A held agent action awaiting administrator approval (risk 60-69). The action is evaluated and
 * recorded but its side effects are NOT applied until an administrator approves it.
 */
@Entity
@Table(name = "approvals", indexes = @Index(name = "idx_approval_status", columnList = "status"))
public class ApprovalRequest {

    public enum Status {
        PENDING, APPROVED, DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "agent_id", nullable = false, length = 40)
    private String agentId;

    @Column(name = "agent_name", nullable = false, length = 80)
    private String agentName;

    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(length = 60)
    private String tool;

    @Column(length = 60)
    private String category;

    @Column(length = 400)
    private String target;

    @Column(length = 500)
    private String message;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(length = 300)
    private String reason;

    @Column(name = "factors_json", length = 4000)
    private String factorsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "execution_detail", length = 2000)
    private String executionDetail;

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

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
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

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public String getExecutionDetail() {
        return executionDetail;
    }

    public void setExecutionDetail(String executionDetail) {
        this.executionDetail = executionDetail;
    }
}
