package commanderpepper.optcgsimcardartchooser.theme

import kotlin.math.abs

/** Light or dark colours; [System] follows the OS setting. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/** Text size as a percentage of the default, limited to fixed [STEPS]. */
object TextScale {
    val STEPS = listOf(85, 90, 100, 110, 120, 135)
    const val DEFAULT = 100
    val MIN get() = STEPS.first()
    val MAX get() = STEPS.last()

    /** The step closest to [percent], so an out-of-range or outdated saved value still works. */
    fun nearest(percent: Int): Int = STEPS.minBy { abs(it - percent) }
}

/** Remembers the appearance choices (theme and text size) between launches. */
interface ThemeStore {
    fun load(): ThemeMode
    fun save(mode: ThemeMode)

    /** The saved text size in percent; [TextScale.DEFAULT] if none. */
    fun loadTextScale(): Int
    fun saveTextScale(percent: Int)
}
