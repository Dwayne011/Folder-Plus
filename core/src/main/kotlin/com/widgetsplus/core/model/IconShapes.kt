package com.widgetsplus.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Outlines for [IconShape], in a unit square (0..1), for drawing app icons in that shape.
 * A squircle is a superellipse |x|^n + |y|^n = 1 with [SQUIRCLE_EXPONENT] (between a circle,
 * n = 2, and a square); a rounded square has corners [ROUNDED_RADIUS] of its side.
 */
object IconShapes {
    const val SQUIRCLE_EXPONENT = 3.0
    const val ROUNDED_RADIUS = 0.2f

    /** [count] points around the squircle, starting at the right-hand middle, in a unit square. */
    fun squirclePoints(count: Int = 96): List<Pair<Float, Float>> = (0 until count).map { i ->
        val t = 2 * PI * i / count
        val c = cos(t)
        val s = sin(t)
        val x = 0.5 + 0.5 * sign(c) * abs(c).pow(2 / SQUIRCLE_EXPONENT)
        val y = 0.5 + 0.5 * sign(s) * abs(s).pow(2 / SQUIRCLE_EXPONENT)
        x.toFloat() to y.toFloat()
    }
}
