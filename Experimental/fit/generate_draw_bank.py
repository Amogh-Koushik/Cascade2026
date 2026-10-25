"""
Draw Bank Generator for Pratibimb.
Generates 400 standard-normal triples (z1, z2, z3) using a fixed seed (42).
This bank is embedded into twin_params.json and shared identically across
Python (engine.py) and Kotlin (TwinEngine.kt) for exact reproducibility.
"""

import json
import numpy as np

def generate_draw_bank(n_draws=400, seed=42):
    rng = np.random.default_rng(seed)
    # Generate 400 triples of standard normal N(0, 1)
    draws = rng.standard_normal((n_draws, 3)).tolist()
    # Round to 6 decimal places for clean, cross-language precision
    draws_rounded = [[round(val, 6) for val in triple] for triple in draws]
    return draws_rounded

if __name__ == "__main__":
    bank = generate_draw_bank(400, seed=42)
    output_path = "fit/draw_bank.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump({"draw_bank": bank}, f, indent=2)
    print(f"Generated {len(bank)} draw triples in {output_path}")
