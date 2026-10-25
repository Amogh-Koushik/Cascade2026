# Generates the shared draw bank: a fixed set of standard-normal triples (z1, z2, z3).
# engine.py and TwinEngine.kt both read this one file instead of drawing their own random
# numbers, so Python and Kotlin sample exactly the same futures and their outputs can be
# compared number for number in the test vectors.
#
# Run: uv run python -m contracts.make_draw_bank

import argparse
import json
import math
from pathlib import Path

import numpy as np

SEED = 2026
SIZE = 400
DECIMALS = 6
OUTPUT_PATH = Path(__file__).with_name("draw_bank.json")


def generate_draws(seed: int = SEED, size: int = SIZE) -> list[list[float]]:
    rng = np.random.default_rng(seed)
    draws = rng.standard_normal((size, 3))
    # Rounded so the JSON is readable and both languages parse the same short decimals
    # into the same doubles.
    return [[round(float(z), DECIMALS) for z in row] for row in draws]


def build_draw_bank(seed: int = SEED, size: int = SIZE) -> dict[str, object]:
    return {
        "seed": seed,
        "size": size,
        "columns": ["z1_pull", "z2_minutes", "z3_worth"],
        "draws": generate_draws(seed, size),
    }


def summarise(draws: list[list[float]]) -> list[tuple[float, float]]:
    # Mean and standard deviation of each column, used as a quick sanity check.
    columns = list(zip(*draws, strict=True))
    stats = []
    for column in columns:
        mean = sum(column) / len(column)
        variance = sum((z - mean) ** 2 for z in column) / (len(column) - 1)
        stats.append((mean, math.sqrt(variance)))
    return stats


def main() -> None:
    parser = argparse.ArgumentParser(description="Write the shared draw bank JSON.")
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--size", type=int, default=SIZE)
    parser.add_argument("--output", type=Path, default=OUTPUT_PATH)
    args = parser.parse_args()

    bank = build_draw_bank(args.seed, args.size)
    args.output.write_text(json.dumps(bank, indent=1) + "\n", encoding="utf-8")

    print(f"Wrote {args.size} draws (seed {args.seed}) to {args.output}")
    for name, (mean, std) in zip(bank["columns"], summarise(bank["draws"]), strict=True):
        print(f"  {name}: mean {mean:+.3f}, std {std:.3f}")


if __name__ == "__main__":
    main()
