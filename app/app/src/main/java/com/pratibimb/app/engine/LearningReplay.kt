package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.FeedbackEvent
import com.pratibimb.twin.PhoneState
import com.pratibimb.twin.SimpleJsonParser

// Reads contracts/demo/learning_replay.json: simulated pull outcomes the demo pushes through
// the same applyFeedback path as real taps, so the Future Card's ranges visibly narrow.
object LearningReplay {
    const val ASSET = "learning_replay.json"

    @Suppress("UNCHECKED_CAST")
    fun parse(json: String): List<FeedbackEvent.PullOutcome> {
        val document = SimpleJsonParser(json).parse() as Map<String, Any?>
        return (document["events"] as List<Any?>).map { raw ->
            val event = raw as Map<String, Any?>
            require(event["type"] == "pull_outcome") { "Unsupported replay event ${event["type"]}" }
            FeedbackEvent.PullOutcome(
                category = Category.fromKey(event["category"] as String),
                state = PhoneState.valueOf(event["state"] as String),
                opened = event["opened"] as Boolean,
                minutesLost = (event["minutes_lost"] as Number?)?.toDouble(),
                releasedFromTray = event["released_from_tray"] as Boolean,
            )
        }
    }
}
