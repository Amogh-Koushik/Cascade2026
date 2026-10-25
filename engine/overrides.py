# Never-hold overrides, checked before any model runs, in the order of CONTRACTS.md section 5.
# The first match wins. No learned value and no coach rule can change this list.

from dataclasses import dataclass
from typing import Any

from engine.contract import NotificationFeatures, TwinContext


@dataclass(frozen=True)
class Override:
    reason: str
    explanation: str


def check_overrides(
    features: NotificationFeatures,
    context: TwinContext,
    params: dict[str, Any],
) -> Override | None:
    never_hold = params["never_hold"]
    repeat_ping = params["policy"]["repeat_ping"]

    if features.package_name in context.always_deliver_packages:
        return Override(
            "Always delivered: you chose this app", "Delivered: you always let this app through."
        )
    if features.sender_hash is not None and features.sender_hash in context.always_deliver_senders:
        return Override(
            "Always delivered: you chose this sender",
            "Delivered: you always let this sender through.",
        )
    if features.android_category in never_hold["android_categories"]:
        return Override(
            "Always delivered: calls, alarms and navigation",
            "Delivered: calls, alarms and navigation always come through.",
        )
    if features.is_ongoing:
        return Override(
            "Always delivered: ongoing activity",
            "Delivered: something is in progress, like a timer or a live trip.",
        )
    if features.package_name in never_hold["packages"]:
        return Override(
            "Always delivered: rides, deliveries and maps",
            "Delivered: rides, deliveries and maps always come through.",
        )
    if features.otp_pattern and features.package_name in never_hold["otp_source_packages"]:
        return Override(
            "Always delivered: one-time password",
            "Delivered: looks like a one-time password from a trusted app.",
        )
    # Only a real person pinging again counts; an app re-sending a promo never does.
    pings = features.same_sender_recent_pings + 1
    if (
        features.is_conversation
        and features.sender_hash is not None
        and (pings >= repeat_ping["count"])
    ):
        who = features.sender_display_name or "This sender"
        times = "twice" if pings == 2 else f"{pings} times"
        return Override(
            "Always delivered: repeated ping",
            f"Delivered: {who} messaged {times} in {repeat_ping['window_min']:g} minutes.",
        )
    return None
