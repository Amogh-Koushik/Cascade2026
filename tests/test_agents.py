import json
import re
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest

from agents.checkpoint import Checkpoint, progress_path
from agents.json_generation import GenerationError, extract_json, generate_json
from agents.llm_errors import LLMError
from agents.persona_agent import generate_personas, persona_prompt
from agents.persona_briefs import PERSONA_BRIEFS, PERSONA_NAMES
from agents.persona_validator import validate_personas
from agents.redteam_agent import ATTACKS_PER_TYPE, generate_redteam_set
from agents.redteam_runner import run_attacks
from agents.redteam_schema import ATTACK_TYPES, validate_attack_set
from agents.text_bank import TextBankError, validate_items
from agents.text_bank_agent import BATCHES, generate_text_bank
from tests.builders import valid_persona


def fixed_clock() -> datetime:
    return datetime(2026, 10, 25, 12, 0, tzinfo=UTC)


class ScriptedLLM:
    # A stand-in for Ollama: replies come from a function of the prompt, and every call is kept.
    def __init__(self, reply: Callable[[str, int], str]) -> None:
        self.reply = reply
        self.prompts: list[str] = []

    def __call__(self, system: str, prompt: str) -> str:
        self.prompts.append(prompt)
        return self.reply(prompt, len(self.prompts))


def accept_positive(value: Any) -> int:
    if not isinstance(value, dict) or not isinstance(value.get("n"), int) or value["n"] <= 0:
        raise ValueError("n must be a positive integer")
    return value["n"]


def test_a_valid_first_reply_is_used_as_is() -> None:
    llm = ScriptedLLM(lambda prompt, call: '{"n": 3}')

    assert generate_json(llm, "sys", "give n", accept_positive, "test") == 3
    assert len(llm.prompts) == 1


def test_a_rejected_reply_is_retried_with_the_problems_listed() -> None:
    llm = ScriptedLLM(lambda prompt, call: '{"n": -1}' if call == 1 else '{"n": 4}')

    assert generate_json(llm, "sys", "give n", accept_positive, "test") == 4
    assert "n must be a positive integer" in llm.prompts[1]
    assert llm.prompts[1].startswith("give n")


def test_generation_gives_up_after_the_last_attempt() -> None:
    llm = ScriptedLLM(lambda prompt, call: "not json at all")

    with pytest.raises(GenerationError, match="no valid reply after 3 attempts"):
        generate_json(llm, "sys", "give n", accept_positive, "test")
    assert len(llm.prompts) == 3


def test_json_is_found_inside_fences_and_chatter() -> None:
    assert extract_json('Sure!\n```json\n{"n": 1}\n```') == {"n": 1}
    with pytest.raises(json.JSONDecodeError):
        extract_json("no braces here")


def persona_reply(prompt: str, call: int) -> str:
    name = re.search(r"Persona name: (\w+)", prompt)
    assert name
    persona = valid_persona(name.group(1))
    persona["pull"]["r_c"]["social"] = 9.0
    return json.dumps(persona)


def test_the_persona_agent_writes_all_three_validated_personas() -> None:
    llm = ScriptedLLM(persona_reply)

    document = generate_personas(llm, clock=fixed_clock)

    assert [p["persona"] for p in document["personas"]] == list(PERSONA_NAMES)
    assert validate_personas(document)
    assert document["created_at"] == "2026-10-25T12:00:00+00:00"
    assert document["personas"][0]["pull"]["r_c"]["social"] == 3.0
    assert document["clamped"]["student_coder"][0]["original"] == 9.0


def test_the_persona_prompt_carries_the_brief_and_the_ranges() -> None:
    prompt = persona_prompt("night_shift_nurse")

    assert PERSONA_BRIEFS["night_shift_nurse"] in prompt
    assert "0.2 to 3" in prompt
    assert "exactly 24 numbers" in prompt


def text_bank_reply(prompt: str, call: int) -> str:
    theme = re.search(r"for this theme: (.+?)\.\n", prompt)
    package = re.search(r"- [^:\n]+: ([\w.]+)", prompt)
    assert theme and package
    items = [
        {
            "package_name": package.group(1),
            "title": f"{theme.group(1)} {index}",
            "text": f"Message number {index}",
            "sender": None,
            "language": "hinglish" if index % 3 == 0 else "english",
            "is_conversation": False,
            "group_chat": False,
            "important": index % 5 == 0,
            "bait": index % 4 == 0 and index % 5 != 0,
            "urgency": index % 7 == 0,
        }
        for index in range(25)
    ]
    return json.dumps({"items": items})


def test_the_text_bank_agent_writes_250_labelled_items(params: dict[str, Any]) -> None:
    document = generate_text_bank(ScriptedLLM(text_bank_reply), params, clock=fixed_clock)
    items = document["items"]

    assert len(items) == sum(batch.count for batch in BATCHES) == 250
    assert [item["id"] for item in items[:2]] == ["tb001", "tb002"]
    assert items[-1]["id"] == "tb250"
    assert {item["source"] for item in items} == {"llm"}
    whatsapp = next(item for item in items if item["package_name"] == "com.whatsapp")
    assert (whatsapp["app"], whatsapp["category"]) == ("WhatsApp", "messaging")


