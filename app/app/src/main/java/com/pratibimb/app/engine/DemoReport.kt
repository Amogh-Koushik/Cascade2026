package com.pratibimb.app.engine

import com.pratibimb.twin.SimpleJsonParser

// Reads contracts/demo/demo_report.json: a simulated week of the student coder persona, decided
// by the reference engine and aggregated like a real week (engine/make_demo_report.py). The
// Report tab shows it, labelled simulated, until the phone has its own focus time recorded.
data class DemoReport(val history: ReportHistory, val appLabels: Map<String, String>) {
    companion object {
        const val ASSET = "demo_report.json"

        @Suppress("UNCHECKED_CAST")
        fun parse(json: String): DemoReport {
            val document = SimpleJsonParser(json).parse() as Map<String, Any?>
            require(document["label"] == "simulated") { "The demo report must be labelled simulated" }
            return DemoReport(
                history = ReportHistory.fromMap(document["history"] as Map<String, Any?>),
                appLabels = (document["app_labels"] as Map<String, Any?>).mapValues { it.value as String },
            )
        }
    }
}
