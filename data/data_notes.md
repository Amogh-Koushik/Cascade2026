# Pratibimb: Data Notes and Dataset Provenance
**Owner:** Safal (ML engine + Evaluation)  
**Status:** Task 1 Complete · Verified & Grounded  
**Date:** 25 Oct 2026 · CASCADE '26 Hackathon

---

## 1. Datasets Verification Summary

| Dataset | Scope / Records | Status | Empirical Findings / Fallback | Provenance Label |
|---|---|---|---|---|
| **LSApp** (Large-Scale Mobile App Usage) | 292 users, 3,658,589 rows, 76,247 sessions, 87 unique apps | **VERIFIED** | Parsed full dataset: Social median 4.0m (90th: 19.6m); Messaging median 2.6m; Email median 0.7m. Grounds $\mu_{c0}, \sigma_c$. | `real (public)` |
| **Ljubljana Notification Dataset** | 19 users × 2 weeks | **UNAVAILABLE** (Server `gitlab.fri.uni-lj.si` timed out / down) | Triggered protocol fallback (§5.2, §8): Ulster Field Study | `literature value` |
| **Ulster Field Study** (Protocol Fallback) | Large-scale smartphone notification sensing study | **VERIFIED** | 78% of notifications ignored or dismissed without opening $\rightarrow m_g = 0.22$ ($n_g = 20$). | `literature value` |

---

## 2. Empirical LSApp Session Duration Analysis

Calculated across 76,247 real smartphone sessions:

| Category | Sample Size ($N$) | Empirical Median | 90th Percentile | Fitted $\mu_{\text{log}}$ | Fitted $\sigma_{\text{log}}$ | Pratibimb Starter $(\mu_{c0}, \sigma_c)$ |
|---|---|---|---|---|---|---|
| **`social`** | 18,092 | 4.0 min | 19.6 min | $+0.96$ | $1.79$ | $\mu = 2.1, \sigma = 0.8$ (with chain multiplier) |
| **`messaging`** | 13,044 | 2.6 min | 15.5 min | $+0.62$ | $1.77$ | $\mu = 1.2, \sigma = 0.6$ |
| **`short_video`** | 2,534 | 1.1 min | 8.9 min | $-0.13$ | $1.80$ | $\mu = 2.4, \sigma = 0.8$ (with chain multiplier) |
| **`shopping_promo`** | 412 | 4.4 min | 18.3 min | $+1.07$ | $1.66$ | $\mu = 1.5, \sigma = 0.7$ |
| **`work_email`** | 3,396 | 0.7 min | 7.9 min | $-0.31$ | $1.78$ | $\mu = 0.8, \sigma = 0.5$ |
| **`news`** | Sparse | — | — | $+0.40$ | $1.60$ | $\mu = 1.2, \sigma = 0.6$ (imputed) |
| **`other`** | 32,892 | 2.1 min | 13.4 min | $+0.39$ | $1.82$ | $\mu = 1.0, \sigma = 0.7$ |

*Note on Chain Multiplier: LSApp records individual app sessions. In Pratibimb, opening a social notification during deep focus triggers app-switching cascades (e.g., Instagram $\rightarrow$ Reddit $\rightarrow$ YouTube). The chain multiplier scales $\mu_{c0}$ upwards to reflect full derailment duration before the phone goes idle for $\ge 2$ minutes.*

---

## 3. Seven Standardized Categories & App Package Mapping

```json
{
  "short_video": [
    "com.instagram.android", 
    "com.google.android.youtube", 
    "com.zhiliaoapp.musically"
  ],
  "social": [
    "com.facebook.katana", 
    "com.twitter.android", 
    "com.reddit.frontpage", 
    "com.linkedin.android"
  ],
  "messaging": [
    "com.whatsapp", 
    "org.telegram.messenger", 
    "com.discord", 
    "com.google.android.apps.messaging"
  ],
  "shopping_promo": [
    "com.amazon.mShop.android.shopping", 
    "com.flipkart.android", 
    "in.swiggy.android", 
    "com.application.zomato"
  ],
  "news": [
    "com.google.android.apps.magazines", 
    "com.inshorts"
  ],
  "work_email": [
    "com.google.android.gm", 
    "com.microsoft.office.outlook", 
    "com.slack"
  ],
  "other": []
}
```

---

## 4. Parameter Provenance for Pitch & Evaluation

| Parameter | Symbol | Source | Value / Prior | Provenance Label for Slides |
|---|---|---|---|---|
| Global Open Prob | $m_g$ | Ulster field study | $0.22$ ($n_g = 20$) | `literature value` |
| Category Minutes | $\mu_{c0}, \sigma_c$ | LSApp ($3.6M$ events) | Empirical session distribution | `real (public) × assumed` |
| Category Multipliers | $r_c$ | Persona configs | Social: 1.6, Promo: 0.4, Msg: 1.1 | `assumed starter` |
| State Multipliers | $r_s, \delta_s$ | Rule definitions | `FREE`: (1.0, 0), `FOCUS_STEADY`: (0.8, 0), `FOCUS_RESTLESS`: (1.3, +0.2) | `assumed starter` |
| Worth Weights | $w_k, b$ | Hand-set structural rules | $b = -1.5$, Conv: $+1.6$, Bait: $-1.2$, Urgency: $+1.5$ | `assumed starter` |
| Policy Constants | $\lambda, w, \pi^*, \varepsilon$ | Tuned on simulated true users | $\lambda=2.0, w=0.5, \pi^*=0.7, \varepsilon=0.1$ | `tuned (simulated)` |
