package com.widgetsplus.core.model

/**
 * Text size as a percentage of the normal size: the calendar widget's (shared by every calendar
 * widget and its day and 30-day views) and each folder's app names. 80% to 140% in 5% steps.
 */
object TextSize {
    const val DEFAULT = 100
    const val MIN = 80
    const val MAX = 140
    const val STEP = 5

    /** Slider stops between the ends (Compose's Slider counts the ones in between). */
    const val SLIDER_STEPS = (MAX - MIN) / STEP - 1

    /** [percent] kept in range and on a 5% step. */
    fun clamp(percent: Int): Int {
        val inRange = percent.coerceIn(MIN, MAX)
        return ((inRange - MIN + STEP / 2) / STEP * STEP + MIN).coerceIn(MIN, MAX)
    }

    /** The multiplier for [percent]: 1.0 at 100%. */
    fun scale(percent: Int): Float = clamp(percent) / 100f
}
