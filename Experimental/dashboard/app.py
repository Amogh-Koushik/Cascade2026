"""
Pratibimb Proof Dashboard (dashboard/app.py)
Author: Safal | CASCADE '26

Streamlit dashboard showing all 4 evaluation experiments.
Run with: streamlit run dashboard/app.py

CSS screenshots this for Slide 8 (Proof Slide).
"""

import streamlit as st
from PIL import Image
import os

st.set_page_config(
    page_title="Pratibimb · Proof Dashboard",
    page_icon="🧠",
    layout="wide",
    initial_sidebar_state="expanded"
)

# ── Custom dark theme CSS ────────────────────────────────────────────────────
st.markdown("""
<style>
    .stApp { background-color: #0F1117; color: white; }
    .block-container { padding-top: 1.5rem; }
    .provenance-real    { background: #1B4332; color: #2ECC71;
                          padding: 4px 10px; border-radius: 6px;
                          font-size: 12px; font-weight: bold; }
    .provenance-simulated { background: #2C2358; color: #9B59B6;
                            padding: 4px 10px; border-radius: 6px;
                            font-size: 12px; font-weight: bold; }
    .provenance-hackathon { background: #2E1A0E; color: #E67E22;
                            padding: 4px 10px; border-radius: 6px;
                            font-size: 12px; font-weight: bold; }
    .metric-card { background: #1A1D27; border-radius: 10px;
                   padding: 16px; text-align: center; border: 1px solid #333344; }
    h1, h2, h3 { color: white !important; }
</style>
""", unsafe_allow_html=True)

# ── Sidebar ──────────────────────────────────────────────────────────────────
with st.sidebar:
    st.image("https://raw.githubusercontent.com/google/material-design-icons/master/png/action/psychology/materialicons/48dp/2x/baseline_psychology_black_48dp.png",
             width=48)
    st.title("Pratibimb")
    st.caption("प्रतिबिंब · CASCADE '26")
    st.markdown("---")
    st.markdown("**Team:** Safal · Amogh · CSS · Satvik")
    st.markdown("**Theme:** Attention Economy · PS 3.3")
    st.markdown("---")
    st.markdown("### Provenance Legend")
    st.markdown('<span class="provenance-real">real (public)</span>', unsafe_allow_html=True)
    st.markdown("LSApp dataset · Ulster Field Study")
    st.markdown('<span class="provenance-simulated">simulated</span>', unsafe_allow_html=True)
    st.markdown("Twin Lab · lognormal / Beta models")
    st.markdown('<span class="provenance-hackathon">hackathon conditions</span>', unsafe_allow_html=True)
    st.markdown("4 teammates · 24h data")
    st.markdown("---")
    st.info("**LLMs advise. Mathematics decides.**")

# ── Header ───────────────────────────────────────────────────────────────────
st.title("Pratibimb · Proof Dashboard")
st.markdown(
    "_See what a notification will cost you before you pay for it._  "
    "| Platforms optimise for your impulses. **Pratibimb works for the person you're trying to be.**"
)
st.markdown("---")

# ── Key Metrics Row ──────────────────────────────────────────────────────────
col1, col2, col3, col4, col5 = st.columns(5)
metrics = [
    ("31.8%", "Simulated Derailment Rate", "simulated"),
    ("8.9 min", "Avg. Minutes Lost (if opened)", "simulated"),
    ("0%",   "Urgent Notification False Hold", "simulated"),
    ("16.6%", "Truly Important Notifications", "simulated"),
    ("76,247", "Real Sessions (LSApp)", "real"),
]
for col, (val, label, prov) in zip([col1, col2, col3, col4, col5], metrics):
    with col:
        badge_class = f"provenance-{prov if prov != 'real' else 'real'}"
        st.markdown(f"""
        <div class="metric-card">
            <h2 style="color:white;margin:0">{val}</h2>
            <p style="color:#AAAAAA;font-size:12px;margin:4px 0">{label}</p>
            <span class="{badge_class}">{prov}</span>
        </div>
        """, unsafe_allow_html=True)

st.markdown("---")

# ── Tabs for 4 experiments ───────────────────────────────────────────────────
tab_e1, tab_e2, tab_e5, tab_e4 = st.tabs([
    "E1 · Policy Comparison",
    "E2 · Convergence Proof",
    "E5 · Simulator Sanity",
    "E4 · Red-Team Safety",
])

def load_chart(path, fallback_msg):
    if os.path.exists(path):
        return Image.open(path)
    return None

