package com.pratibimb.app.engine

import com.pratibimb.twin.Category

// The placement pair for the demo: the same topic from the real Placement Cell and from an
// unknown number with a link. With the new-sender rule off, the Placements context and the
// deadline let both through; switched on, the fake one is held and flagged. Only the features
// matter on the real path; the template supplies the stub engine's canned context and decision.
object DemoScenarios {
    fun placement(template: MockScenario): List<MockScenario> = listOf(
        template.copy(
            id = "placement_real",
            display = ScenarioDisplay(
                app = "Gmail",
                title = "Placement Cell",
                text = "Infosys drive: shortlist is out. Confirm your interview slot in the portal.",
            ),
            features = template.features.copy(
                key = "n_placement_real",
                packageName = "com.google.android.gm",
                category = Category.WORK_EMAIL,
                isConversation = false,
                groupChat = false,
                senderHash = null,
                senderDisplayName = null,
                sameSenderRecentPings = 0,
                baitHit = false,
                urgencyHit = false,
                otpPattern = false,
            ),
        ),
        template.copy(
            id = "placement_fake",
            display = ScenarioDisplay(
                app = "WhatsApp",
                title = "+91 98765 43210",
                text = "Placement drive registration closes in 30 min. Register now: bit.ly/tnp-reg26",
            ),
            features = template.features.copy(
                key = "n_placement_fake",
                packageName = "com.whatsapp",
                category = Category.MESSAGING,
                isConversation = true,
                groupChat = false,
                senderHash = "s_demo_unknown_number",
                senderDisplayName = "+91 98765 43210",
                sameSenderRecentPings = 0,
                baitHit = false,
                urgencyHit = false,
                otpPattern = false,
            ),
        ),
    )
}
