package com.pratibimb.app.ui.theme

// The accent colours the user can pick. Seeds are the 500 shades of Material Design's colour
// palette (material.io/design/color, "2014 Material Design color palettes"); each becomes a full
// Material 3 dark scheme. ORIGINAL is the app's own hand-tuned Warm Obsidian palette and
// WALLPAPER is Android's dynamic colour, taken from the user's wallpaper.
enum class Accent(val label: String, val seed: Int?) {
    ORIGINAL("Pratibimb", null),
    WALLPAPER("Wallpaper", null),
    RED("Red", 0xFFF44336.toInt()),
    PINK("Pink", 0xFFE91E63.toInt()),
    PURPLE("Purple", 0xFF9C27B0.toInt()),
    DEEP_PURPLE("Deep purple", 0xFF673AB7.toInt()),
    INDIGO("Indigo", 0xFF3F51B5.toInt()),
    BLUE("Blue", 0xFF2196F3.toInt()),
    LIGHT_BLUE("Light blue", 0xFF03A9F4.toInt()),
    CYAN("Cyan", 0xFF00BCD4.toInt()),
    TEAL("Teal", 0xFF009688.toInt()),
    GREEN("Green", 0xFF4CAF50.toInt()),
    LIGHT_GREEN("Light green", 0xFF8BC34A.toInt()),
    LIME("Lime", 0xFFCDDC39.toInt()),
    YELLOW("Yellow", 0xFFFFEB3B.toInt()),
    AMBER("Amber", 0xFFFFC107.toInt()),
    ORANGE("Orange", 0xFFFF9800.toInt()),
    DEEP_ORANGE("Deep orange", 0xFFFF5722.toInt()),
    BROWN("Brown", 0xFF795548.toInt()),
    BLUE_GREY("Blue grey", 0xFF607D8B.toInt()),
    ;

    companion object {
        fun fromKey(key: String?): Accent = entries.firstOrNull { it.name == key } ?: ORIGINAL
    }
}
