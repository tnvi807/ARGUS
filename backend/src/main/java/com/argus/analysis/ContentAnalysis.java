package com.argus.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted record of a content analysis (prompt / PDF). Only a bounded excerpt of the untrusted
 * input is stored - the full document is treated as hostile data and is never re-interpreted as
 * application instructions.
 */
@Entity
@Table(name = "content_analyses", indexes = @Index(name = "idx_analysis_ts", columnList = "ts"))
public class ContentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    @Column(length = 64)
    private String actor;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "filename", length = 200)
    private String filename;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "text_excerpt", length = 600)
    private String textExcerpt;

    @Column(name = "text_length", nullable = false)
    private int textLength;

    @Column(name = "indicator_count", nullable = false)
    private int indicatorCount;

    @Column(nullable = false)
    private int score;

    @Column(name = "recommendation", nullable = false, length = 30)
    private String recommendation;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "indicators_json", length = 8000)
    private String indicatorsJson;

    @Column(name = "affected_agents_json", length = 4000)
    private String affectedAgentsJson;

    @Column(name = "gateway_json", length = 8000)
    private String gatewayJson;

    @Column(name = "sensitive_values", nullable = false)
    private int sensitiveValues;

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

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getTextExcerpt() {
        return textExcerpt;
    }

    public void setTextExcerpt(String textExcerpt) {
        this.textExcerpt = textExcerpt;
    }

    public int getTextLength() {
        return textLength;
    }

    public void setTextLength(int textLength) {
        this.textLength = textLength;
    }

    public int getIndicatorCount() {
        return indicatorCount;
    }

    public void setIndicatorCount(int indicatorCount) {
        this.indicatorCount = indicatorCount;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIndicatorsJson() {
        return indicatorsJson;
    }

    public void setIndicatorsJson(String indicatorsJson) {
        this.indicatorsJson = indicatorsJson;
    }

    public String getAffectedAgentsJson() {
        return affectedAgentsJson;
    }

    public void setAffectedAgentsJson(String affectedAgentsJson) {
        this.affectedAgentsJson = affectedAgentsJson;
    }

    public String getGatewayJson() {
        return gatewayJson;
    }

    public void setGatewayJson(String gatewayJson) {
        this.gatewayJson = gatewayJson;
    }

    public int getSensitiveValues() {
        return sensitiveValues;
    }

    public void setSensitiveValues(int sensitiveValues) {
        this.sensitiveValues = sensitiveValues;
    }
}
