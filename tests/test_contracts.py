import copy
import json
import re
from pathlib import Path
from typing import Any

import pytest

from contracts.make_example_params import OUTPUT_PATH as EXAMPLE_PARAMS_PATH
from contracts.make_example_params import build_example_params
from contracts.make_sample_decisions import OUTPUT_PATH as SAMPLES_PATH
from contracts.make_sample_decisions import build_scenarios
from contracts.params import ParamsError, load_draw_bank, load_params, validate_params

KOTLIN_CONTRACT = (
    Path(__file__).resolve().parent.parent
    / "contracts"
    / "kotlin"
    / "com"
    / "pratibimb"
    / "twin"
    / "TwinEngine.kt"
)

# Comments are stripped first: they contain parentheses that would confuse the regexes below.
KOTLIN_SOURCE = re.sub(r"//[^\n]*", "", KOTLIN_CONTRACT.read_text(encoding="utf-8"))


def kotlin_fields(class_name: str) -> list[str]:
    match = re.search(rf"data class {class_name}\((.*?)\)", KOTLIN_SOURCE, re.DOTALL)
    assert match, f"data class {class_name} not found in TwinEngine.kt"
    return re.findall(r"val (\w+):", match.group(1))


def kotlin_enum(enum_name: str) -> list[str]:
    match = re.search(
        rf"enum class {enum_name}(?:\([^)]*\))?\s*\{{(.*?)\}}", KOTLIN_SOURCE, re.DOTALL
    )
    assert match, f"enum class {enum_name} not found in TwinEngine.kt"
    # A constant starts the block or follows a comma, on one line or many.
    return re.findall(r"(?:^|,)\s*([A-Z][A-Z0-9_]*)\b", match.group(1).strip())


def kotlin_category_keys() -> list[str]:
    return re.findall(r'[A-Z_]+\("([a-z_]+)"\)', KOTLIN_SOURCE)


def json_key(kotlin_name: str) -> str:
    return re.sub(r"([A-Z])", r"_\1", kotlin_name).lower()


@pytest.fixture(scope="module")
def example_params() -> dict[str, Any]:
    return load_params(EXAMPLE_PARAMS_PATH)


@pytest.fixture(scope="module")
def scenarios() -> list[dict[str, Any]]:
    return json.loads(SAMPLES_PATH.read_text(encoding="utf-8"))["scenarios"]


def test_json_key_rule_matches_the_documented_examples() -> None:
    assert json_key("lP10") == "l_p10"
    assert json_key("holdBetterPerHundred") == "hold_better_per_hundred"
    assert json_key("minsIfOpenedLo") == "mins_if_opened_lo"


def test_example_params_file_is_up_to_date_and_valid(example_params: dict[str, Any]) -> None:
    assert example_params == build_example_params()


def test_example_params_embed_the_shared_draw_bank(example_params: dict[str, Any]) -> None:
    assert example_params["draw_bank"] == load_draw_bank()


def test_kotlin_categories_match_the_schema_and_params(example_params: dict[str, Any]) -> None:
    schema = json.loads(Path("contracts/twin_params.schema.json").read_text(encoding="utf-8"))
    schema_categories = schema["$defs"]["category"]["enum"]

    assert kotlin_category_keys() == schema_categories
    assert sorted(example_params["pull"]["category"]) == sorted(schema_categories)


def test_kotlin_phone_states_match_the_params(example_params: dict[str, Any]) -> None:
    assert kotlin_enum("PhoneState") == list(example_params["pull"]["state"])


def _broken(path: list[str], value: Any) -> dict[str, Any]:
    params = copy.deepcopy(build_example_params())
    target = params
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value
    return params


@pytest.mark.parametrize(
    ("path", "value"),
    [
        (["app_category_map", "com.example"], "games"),
        (["pull", "global", "m_g"], 1.4),
        (["policy", "epsilon"], -0.1),
        (["policy", "repeat_ping", "count"], 1),
        (["draw_bank"], [[0.0, 0.0, 0.0]] * 399),
        (["persona"], "Night Shift"),
        (["worth", "weights", "unknown_feature"], 1.0),
    ],
)
def test_schema_rejects_bad_params(path: list[str], value: Any) -> None:
    with pytest.raises(ParamsError):
        validate_params(_broken(path, value))


def test_schema_rejects_params_missing_a_category() -> None:
    params = copy.deepcopy(build_example_params())
    del params["pull"]["category"]["news"]

    with pytest.raises(ParamsError):
        validate_params(params)


def test_sample_decisions_file_is_up_to_date(scenarios: list[dict[str, Any]]) -> None:
    assert scenarios == json.loads(json.dumps(build_scenarios()))


def test_there_are_ten_uniquely_named_placeholder_scenarios(
    scenarios: list[dict[str, Any]],
) -> None:
    assert len(scenarios) == 10
    assert len({scenario["id"] for scenario in scenarios}) == 10
    assert all(scenario["placeholder"] for scenario in scenarios)


