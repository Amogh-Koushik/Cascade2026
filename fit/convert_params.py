# Rebuilds the fitted twin_params files from the simulator (fit/twin_params_<persona>.json) into
# the contract schema, so the edge server can serve them and both engines can load them.
#
# Rules: every fitted value is kept. Keys the contract needs but the fit file lacks are filled
# from the example params; renamed keys are mapped; lists (never-hold, lexicons, app map) are
# the union of both; the draw bank is always the shared one. One exception to "fitted wins":
# an app's category follows the contract's app map, because the text bank, the red-team set
# and the sample scenarios are all labelled with it. Every change is reported, and the result
# must pass the schema before it is written.
#
# Run: uv run python -m fit.convert_params [fit files...]

import argparse
import copy
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from contracts.params import PARAMS_DIR, ParamsError, load_draw_bank, load_params, validate_params

REPO_ROOT = Path(__file__).resolve().parent.parent
FIT_DIR = REPO_ROOT / "fit"
EXAMPLE_PATH = PARAMS_DIR / "twin_params_example.json"
DEFAULT_INPUTS = (
    FIT_DIR / "twin_params_student_coder.json",
    FIT_DIR / "twin_params_night_shift_nurse.json",
)

# Old key name in the fit files -> name in the contract, as (section path, old, new).
RENAMES = (
    (("worth", "weights"), "same_sender_repeats_3m", "same_sender_repeat"),
    (("never_hold",), "categories", "android_categories"),
    (("never_hold",), "otp_sources", "otp_source_packages"),
)

# Urgency words that also match every OTP message, so OTP promos would all read as urgent.
OTP_LIKE_URGENCY = frozenset({"otp", "code"})


@dataclass
class ConversionReport:
    persona: str
    renamed: list[str] = field(default_factory=list)
    filled: list[str] = field(default_factory=list)
    merged: list[str] = field(default_factory=list)
    conflicts: list[str] = field(default_factory=list)
    corrected: list[str] = field(default_factory=list)
    dropped: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def lines(self) -> list[str]:
        sections = (
            ("renamed", self.renamed),
            ("filled from example", self.filled),
            ("lists merged", self.merged),
            ("kept fitted value over example", self.conflicts),
            ("set to the contract's category", self.corrected),
            ("dropped (not in contract)", self.dropped),
            ("warnings", self.warnings),
        )
        out = [f"{self.persona}:"]
        for title, items in sections:
            if items:
                out.append(f"  {title} ({len(items)}):")
                out += [f"    - {item}" for item in items]
        return out


def _section(document: dict[str, Any], path: tuple[str, ...]) -> dict[str, Any] | None:
    target: Any = document
    for key in path:
        if not isinstance(target, dict) or key not in target:
            return None
        target = target[key]
    return target if isinstance(target, dict) else None


def _apply_renames(fitted: dict[str, Any], report: ConversionReport) -> None:
    for path, old, new in RENAMES:
        section = _section(fitted, path)
        if section is not None and old in section and new not in section:
            section[new] = section.pop(old)
            report.renamed.append(f"{'.'.join(path)}.{old} -> {new}")


def _merge(example: Any, fitted: Any, path: str, report: ConversionReport) -> Any:
    # Walks the example's shape: it decides which keys exist, the fit decides the values.
    if isinstance(example, dict) and isinstance(fitted, dict):
        merged = {}
        for key, example_value in example.items():
            child = f"{path}.{key}" if path else key
            if key in fitted:
                merged[key] = _merge(example_value, fitted[key], child, report)
            else:
                merged[key] = copy.deepcopy(example_value)
                report.filled.append(child)
        report.dropped += [f"{path}.{key}" if path else key for key in fitted if key not in example]
        return merged
    if isinstance(example, list) and isinstance(fitted, list):
        union = list(fitted) + [item for item in example if item not in fitted]
        if len(union) > len(fitted):
            report.merged.append(f"{path}: +{len(union) - len(fitted)} from example")
        return union
    return copy.deepcopy(fitted)


def _merge_open_map(
    example: dict[str, Any],
    fitted: dict[str, Any],
    path: str,
    report: ConversionReport,
    example_wins: bool,
) -> dict[str, Any]:
    # Maps with free-form keys (app map, provenance): keep every entry from both. Where both
    # have a key with different values, example_wins decides which one stays.
    merged = dict(fitted)
    added = 0
    for key, value in example.items():
        if key not in merged:
            merged[key] = value
            added += 1
        elif merged[key] != value and example_wins:
            report.corrected.append(f"{path}.{key}: {merged[key]!r} -> {value!r}")
            merged[key] = value
        elif merged[key] != value:
            report.conflicts.append(f"{path}.{key}: {merged[key]!r} (example has {value!r})")
    if added:
        report.merged.append(f"{path}: +{added} entries from example")
    return merged


def convert(
    fitted: dict[str, Any], example: dict[str, Any], source: str
) -> tuple[dict[str, Any], ConversionReport]:
    fitted = copy.deepcopy(fitted)
    report = ConversionReport(persona=str(fitted.get("persona", "?")))
    _apply_renames(fitted, report)

    open_maps = {key: fitted.pop(key, {}) for key in ("app_category_map", "provenance")}
    fitted.pop("draw_bank", None)
    shape = {
        k: v
        for k, v in example.items()
        if k not in ("app_category_map", "provenance", "draw_bank", "placeholder")
    }

    converted = _merge(shape, fitted, "", report)
    converted["app_category_map"] = _merge_open_map(
        example["app_category_map"],
        open_maps["app_category_map"],
        "app_category_map",
        report,
        example_wins=True,
    )
    provenance = {k: str(v) for k, v in open_maps["provenance"].items()}
    provenance["conversion"] = (
        f"converted from {source} by fit/convert_params.py: {len(report.filled)} keys filled "
        "from the example params, draw bank replaced with contracts/draw_bank.json"
    )
    converted["provenance"] = provenance
    converted["draw_bank"] = load_draw_bank()

    risky = sorted(OTP_LIKE_URGENCY & {w.lower() for w in converted["lexicons"]["urgency"]})
    if risky:
        report.warnings.append(
            f"urgency lexicon contains {risky}: every OTP-style message will also count as urgent"
        )
    validate_params(converted)
    return converted, report


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert fitted params to the contract schema.")
    parser.add_argument("inputs", nargs="*", type=Path, default=list(DEFAULT_INPUTS))
    parser.add_argument("--out-dir", type=Path, default=PARAMS_DIR)
    args = parser.parse_args()

    example = load_params(EXAMPLE_PATH)
    for path in args.inputs:
        try:
            fitted = json.loads(path.read_text(encoding="utf-8"))
            converted, report = convert(fitted, example, path.relative_to(REPO_ROOT).as_posix())
        except (OSError, json.JSONDecodeError, ParamsError, ValueError) as error:
            print(f"{path}: {error}")
            return 1
        out = args.out_dir / f"twin_params_{converted['persona']}.json"
        out.write_text(json.dumps(converted, indent=1) + "\n", encoding="utf-8")
        print("\n".join(report.lines()))
        print(f"  wrote {out.relative_to(REPO_ROOT).as_posix()}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
