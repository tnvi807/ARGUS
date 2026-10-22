package com.argus.containment;

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

/** Every containment action (resource quarantine, pause, isolate, revoke, restore) ever applied. */
@Entity
@Table(name = "containment_actions", indexes = @Index(name = "idx_containment_ts", columnList = "ts"))
public class ContainmentAction {

    public enum Kind {
        BLOCK_RESOURCE, PAUSE_AGENT, RESUME_AGENT, ISOLATE_AGENT, RESTORE_AGENT, REVOKE_TOOL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "agent_id", nullable = false, length = 40)
    private String agentId;

    @Column(name = "agent_name", length = 80)
    private String agentName;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Kind kind;

    @Column(nullable = false, length = 300)
    private String detail;

    @Column(name = "trigger_reason", length = 200)
    private String trigger;

    @Column(name = "actor", length = 64)
    private String actor;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
