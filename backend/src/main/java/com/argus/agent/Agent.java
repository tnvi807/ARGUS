package com.argus.agent;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identity &amp; least-privilege policy record for one autonomous agent (the "ID badge"),
 * plus its behavioural fingerprint counters and drift baseline.
 */
@Entity
@Table(name = "agents")
public class Agent {

    public enum Status {
        ACTIVE, PAUSED, ISOLATED
    }

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 8)
    private String icon;

    @Column(name = "role_title", nullable = false, length = 80)
    private String roleTitle;

    @Column(length = 400)
    private String description;

    @ElementCollection
    @CollectionTable(name = "agent_tools", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "tool", length = 40)
    private Set<String> tools = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "agent_allowed", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "category", length = 40)
    private Set<String> allowed = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "agent_restricted", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "category", length = 40)
    private Set<String> restricted = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "agent_restrict_actions", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "action_kind", length = 40)
    private Set<String> restrictActions = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "agent_blocked_resources", joinColumns = @JoinColumn(name = "agent_id"))
    @Column(name = "category", length = 40)
    private Set<String> blockedResources = new LinkedHashSet<>();

    @Column(nullable = false)
    private int clearance = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "last_risk", nullable = false)
    private int lastRisk;

    /* ---- behavioural baseline (engine 3) ---- */

    @Column(name = "baseline_rate", nullable = false)
    private double baselineRate = 10;

    @Column(name = "baseline_off_hours", nullable = false)
    private double baselineOffHours = 0.05;

    @Column(name = "baseline_avg_sens", nullable = false)
    private double baselineAvgSens = 1.5;

    @ElementCollection
    @CollectionTable(name = "agent_baseline_cats", joinColumns = @JoinColumn(name = "agent_id"))
    @MapKeyColumn(name = "category", length = 40)
    @Column(name = "weight")
    private Map<String, Double> baselineCats = new LinkedHashMap<>();

    /* ---- identity fingerprint counters ---- */

    @Column(name = "file_reads", nullable = false)
    private int fileReads;

    @Column(name = "api_calls", nullable = false)
    private int apiCalls;

    @Column(name = "exports", nullable = false)
    private int exports;

    @Column(name = "off_hours_actions", nullable = false)
    private int offHoursActions;

    @Column(name = "action_count", nullable = false)
    private int actionCount;

    @Column(name = "sens_sum", nullable = false)
    private double sensSum;

    @ElementCollection
    @CollectionTable(name = "agent_fingerprint_cats", joinColumns = @JoinColumn(name = "agent_id"))
    @MapKeyColumn(name = "category", length = 40)
    @Column(name = "hits", nullable = false)
    private Map<String, Integer> fingerprintCats = new LinkedHashMap<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DriftPoint> driftHistory = new ArrayList<>();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void recordAction(String kind, String category, double sensitivity, boolean offHours) {
        actionCount++;
        sensSum += sensitivity;
        if (category != null) {
            fingerprintCats.merge(category, 1, Integer::sum);
        }
        switch (kind == null ? "" : kind) {
            case "read" -> fileReads++;
            case "api_call" -> apiCalls++;
            case "export" -> exports++;
            default -> {
            }
        }
        if (offHours) {
            offHoursActions++;
        }
    }

    public void addDriftPoint(String label, double sens) {
        driftHistory.add(new DriftPoint(this, label, sens));
    }

    public boolean hasSeenCategory(String category) {
        return category != null && fingerprintCats.containsKey(category);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getTools() {
        return tools;
    }

    public void setTools(Set<String> tools) {
        this.tools = tools;
    }

    public Set<String> getAllowed() {
        return allowed;
    }

    public void setAllowed(Set<String> allowed) {
        this.allowed = allowed;
    }

    public Set<String> getRestricted() {
        return restricted;
    }

    public void setRestricted(Set<String> restricted) {
        this.restricted = restricted;
    }

    public Set<String> getRestrictActions() {
        return restrictActions;
    }

    public void setRestrictActions(Set<String> restrictActions) {
        this.restrictActions = restrictActions;
    }

    public Set<String> getBlockedResources() {
        return blockedResources;
    }

    public void setBlockedResources(Set<String> blockedResources) {
        this.blockedResources = blockedResources;
    }

    public int getClearance() {
        return clearance;
    }

    public void setClearance(int clearance) {
        this.clearance = clearance;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getLastRisk() {
        return lastRisk;
    }

    public void setLastRisk(int lastRisk) {
        this.lastRisk = lastRisk;
    }

    public double getBaselineRate() {
        return baselineRate;
    }

    public void setBaselineRate(double baselineRate) {
        this.baselineRate = baselineRate;
    }

    public double getBaselineOffHours() {
        return baselineOffHours;
    }

    public void setBaselineOffHours(double baselineOffHours) {
        this.baselineOffHours = baselineOffHours;
    }

    public double getBaselineAvgSens() {
        return baselineAvgSens;
    }

    public void setBaselineAvgSens(double baselineAvgSens) {
        this.baselineAvgSens = baselineAvgSens;
    }

    public Map<String, Double> getBaselineCats() {
        return baselineCats;
    }

    public void setBaselineCats(Map<String, Double> baselineCats) {
        this.baselineCats = baselineCats;
    }

    public int getFileReads() {
        return fileReads;
    }

    public int getApiCalls() {
        return apiCalls;
    }

    public int getExports() {
        return exports;
    }

    public int getOffHoursActions() {
        return offHoursActions;
    }

    public int getActionCount() {
        return actionCount;
    }

    public double getSensSum() {
        return sensSum;
    }

    public Map<String, Integer> getFingerprintCats() {
        return fingerprintCats;
    }

    public List<DriftPoint> getDriftHistory() {
        return driftHistory;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
