package com.flamedroid.engine

import kotlin.math.abs

/**
 * Fast trig for the iteration loop. Phone runtimes call into libm for sin/cos/atan2,
 * which is several times slower than a table lookup; JWildfire uses a fast-math library
 * for the same reason. Accuracy (~3e-7 for sin/cos, ~2e-8 for atan2) is far below what
 * can show up in an image. NaN and huge arguments fall back to the exact functions so
 * divergent points are still detected.
 */
object FastMath {
    private const val BITS = 12
    private const val SIZE = 1 shl BITS
    private const val MASK = SIZE - 1
    private const val TWO_PI = 2.0 * Math.PI
    private const val INV_STEP = SIZE / TWO_PI
    private const val QUARTER = SIZE / 4
    private val TABLE = DoubleArray(SIZE + 1) { Math.sin(it * TWO_PI / SIZE) }

    @JvmStatic
    fun sin(x: Double): Double {
        if (!(abs(x) < 1e9)) return Math.sin(x) // NaN, infinities, huge values
        val t = x * INV_STEP
        var i = t.toLong()
        if (t < i) i-- // floor for negatives
        val frac = t - i
        val k = (i and MASK.toLong()).toInt()
        val a = TABLE[k]
        return a + (TABLE[k + 1] - a) * frac
    }

    @JvmStatic
    fun cos(x: Double): Double {
        if (!(abs(x) < 1e9)) return Math.cos(x)
        val t = x * INV_STEP + QUARTER
        var i = t.toLong()
        if (t < i) i--
        val frac = t - i
        val k = (i and MASK.toLong()).toInt()
        val a = TABLE[k]
        return a + (TABLE[k + 1] - a) * frac
    }

    /** atan for |z| <= 1, Abramowitz & Stegun 4.4.49 (|error| <= 2e-8). */
    private fun atanUnit(z: Double): Double {
        val z2 = z * z
        return z * (1.0 + z2 * (-0.3333314528 + z2 * (0.1999355085 + z2 * (-0.1420889944 +
                z2 * (0.1065626393 + z2 * (-0.0752896400 + z2 * (0.0429096138 +
                z2 * (-0.0161657367 + z2 * 0.0028662257))))))))
    }

    @JvmStatic
    fun atan2(y: Double, x: Double): Double {
        if (x != x || y != y) return Double.NaN
        val ax = abs(x); val ay = abs(y)
        if (ax == 0.0 && ay == 0.0) return Math.atan2(y, x) // keeps signed-zero semantics
        if (ax == Double.POSITIVE_INFINITY || ay == Double.POSITIVE_INFINITY) return Math.atan2(y, x)
        var r = if (ay <= ax) atanUnit(ay / ax) else Math.PI / 2 - atanUnit(ax / ay)
        if (x < 0) r = Math.PI - r
        return if (y < 0) -r else r
    }
}
