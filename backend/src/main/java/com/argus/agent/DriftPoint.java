package com.argus.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One point of the 14-day sensitivity trend used by the slow-drift engine. */
@Entity
@Table(name = "agent_drift_points")
public class DriftPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(name = "label", nullable = false, length = 12)
    private String label;

    @Column(nullable = false)
    private double sens;

    protected DriftPoint() {
    }

    public DriftPoint(Agent agent, String label, double sens) {
        this.agent = agent;
        this.label = label;
        this.sens = sens;
    }

    public Long getId() {
        return id;
    }

    public Agent getAgent() {
        return agent;
    }

    public String getLabel() {
        return label;
    }

    public double getSens() {
        return sens;
    }

    public void setSens(double sens) {
        this.sens = sens;
    }
}
