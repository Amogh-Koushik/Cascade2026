package com.pratibimb.app.engine

// A persona is one fitted params file bundled from contracts/params. Switching persona
// changes the priors the twin starts from; what it has learned about this user is kept.
data class PersonaOption(val id: String, val label: String, val description: String) {
    val asset: String get() = "twin_params_$id.json"
}

object Personas {
    val all = listOf(
        PersonaOption(
            id = "student_coder",
            label = "Student coder",
            description = "Exam and placement prep, easily pulled into short videos late at night.",
        ),
        PersonaOption(
            id = "night_shift_nurse",
            label = "Night-shift nurse",
            description = "12-hour night shifts, certification study and daytime sleep.",
        ),
    )

    val default: PersonaOption = all.first()

    // Accepts the id or the label, so a screen can pass whichever it shows.
    fun find(idOrLabel: String): PersonaOption? =
        all.firstOrNull { it.id == idOrLabel || it.label == idOrLabel }
}