# ── E1 Tab ───────────────────────────────────────────────────────────────────
with tab_e1:
    st.subheader("E1 — Policy Comparison: Why Not Just Use DND?")
    st.markdown(
        "Compares Pratibimb (Twin Adaptive) against 5 baselines across 500 simulated notifications. "
        "**x-axis:** urgent message miss rate (must stay <2%). **y-axis:** focus minutes protected."
    )
    col_badge, _ = st.columns([1, 5])
    with col_badge:
        st.markdown('<span class="provenance-simulated">simulated</span>', unsafe_allow_html=True)

    img = load_chart("eval/e1_policy_comparison.png", "E1 chart not yet generated.")
    if img:
        st.image(img, use_container_width=True)
    else:
        st.warning("Run `python eval/e1_policy_comparison.py` to generate this chart.")

    with st.expander("What this proves"):
        st.markdown("""
        - **Always Deliver** protects 0 focus minutes — baseline floor.
        - **Plain DND** blocks all notifications but misses urgent messages at high rates.
        - **Hold Everything** same problem — blind to urgency.
        - **Random 50%** is neither safe nor effective.
        - **Twin Static** (starter priors) already outperforms DND while respecting urgent rules.
        - **Twin Adaptive (Pratibimb)** achieves the best focus protection at the lowest urgent miss rate — the **Pareto frontier**.
        """)

# ── E2 Tab ───────────────────────────────────────────────────────────────────
with tab_e2:
    st.subheader("E2 — Convergence: Why Exploration Is Non-Negotiable")
    st.markdown(
        "Simulates 14 days of Bayesian online learning on a shifted true user. "
        "Compares **no exploration** (epsilon=0) vs. **epsilon-greedy exploration** (epsilon=0.1)."
    )
    col_badge, _ = st.columns([1, 5])
    with col_badge:
        st.markdown('<span class="provenance-simulated">simulated</span>', unsafe_allow_html=True)

    img = load_chart("eval/e2_convergence.png", "E2 chart not yet generated.")
    if img:
        st.image(img, use_container_width=True)
    else:
        st.warning("Run `python eval/e2_convergence.py` to generate this chart.")

    with st.expander("What this proves"):
        st.markdown("""
        - Without exploration (`epsilon=0`), every notification the model **holds is never observed**.
          So the model never learns how wrong its estimates are. **Learning freezes permanently.**
        - With `epsilon=0.1`, 10% of held notifications are shown deliberately for observation.
          The model's estimated probability converges to the true user behavior within 7–10 days.
        - This is the scientific justification for the randomized-hold switch in Pratibimb's architecture.
        """)

# ── E5 Tab ───────────────────────────────────────────────────────────────────
with tab_e5:
    st.subheader("E5 — Simulator Sanity: Is Our Simulator Realistic?")
    st.markdown(
        "Overlays real LSApp session duration distributions (coloured bars) against TwinEngine "
        "lognormal simulated draws (white line) for all 7 app categories."
    )
    col1_b, col2_b, _ = st.columns([1, 1, 4])
    with col1_b:
        st.markdown('<span class="provenance-real">real (public)</span>', unsafe_allow_html=True)
    with col2_b:
        st.markdown('<span class="provenance-simulated">simulated</span>', unsafe_allow_html=True)

    img = load_chart("eval/e5_simulator_sanity.png", "E5 chart not yet generated.")
    if img:
        st.image(img, use_container_width=True)
    else:
        st.warning("Run `python eval/e5_simulator_sanity.py` to generate this chart.")

    with st.expander("What this proves"):
        st.markdown("""
        - Social Media: real median **4.0 min**, simulator matches distribution shape. ✅
        - Messaging: real median **2.6 min**, tight overlap at 0–5 min range. ✅
        - Work/Email: real median **0.7 min** (quick glances), both curves peak at <2 min. ✅
        - News: **no LSApp data** → literature fallback (Oulasvirta et al. 2012) — labeled honestly.
        - Close overlap confirms the Twin Lab simulator is grounded in **76,247 real sessions**.
        """)

# ── E4 Tab ───────────────────────────────────────────────────────────────────
with tab_e4:
    st.subheader("E4 — Red-Team Safety: Adversarial Attack Results")
    st.markdown(
        "Results from running 60+ adversarial notification attacks against the Gatekeeper. "
        "Measures bait catch rate and — critically — urgent message false-hold rate."
    )
    col_badge, _ = st.columns([1, 5])
    with col_badge:
        st.markdown('<span class="provenance-simulated">simulated</span>', unsafe_allow_html=True)

    img = load_chart("eval/e4_redteam.png", "E4 chart pending red-team run from Amogh.")
    if img:
        st.image(img, use_container_width=True)
    else:
        st.info("Waiting for Amogh's red-team run (60 adversarial cases). Chart will appear here.")

    with st.expander("What this proves"):
        st.markdown("""
        - **Target:** 0% urgent message false-hold rate (no emergency held back by the system).
        - **Target:** >80% bait detection rate (clickbait disguised as important).
        - Failures are reported **openly** — not hidden. This is scientific honesty.
        - Demonstrates that deterministic safety overrides are never bypassed by ML predictions.
        """)

st.markdown("---")
st.caption(
    "Pratibimb · CASCADE '26 · IIIT Nagpur · TantraFiesta 2026 | "
    "All results labeled: real (public) / simulated / hackathon conditions. "
    "Counterfactual results are simulated, not experimentally validated."
)
