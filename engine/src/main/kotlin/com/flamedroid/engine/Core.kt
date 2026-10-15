/*
 * FlameDroid engine — core types.
 * Variation math is ported from JWildfire (c) Andreas Maschke, LGPL 2.1+.
 */
package com.flamedroid.engine

import com.flamedroid.engine.FastMath.atan2
import kotlin.math.sqrt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// Constants mirroring org.jwildfire.base.mathlib.MathLib
const val SMALL_EPSILON = 1.0e-300
const val EPSILON = 0.00000001
const val M_PI = Math.PI
const val M_PI_2 = Math.PI * 0.5
const val M_PI_4 = Math.PI * 0.25
const val M_1_PI = 1.0 / Math.PI
const val M_2_PI = 2.0 / Math.PI
const val M_2PI = 2.0 * Math.PI
const val R_PI = 0.31830989

fun sqr(a: Double) = a * a
fun fmod(a: Double, b: Double) = a % b

/** A point in flame space with lazily cached polar helpers (JWildfire XYZPoint). */
class Pt {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var color = 0.0

    private var vSumsq = false; private var sumsq = 0.0
    private var vSqrt = false; private var sqrtV = 0.0
    private var vAtan = false; private var atan = 0.0
    private var vAtanYX = false; private var atanYX = 0.0
    private var vSinA = false; private var sinA = 0.0
    private var vCosA = false; private var cosA = 0.0

    fun invalidate() { vSumsq = false; vSqrt = false; vAtan = false; vAtanYX = false; vSinA = false; vCosA = false }

    fun clear() { x = 0.0; y = 0.0; z = 0.0; invalidate() }

    fun assign(p: Pt) { x = p.x; y = p.y; z = p.z; color = p.color; invalidate() }

    val precalcSumsq: Double get() { if (!vSumsq) { sumsq = x * x + y * y; vSumsq = true }; return sumsq }
    val precalcSqrt: Double get() { if (!vSqrt) { sqrtV = sqrt(x * x + y * y) + SMALL_EPSILON; vSqrt = true }; return sqrtV }
    /** NB: JWildfire's "atan" is atan2(x, y) — kept for faithful ports. */
    val precalcAtan: Double get() { if (!vAtan) { atan = atan2(x, y); vAtan = true }; return atan }
    val precalcAtanYX: Double get() { if (!vAtanYX) { atanYX = atan2(y, x); vAtanYX = true }; return atanYX }
    val precalcSinA: Double get() { if (!vSinA) { sinA = x / precalcSqrt; vSinA = true }; return sinA }
    val precalcCosA: Double get() { if (!vCosA) { cosA = y / precalcSqrt; vCosA = true }; return cosA }
}

/** Per-thread random source + render flags (JWildfire FlameTransformationContext). */
class Ctx(seed: Long) {
    private var s0 = seed xor 0x5DEECE66DL
    private var s1 = (seed * 6364136223846793005L) or 1L
    var preserveZ = false

    /** xorshift128+ → [0,1) */
    fun random(): Double {
        var a = s0
        val b = s1
        s0 = b
        a = a xor (a shl 23)
        s1 = a xor b xor (a ushr 17) xor (b ushr 26)
        return ((s1 + b) ushr 11) * (1.0 / (1L shl 53))
    }

    fun random(max: Int): Int = if (max <= 0) 0 else (random() * max).toInt().coerceAtMost(max - 1)
}

/** A numeric variation parameter. */
class Param(val name: String, var value: Double, val isInt: Boolean) : ReadWriteProperty<Any?, Double> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) { this.value = value }
}

/**
 * Base class for all variations ("VariationFunc" in JWildfire).
 * Each variation adds its contribution to [v] based on the affine-transformed point [a].
 */
abstract class Variation(val name: String) {
    val params = ArrayList<Param>()

    protected fun param(name: String, default: Double) = Param(name, default, false).also { params += it }
    protected fun intParam(name: String, default: Int) = Param(name, default.toDouble(), true).also { params += it }

    /** Called once per render thread before iterating (precompute constants here). */
    open fun init(ctx: Ctx, xf: XForm, amount: Double) {}

    abstract fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double)

    /** Randomise parameters (used by the random flame generator). Default: jitter around defaults. */
    open fun randomize(r: java.util.Random) {
        for (p in params) {
            val base = p.value
            val nv = if (base == 0.0) r.nextGaussian() * 0.5 else base * (0.5 + r.nextDouble())
            p.value = if (p.isInt) Math.round(nv).toDouble().let { if (it == 0.0) 1.0 else it } else nv
        }
    }

    fun setParam(name: String, value: Double): Boolean {
        val p = params.firstOrNull { it.name.equals(name, true) } ?: return false
        p.value = if (p.isInt) Math.round(value).toDouble() else value
        return true
    }

    fun copy(): Variation {
        val c = Variations.create(name)
        for (i in params.indices) c.params[i].value = params[i].value
        return c
    }

    protected fun z(ctx: Ctx, a: Pt, v: Pt, amount: Double) {
        if (ctx.preserveZ) v.z += amount * a.z
    }
}
