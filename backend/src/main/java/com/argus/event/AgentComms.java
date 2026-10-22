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

/** One agent-to-agent message observed by the trust graph. */
@Entity
@Table(name = "agent_comms", indexes = @Index(name = "idx_comms_ts", columnList = "ts"))
public class AgentComms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "from_agent", nullable = false, length = 40)
    private String fromAgent;

    @Column(name = "to_agent", nullable = false, length = 40)
    private String toAgent;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private boolean flagged;

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

    public String getFromAgent() {
        return fromAgent;
    }

    public void setFromAgent(String fromAgent) {
        this.fromAgent = fromAgent;
    }

    public String getToAgent() {
        return toAgent;
    }

    public void setToAgent(String toAgent) {
        this.toAgent = toAgent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }
}
