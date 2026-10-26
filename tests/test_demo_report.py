import json
from pathlib import Path

from engine.make_demo_report import DAYS, OUTPUT_PATH, READING_SECONDS, build_document

DOCUMENT = json.loads(Path(OUTPUT_PATH).read_text(encoding="utf-8"))
HISTORY = DOCUMENT["history"]

# The keys ReportHistory.toJson writes on the phone.
REPORT_KEYS = {
    "since",
    "steady",
    "restless",
    "fatigue",
    "opens_app",
    "minutes_app",
    "opens_category",
    "minutes_category",
    "switches",
    "held",
    "delivered",
    "worth",
}


def test_report_file_is_up_to_date_and_labelled_simulated() -> None:
    assert json.loads(json.dumps(build_document())) == DOCUMENT
    assert DOCUMENT["label"] == "simulated"
    assert DOCUMENT["persona"] == "student_coder"


def test_history_has_the_phones_report_shape() -> None:
    assert set(HISTORY) == REPORT_KEYS
    assert len(HISTORY["steady"]) == len(HISTORY["restless"]) == len(HISTORY["fatigue"]) == 24
    assert all(len(hour) == 3 for hour in HISTORY["fatigue"])


def test_focus_time_matches_the_persona_schedule_for_a_week() -> None:
    schedule = json.loads(Path("sim/personas.json").read_text(encoding="utf-8"))["personas"][
        "student_coder"
    ]["focus_schedule"]
    per_day = sum(session["duration_min"] for session in schedule) * 60
    assert sum(HISTORY["steady"]) + sum(HISTORY["restless"]) == DAYS * per_day


def test_one_fatigue_reading_per_fifteen_seconds_of_focus() -> None:
    for hour in range(24):
        seconds = HISTORY["steady"][hour] + HISTORY["restless"][hour]
        assert sum(HISTORY["fatigue"][hour]) * READING_SECONDS == seconds


def test_opens_come_only_from_delivered_notifications() -> None:
    for package, opens in HISTORY["opens_app"].items():
        assert opens <= HISTORY["delivered"][package]
        assert HISTORY["minutes_app"][package] > 0
    assert sum(HISTORY["opens_app"].values()) == sum(HISTORY["opens_category"].values())


def test_worth_answers_never_exceed_the_taps() -> None:
    assert all(0 <= yes <= total for yes, total in HISTORY["worth"].values())


def test_every_app_in_the_report_has_a_display_name() -> None:
    packages = set(HISTORY["held"]) | set(HISTORY["delivered"]) | set(HISTORY["switches"])
    assert packages <= set(DOCUMENT["app_labels"])
