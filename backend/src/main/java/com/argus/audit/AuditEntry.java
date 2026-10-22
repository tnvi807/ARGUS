package com.argus.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only governance ledger. Updates and deletes are rejected at the persistence layer so the
 * log stays tamper-evident for SOC 2 / ISO 27001 / EU AI Act style audits.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    /** Authenticated user (or "system") responsible for the entry. */
    @Column(length = 64)
    private String actor;

    @Column(length = 80)
    private String agent;

    @Column(length = 80)
    private String role;

    @Column(length = 40)
    private String kind;

    @Column(length = 500)
    private String description;

    @Column(length = 300)
    private String target;

    @Column(length = 20)
    private String decision;

    private Integer score;

    @Column(length = 300)
    private String reason;

    @Column(length = 4000)
    private String factorsJson;

    @PrePersist
    void onCreate() {
        if (ts == null) {
            ts = Instant.now();
        }
    }

    @PreUpdate
    void forbidUpdate() {
        throw new IllegalStateException("Audit log entries are append-only");
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

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
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
}
