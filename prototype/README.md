# 👁️ ARGUS SENTINEL
### Agent Role & Graph Unified Sentinel
**Real-Time Autonomous Agent Security Operations Center (SOC) & Intent Firewall**

[![MVP Features](https://img.shields.io/badge/MVP%20Features-6%2F6%20Live-brightgreen)](#the-6-core-mvp-features)
[![Security Solutions](https://img.shields.io/badge/Merged%20Solutions-3%20Engines-blue)](#the-3-unified-security-engines)
[![Dependencies](https://img.shields.io/badge/Dependencies-0%20Zero-success)](#getting-started)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](#)

---

## 🌟 Overview
Enterprises are rapidly deploying autonomous AI agents to manage databases, ERP systems, email workflows, and code pipelines. While conventional security focuses strictly on **authentication** ("Is this agent logged in?"), **ARGUS focuses on authorization of intent**:

> **"Given your defined job description, baseline history, and upstream instructions—is this specific action actually your job?"**

ARGUS stops prompt injection, multi-agent lateral movement (confused-deputy attacks), unauthorized tool invocation, and silent behavioral drift in real time.

---

## 📋 Complete Documentation
For an exhaustive, technical deep-dive into the mathematical engines, rule weights, forensic log formats, and compliance alignment, please see:
👉 **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)**

---

## 🚀 The 6 Core MVP Features & Their Exact Jobs

| Feature | Primary Page | Exact Job & Purpose |
|---|---|---|
| **1. Agent Role Registry** | [`pages/agents.html`](pages/agents.html) | **Identity & Policy Registry:** Provides each agent with an explicit least-privilege "ID badge" defining permitted tools, authorized data scopes, denied data scopes, forbidden actions, and clearance level (1–5). |
| **2. Live Activity Monitoring** | [`pages/live.html`](pages/live.html) | **Real-Time Security Feed:** Sub-millisecond stream evaluating agent operations. Provides color-coded decisions (`ALLOWED`, `FLAGGED`, `BLOCKED`), live risk score meters, and instant security toast alerts. |
| **3. Identity Drift Detection Engine** | [`pages/drift.html`](pages/drift.html) | **Behavioral Anomaly & Slow-Drift Defense:** Monitors 14-day sensitivity trends, trend slopes, off-hours activity, and computes the **Identity Fingerprint Integrity %** to catch gradual attacks over days. |
| **4. Explainable Risk Scoring & Alerts** | [`pages/alerts.html`](pages/alerts.html) | **Explainable Decisions & Triage:** Eliminates black-box AI decisions by calculating normalized risk (0–100) with complete factor attribution, side-by-side role vs. attempt comparisons, and forensic evidence. |
| **5. Automatic & Surgical Containment** | Background & [`pages/alerts.html`](pages/alerts.html) | **Automated Response:** Surgically freezes specific target resources or pauses compromised agents (at risk $\ge 90$ or upon self-modification attempts) without breaking benign workflows. |
| **6. Compliance Audit Log** | [`pages/audit.html`](pages/audit.html) | **Immutable Governance Ledger:** Append-only event store recording every action, score, factor, and playbook execution with real-time search and 1-click export to JSON/CSV for SOC 2 and EU AI Act compliance. |

---

## 🛡️ The 3 Unified Security Engines

Inside [`js/store.js`](js/store.js), ARGUS runs three security engines on every single agent action:

1. **Role-Aware Action Firewall (Solution 1):** Verifies tool allowlists, denied data categories, clearance levels, and explicitly forbidden actions (e.g. self-modifying configuration or unauthorized exports).
2. **Trust-Graph & Chain Detection (Solution 2):** Scans inter-agent messages for risky directives (`export`, `dump`, `bypass`, `urgent`, `don't tell`) and propagates taint across multi-agent communication hops.
3. **Behavioral Baseline & Slow-Drift Defense (Solution 3):** Computes sensitivity Z-Scores ($Z > 2.0$), trend-line linear slopes ($> +0.35/\text{day}$), and novel resource access to identify stealth attacks.

---

## ⚡ Bonus Innovations

* **🔮 Attack-Path Prediction ("Blast Radius"):** When an agent is flagged or compromised, ARGUS runs a Breadth-First Search (BFS) over the multi-agent graph to predict the adversary's next 3 steps (target peer agents, reachable tools, and vulnerable databases).
* **🧬 Identity Fingerprint Integrity %:** A living behavioral metric ($0\% - 100\%$) indicating how much of the original agent's identity remains intact. Agents dropping below $60\%$ are flagged as compromised.

---

## 🎭 5 Scripted Attack Simulation Demos

Experience ARGUS in action with realistic enterprise attack simulations:

* **Demo 1 — Role Violation:** ProcureBot attempts to steal HR salary files (`pages/live.html?demo=role`).
* **Demo 2 — Suspicious Chain:** Phished Email Agent tricks Finance Agent into exfiltrating data (`pages/graph.html?demo=chain`).
* **Demo 3 — Slow Drift:** DataMiner creeps from innocent metrics to customer PII over 7 days (`pages/drift.html?demo=drift`).
* **Demo 4 — Rogue Tool:** DevOpsBot attempts to ship secrets via an unauthorized webhook tool (`pages/live.html?demo=tool`).
* **Demo 5 — Privilege Escalation:** Agent attempts to self-modify its own role configuration (`pages/alerts.html?demo=priv`).

---

## 💻 Getting Started

ARGUS has **zero external dependencies** and requires **no build step**:

1. Clone or download this repository.
2. Open [`pages/index.html`](pages/index.html) in any modern web browser.
3. Watch the live autopilot traffic or run an attack demo from the Overview page.
4. Visit [`pages/features.html`](pages/features.html) for the plain-English guide and 90-second judge presentation script.
