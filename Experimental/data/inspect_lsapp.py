"""
Inspect LSApp dataset:
1. Extract list of unique apps and sample session durations
2. Map to the 7 Pratibimb categories
3. Calculate empirical session duration statistics (mu_c0, sigma_c) in log-minutes
"""

import tarfile
import csv
from collections import defaultdict
from datetime import datetime
import numpy as np

CATEGORY_MAP = {
    # Short video / video
    "YouTube": "short_video",
    "TikTok": "short_video",
    "Netflix": "short_video",
    # Social
    "Facebook": "social",
    "Instagram": "social",
    "Twitter": "social",
    "Reddit": "social",
    "Pinterest": "social",
    "Snapchat": "social",
    # Messaging
    "WhatsApp": "messaging",
    "Messenger": "messaging",
    "Telegram": "messaging",
    "Hangouts": "messaging",
    "Messages": "messaging",
    # Shopping / Promo
    "Amazon": "shopping_promo",
    "eBay": "shopping_promo",
    # News
    "News": "news",
    "BBC News": "news",
    # Work / Email
    "Gmail": "work_email",
    "Outlook": "work_email",
    "Slack": "work_email",
    "Drive": "work_email",
    "Docs": "work_email"
}

def analyze_lsapp():
    apps = set()
    session_events = defaultdict(list)
    
    print("Reading LSApp dataset from tar.gz...")
    with tarfile.open("data/LSApp/lsapp.tsv.gz", "r:gz") as tar:
        f = tar.extractfile("lsapp.tsv")
        # Read header
        header = f.readline().decode("utf-8").strip().split("\t")
        print("Header:", header)
        
        row_count = 0
        for line in f:
            row_count += 1
            parts = line.decode("utf-8", errors="ignore").strip().split("\t")
            if len(parts) >= 5:
                user_id, session_id, ts_str, app_name, event_type = parts[:5]
                apps.add(app_name)
                # Parse timestamp
                try:
                    ts = datetime.strptime(ts_str, "%Y-%m-%d %H:%M:%S")
                    session_key = (user_id, session_id)
                    session_events[session_key].append((ts, app_name, event_type))
                except Exception:
                    pass
            if row_count % 100000 == 0:
                print(f"Processed {row_count} rows...")

    print(f"Total rows: {row_count}, Unique apps: {len(apps)}, Total sessions: {len(session_events)}")
    
    # Calculate session lengths per category
    category_durations = defaultdict(list)
    
    for (user_id, session_id), events in session_events.items():
        if len(events) < 2:
            continue
        start_time = events[0][0]
        end_time = events[-1][0]
        duration_sec = (end_time - start_time).total_seconds()
        duration_min = max(0.1, duration_sec / 60.0) # minimum 6 seconds
        
        # Determine dominant app in session
        app_counts = defaultdict(int)
        for _, app_name, _ in events:
            app_counts[app_name] += 1
        top_app = max(app_counts, key=app_counts.get)
        
        cat = "other"
        for key, val in CATEGORY_MAP.items():
            if key.lower() in top_app.lower():
                cat = val
                break
        
        # Cap at 60 mins per PRD definition
        capped_duration = min(60.0, duration_min)
        category_durations[cat].append(capped_duration)
    
    print("\n--- Empirical Category Duration Stats (Log-Minutes) ---")
    summary = {}
    for cat in ["short_video", "social", "messaging", "shopping_promo", "news", "work_email", "other"]:
        durations = category_durations[cat]
        if durations:
            log_durations = np.log(durations)
            mu = float(np.mean(log_durations))
            sigma = float(np.std(log_durations))
            median = float(np.median(durations))
            p90 = float(np.percentile(durations, 90))
            summary[cat] = {
                "count": len(durations),
                "mu_log": round(mu, 2),
                "sigma_log": round(sigma, 2),
                "median_min": round(median, 2),
                "p90_min": round(p90, 2)
            }
            print(f"Category: {cat:15} | Count: {len(durations):5} | mu: {mu:+.2f} | sigma: {sigma:.2f} | Median: {median:.1f}m | 90th: {p90:.1f}m")
        else:
            print(f"Category: {cat:15} | No data, using defaults")
            
    return apps, summary

if __name__ == "__main__":
    apps, summary = analyze_lsapp()
