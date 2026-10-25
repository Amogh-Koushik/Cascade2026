import json
import math
from pathlib import Path

from contracts.make_draw_bank import SIZE, build_draw_bank, generate_draws, summarise


def test_same_seed_gives_identical_draws() -> None:
    assert generate_draws(seed=7) == generate_draws(seed=7)


def test_different_seeds_give_different_draws() -> None:
    assert generate_draws(seed=7) != generate_draws(seed=8)


def test_bank_has_400_finite_triples() -> None:
    draws = build_draw_bank()["draws"]

    assert len(draws) == SIZE
    assert all(len(row) == 3 for row in draws)
    assert all(math.isfinite(z) for row in draws for z in row)


def test_each_column_looks_standard_normal() -> None:
    for mean, std in summarise(generate_draws()):
        assert abs(mean) < 0.15
        assert abs(std - 1.0) < 0.15


def test_bank_survives_a_json_round_trip_unchanged(tmp_path: Path) -> None:
    bank = build_draw_bank()
    path = tmp_path / "draw_bank.json"
    path.write_text(json.dumps(bank))

    assert json.loads(path.read_text()) == bank