@pytest.mark.parametrize(
    ("section", "kotlin_class"),
    [
        ("features", "NotificationFeatures"),
        ("context", "TwinContext"),
        ("decision", "Decision"),
    ],
)
def test_sample_keys_match_the_kotlin_types(
    scenarios: list[dict[str, Any]], section: str, kotlin_class: str
) -> None:
    expected = [json_key(name) for name in kotlin_fields(kotlin_class)]
    for scenario in scenarios:
        assert list(scenario[section]) == expected, scenario["id"]


def test_sample_card_keys_match_the_kotlin_future_card(scenarios: list[dict[str, Any]]) -> None:
    expected = [json_key(name) for name in kotlin_fields("FutureCard")]
    for scenario in scenarios:
        assert list(scenario["decision"]["card"]) == expected, scenario["id"]


def test_sample_enum_values_exist_in_kotlin(scenarios: list[dict[str, Any]]) -> None:
    categories = kotlin_category_keys()
    for scenario in scenarios:
        decision, context = scenario["decision"], scenario["context"]
        assert scenario["features"]["category"] in categories
        assert decision["action"] in kotlin_enum("Action")
        assert decision["would_be_action"] in kotlin_enum("Action")
        assert decision["rule"] in kotlin_enum("DecisionRule")
        assert context["state"] in kotlin_enum("PhoneState")
        assert context["autonomy"] in kotlin_enum("Autonomy")
        assert context["fatigue_risk"] in kotlin_enum("FatigueRisk")


def test_sample_ranges_are_ordered_and_consistent(scenarios: list[dict[str, Any]]) -> None:
    for scenario in scenarios:
        card, context = scenario["decision"]["card"], scenario["context"]
        name = scenario["id"]
        assert card["pull_lo"] <= card["pull_mean"] <= card["pull_hi"], name
        assert card["mins_if_opened_lo"] <= card["mins_if_opened_hi"], name
        assert card["l_p10"] <= card["l_p50"] <= card["l_p90"], name
        assert card["h_p10"] <= card["h_p50"] <= card["h_p90"], name
        assert card["q_lo"] <= card["q_mean"] <= card["q_hi"], name
        assert card["q_mean"] <= card["q_upper80"], name
        assert 0 <= card["hold_better_per_hundred"] <= 100, name
        assert card["net_minutes_protected"] == round(card["l_mean"] - card["h_mean"], 2), name
        assert card["minutes_until_release"] == context["minutes_until_release"], name
        assert card["fatigue_risk"] == context["fatigue_risk"], name


def test_sample_low_confidence_follows_the_width_rule(
    scenarios: list[dict[str, Any]], example_params: dict[str, Any]
) -> None:
    width = example_params["policy"]["low_confidence_width"]
    for scenario in scenarios:
        card = scenario["decision"]["card"]
        wide = (card["pull_hi"] - card["pull_lo"]) > width or (card["q_hi"] - card["q_lo"]) > width
        assert card["low_confidence"] == wide, scenario["id"]


def test_sample_decisions_follow_the_decision_rule(
    scenarios: list[dict[str, Any]], example_params: dict[str, Any]
) -> None:
    policy = example_params["policy"]
    for scenario in scenarios:
        decision, context = scenario["decision"], scenario["context"]
        card, rule, name = decision["card"], decision["rule"], scenario["id"]

        assert (decision["action"] == "HOLD") == (rule == "HOLD_BETTER"), name
        assert (context["state"] == "FREE") == (rule == "FREE_STATE"), name
        if rule == "MIGHT_MATTER":
            assert card["q_upper80"] >= policy["might_matter_upper80"], name
        if rule == "HOLD_BETTER":
            assert card["q_upper80"] < policy["might_matter_upper80"], name
            assert card["hold_better_per_hundred"] >= 100 * policy["pi_star"], name
        if rule == "DELIVER_BETTER":
            assert card["hold_better_per_hundred"] < 100 * policy["pi_star"], name


def test_sample_propensity_follows_the_exploration_rule(
    scenarios: list[dict[str, Any]], example_params: dict[str, Any]
) -> None:
    policy = example_params["policy"]
    for scenario in scenarios:
        decision = scenario["decision"]
        eligible = (
            scenario["context"]["autonomy"] == "AUTO"
            and decision["action"] == "HOLD"
            and decision["card"]["q_upper80"] < policy["explore_upper80"]
        )
        expected = round(1 - policy["epsilon"], 2) if eligible else 1.0
        assert decision["propensity"] == expected, scenario["id"]


def test_sample_explanations_are_under_25_words(scenarios: list[dict[str, Any]]) -> None:
    for scenario in scenarios:
        assert len(scenario["decision"]["explanation"].split()) < 25, scenario["id"]
