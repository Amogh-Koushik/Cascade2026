# The test-vector harness. A vector names a params file, small patches to it, feedback events
# to apply in order, and one notification to decide. run_vector() is the whole procedure, and
# compare() is the tolerance rule; the Kotlin test for TwinEngine.kt repeats both exactly.

import copy
import json
import math
from typing import Any

from engine.contract import NotificationFeatures, TwinContext, feedback_from_dict
from engine.engine import TwinEngine

ABSOLUTE_TOLERANCE = 1e-6
EXP_TOLERANCE = 1e-3

# Values computed through exp() on minutes, where libm differences between platforms can show.
EXP_FIELDS = frozenset(
    {
        "mins_if_opened_lo",
        "mins_if_opened_hi",
        "l_p10",
        "l_p50",
        "l_p90",
        "l_mean",
        "net_minutes_protected",
    }
)

# Screen wording only; not part of what the two engines must agree on.
UNCOMPARED_FIELDS = frozenset({"explanation"})


class VectorError(ValueError):
    pass


def apply_patches(params: dict[str, Any], patches: list[dict[str, Any]]) -> dict[str, Any]:
    patched = copy.deepcopy(params)
    for patch in patches:
        *parents, last = patch["path"]
        target = patched
        for key in parents:
            target = target[key]
        # Patches may only change existing constants, so a typo cannot add a dead key.
        if last not in target:
            raise VectorError(f"Patch path {patch['path']} does not exist in the params")
        target[last] = patch["value"]
    return patched


def run_vector(vector: dict[str, Any], base_params: dict[str, Any]) -> dict[str, Any]:
    twin = TwinEngine(apply_patches(base_params, vector["param_patches"]))
    for event in vector["events"]:
        twin.apply_feedback(feedback_from_dict(event))

    trace = twin.trace(
        NotificationFeatures.from_dict(vector["features"]),
        TwinContext.from_dict(vector["context"]),
    )
    return {
        "intermediate": {
            "p_cs": trace.pull.p_cs,
            "n_p": trace.pull.n_p,
            "mu_cs": trace.pull.mu_cs,
            "sigma_pred": trace.pull.sigma_pred,
            "structural_score": trace.worth.structural_score,
            "q": trace.worth.q,
            "n_q": trace.worth.n_q,
            "used_sender": trace.worth.used_sender,
            "holding_wins": trace.futures.holding_wins,
            "pi": trace.futures.pi,
        },
        "decision": json.loads(json.dumps(trace.decision.to_dict())),
        "counts_after_events": json.loads(twin.export_counts()),
    }


def compare(expected: Any, actual: Any, path: str = "") -> list[str]:
    # Returns every mismatch as "path: expected X, got Y"; an empty list means the vector passes.
    field = path.rsplit(".", 1)[-1]
    if field in UNCOMPARED_FIELDS:
        return []

    if isinstance(expected, dict) and isinstance(actual, dict):
        if set(expected) != set(actual):
            return [f"{path}: keys differ, expected {sorted(expected)}, got {sorted(actual)}"]
        return [
            mismatch
            for key in expected
            for mismatch in compare(expected[key], actual[key], f"{path}.{key}" if path else key)
        ]

    if _is_float(expected) and _is_number(actual):
        tolerance = EXP_TOLERANCE if field in EXP_FIELDS else ABSOLUTE_TOLERANCE
        if math.isclose(expected, actual, rel_tol=0.0, abs_tol=tolerance):
            return []
        return [f"{path}: expected {expected}, got {actual}"]

    if expected != actual or type(expected) is not type(actual):
        return [f"{path}: expected {expected!r}, got {actual!r}"]
    return []


def _is_float(value: Any) -> bool:
    return isinstance(value, float)


def _is_number(value: Any) -> bool:
    return isinstance(value, int | float) and not isinstance(value, bool)
