"""
Draw Bank Generator for Pratibimb.
Generates 400 standard-normal triples (z1, z2, z3) using a fixed seed (2026).
"""

import json
import numpy as np
import os

def generate_draw_bank(n_draws=400, seed=2026):
    rng = np.random.default_rng(seed)
    draws = rng.standard_normal((n_draws, 3)).tolist()
    draws_rounded = [[round(val, 6) for val in triple] for triple in draws]
    return draws_rounded

if __name__ == "__main__":
    bank = generate_draw_bank(400, seed=2026)
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    output_path = os.path.join(base_dir, "fit", "draw_bank.json")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump({"draw_bank": bank}, f, indent=2)
    print(f"Generated {len(bank)} draw triples in {output_path}")
