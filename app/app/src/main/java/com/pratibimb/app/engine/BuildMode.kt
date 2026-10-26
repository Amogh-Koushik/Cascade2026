package com.pratibimb.app.engine

// This app is the demo build: features meant for the demo (the pause card) start switched on.
// A product build would set this to false and follow the params flags alone.
object BuildMode {
    const val DEMO = true

    // The demo's priors put a typical Instagram burst at about 2-5 minutes of expected loss, so
    // the demo shows the pause card from 2 minutes; the product params say 5.
    const val DEMO_PAUSE_CARD_MIN_LOSS_MIN = 2.0
}
