package com.widgetsplus.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** The calendar's text size (its settings' slider) in the day and 30-day views; 1.0 at 100%. */
val LocalAgendaTextScale = staticCompositionLocalOf { 1f }

/** [value] sp, grown or shrunk by the calendar's text size (the phone's font size still applies). */
@Composable
@ReadOnlyComposable
fun agendaSp(value: Float): TextUnit = (value * LocalAgendaTextScale.current).sp
