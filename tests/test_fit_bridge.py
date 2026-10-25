import copy
import json
from pathlib import Path
from typing import Any

import pytest

from contracts.params import PARAMS_DIR, load_draw_bank, load_params, validate_params
from fit.convert_params import DEFAULT_INPUTS, EXAMPLE_PATH, convert
from sim.persona_adapter import GENERATED_PERSONAS, adapt
from sim.simulator import ShiftedTrueUser, TwinLabSimulator

FITTED = {path.stem: json.loads(path.read_text(encoding="utf-8")) for path in DEFAULT_INPUTS}


@pytest.fixture(scope="module")
def example() -> dict[str, Any]:
    return load_params(EXAMPLE_PATH)


@pytest.mark.parametrize("name", sorted(FITTED))
def test_converted_fit_files_pass_the_contract_schema(name: str, example: dict[str, Any]) -> None:
    converted, _ = convert(FITTED[name], example, name)

    validate_params(converted)
    assert converted["draw_bank"] == load_draw_bank()


@pytest.mark.parametrize("name", sorted(FITTED))
def test_every_fitted_value_survives_conversion(name: str, example: dict[str, Any]) -> None:
    fitted = FITTED[name]
    converted, _ = convert(fitted, example, name)

    assert converted["pull"]["category"] == fitted["pull"]["category"]
    assert converted["pull"]["state"] == fitted["pull"]["state"]
    assert converted["persona"] == fitted["persona"]
    for key, value in fitted["policy"].items():
        assert converted["policy"][key] == value
    assert (
        converted["worth"]["weights"]["same_sender_repeat"]
        == fitted["worth"]["weights"]["same_sender_repeats_3m"]
    )


def test_conversion_reports_renames_fills_and_conflicts(example: dict[str, Any]) -> None:
    _, report = convert(FITTED["twin_params_student_coder"], example, "fit")

    assert "never_hold.otp_sources -> otp_source_packages" in report.renamed
    assert "pull.prior_weights.sigma" in report.filled
    assert "app_category_map.com.instagram.android: 'short_video' -> 'social'" in report.corrected
    assert any("'otp'" in warning for warning in report.warnings)


def test_lists_keep_fitted_entries_and_add_the_examples(example: dict[str, Any]) -> None:
    fitted = FITTED["twin_params_student_coder"]
    converted, _ = convert(fitted, example, "fit")
    otp_sources = converted["never_hold"]["otp_source_packages"]

    assert (
        otp_sources[: len(fitted["never_hold"]["otp_sources"])]
        == fitted["never_hold"]["otp_sources"]
    )
    assert set(example["never_hold"]["otp_source_packages"]) <= set(otp_sources)


def test_conversion_never_changes_its_input(example: dict[str, Any]) -> None:
    fitted = FITTED["twin_params_student_coder"]
    before = copy.deepcopy(fitted)

    convert(fitted, example, "fit")

    assert fitted == before


@pytest.mark.parametrize("persona", ["student_coder", "night_shift_nurse"])
def test_written_params_files_are_the_current_conversion(
    persona: str, example: dict[str, Any]
) -> None:
    on_disk = load_params(PARAMS_DIR / f"twin_params_{persona}.json")
    source = f"fit/twin_params_{persona}.json"
    fresh, _ = convert(FITTED[f"twin_params_{persona}"], example, source)

    assert on_disk == json.loads(json.dumps(fresh))


def test_adapter_maps_every_persona_field_the_simulator_reads() -> None:
    source = json.loads(Path(GENERATED_PERSONAS).read_text(encoding="utf-8"))
    adapted = adapt(source)["personas"]
    original = {p["persona"]: p for p in source["personas"]}

    assert set(adapted) == set(original)
    for name, persona in adapted.items():
        assert persona["category_pull_multipliers"] == original[name]["pull"]["r_c"]
        assert persona["latent_importance_rates"] == original[name]["important_share"]
        assert (
            persona["state_multipliers"]["FOCUS_RESTLESS"]["r_s"]
            == (original[name]["pull"]["r_s"]["FOCUS_RESTLESS"])
        )
        assert set(persona["category_minutes"]["social"]) == {"mu", "sigma"}


def test_personas_with_params_take_minutes_from_their_own_file() -> None:
    source = json.loads(Path(GENERATED_PERSONAS).read_text(encoding="utf-8"))
    adapted = adapt(source)["personas"]

    assert adapted["student_coder"]["category_minutes_source"] == "twin_params_student_coder.json"
    assert adapted["content_creator"]["category_minutes_source"] == "twin_params_example.json"


def test_the_simulator_runs_on_the_adapted_personas(tmp_path: Path) -> None:
    source = json.loads(Path(GENERATED_PERSONAS).read_text(encoding="utf-8"))
    personas_file = tmp_path / "personas.json"
    personas_file.write_text(json.dumps(adapt(source)))

    simulator = TwinLabSimulator(str(personas_file), str(tmp_path / "missing_draw_bank.json"))
    for persona in simulator.personas.values():
        events = simulator.simulate_event_stream(ShiftedTrueUser(persona, seed=1), 50, seed=2)
        assert len(events) == 50


def test_app_categories_follow_the_contract_map(example: dict[str, Any]) -> None:
    converted, _ = convert(FITTED["twin_params_student_coder"], example, "fit")

    assert converted["app_category_map"]["com.instagram.android"] == "social"
    for package, category in example["app_category_map"].items():
        assert converted["app_category_map"][package] == category