def test_a_batch_that_repeats_an_earlier_one_is_sent_back(params: dict[str, Any]) -> None:
    calls: list[int] = []

    def reply(prompt: str, call: int) -> str:
        calls.append(call)
        # The second batch first comes back with the first batch's titles.
        if call == 2:
            return text_bank_reply(prompt.replace(BATCHES[1].theme, BATCHES[0].theme), call)
        return text_bank_reply(prompt, call)

    document = generate_text_bank(ScriptedLLM(reply), params, clock=fixed_clock)

    assert len(document["items"]) == 250
    assert len(calls) == len(BATCHES) + 1


@pytest.mark.parametrize(
    ("change", "problem"),
    [
        (lambda item: item.update(package_name="com.fake.app"), "listed apps"),
        (lambda item: item.update(bait=True, important=True), "both bait and important"),
        (
            lambda item: item.update(is_conversation=False, group_chat=True),
            "group chat must also be a conversation",
        ),
        (lambda item: item.update(language="tamil"), "language"),
        (lambda item: item.update(urgency="maybe"), "true or false"),
        (lambda item: item.pop("sender"), "missing 'sender'"),
    ],
)
def test_bad_text_bank_items_are_rejected(change: Any, problem: str) -> None:
    item = {
        "package_name": "com.whatsapp",
        "title": "Mom",
        "text": "Dinner at 8?",
        "sender": "Mom",
        "language": "english",
        "is_conversation": True,
        "group_chat": False,
        "important": False,
        "bait": False,
        "urgency": False,
    }
    change(item)

    with pytest.raises(TextBankError) as error:
        validate_items([item], frozenset({"com.whatsapp"}), set(), 1)

    assert any(problem in p for p in error.value.problems)


def test_a_short_batch_is_rejected() -> None:
    with pytest.raises(TextBankError, match="need 25 items, got 0"):
        validate_items([], frozenset({"com.whatsapp"}), set(), 25)


def redteam_reply(prompt: str, call: int) -> str:
    attacks = [
        {
            "description": f"attack {index}",
            "notifications": [
                {
                    "at_min": 0,
                    "package_name": "in.swiggy.android",
                    "title": f"Offer {call}-{index}",
                    "text": "Last chance, 50% off",
                    "sender": None,
                    "is_conversation": False,
                    "group_chat": False,
                    "is_ongoing": False,
                    "android_category": None,
                    "important": False,
                }
            ],
        }
        for index in range(ATTACKS_PER_TYPE + 2)
    ]
    return json.dumps({"attacks": attacks})


def test_the_redteam_agent_writes_ten_attacks_per_type(params: dict[str, Any]) -> None:
    document = generate_redteam_set(ScriptedLLM(redteam_reply), clock=fixed_clock)
    attacks = validate_attack_set(document)

    assert len(attacks) == ATTACKS_PER_TYPE * len(ATTACK_TYPES) == 60
    assert attacks[0]["id"] == "rt_fake_urgency_promo_01"
    assert {a["source"] for a in attacks} == {"synthetic"}
    assert len(run_attacks(attacks, params)) == 60


def test_the_redteam_agent_owns_ids_and_types_whatever_the_model_says() -> None:
    def reply(prompt: str, call: int) -> str:
        document = json.loads(redteam_reply(prompt, call))
        for item in document["attacks"]:
            item["id"] = "model_id"
            item["attack_type"] = "something_else"
        return json.dumps(document)

    attacks = generate_redteam_set(ScriptedLLM(reply), clock=fixed_clock)["attacks"]

    assert attacks[10]["id"] == "rt_spoofed_sender_01"
    assert attacks[10]["attack_type"] == "spoofed_sender"


def test_a_stopped_text_bank_run_resumes_without_redoing_finished_batches(
    params: dict[str, Any], tmp_path: Path
) -> None:
    checkpoint = Checkpoint(tmp_path / "text_bank.progress.json")

    def quota_runs_out(prompt: str, call: int) -> str:
        if call == 4:
            raise LLMError("quota used up")
        return text_bank_reply(prompt, call)

    first = ScriptedLLM(quota_runs_out)
    with pytest.raises(LLMError):
        generate_text_bank(first, params, clock=fixed_clock, checkpoint=checkpoint)

    second = ScriptedLLM(text_bank_reply)
    document = generate_text_bank(second, params, clock=fixed_clock, checkpoint=checkpoint)

    assert len(second.prompts) == len(BATCHES) - 3
    assert [item["id"] for item in document["items"]] == [f"tb{i:03d}" for i in range(1, 251)]


def test_a_stopped_redteam_run_resumes_from_the_next_attack_type(tmp_path: Path) -> None:
    checkpoint = Checkpoint(tmp_path / "redteam_set.progress.json")

    def fails_on_third(prompt: str, call: int) -> str:
        if call == 3:
            raise LLMError("network down")
        return redteam_reply(prompt, call)

    with pytest.raises(LLMError):
        generate_redteam_set(ScriptedLLM(fails_on_third), clock=fixed_clock, checkpoint=checkpoint)

    second = ScriptedLLM(redteam_reply)
    document = generate_redteam_set(second, clock=fixed_clock, checkpoint=checkpoint)

    assert len(second.prompts) == len(ATTACK_TYPES) - 2
    assert len(document["attacks"]) == 60


def test_checkpoint_clear_removes_the_progress_file(tmp_path: Path) -> None:
    checkpoint = Checkpoint(progress_path(tmp_path / "out.json"))
    checkpoint.save("step", "ollama:test", [1, 2])

    assert checkpoint.load()["step"].data == [1, 2]
    checkpoint.clear()
    assert checkpoint.load() == {}
