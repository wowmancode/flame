/*
 * FlameDroid variations, batch 2.
 *
 * Line-by-line ports of org.jwildfire.create.tina.variation.*Func from JWildfire
 * (https://github.com/thargor6/JWildfire), (c) Andreas Maschke and contributors, GNU LGPL v2.1+.
 * Names and parameter names match JWildfire so .flame files stay interchangeable.
 * Note: a few JWildfire variations (rays1-3, hypershift2) assign instead of add to the output;
 * that quirk is kept on purpose.
 */
package com.flamedroid.engine

import kotlin.math.*
import com.flamedroid.engine.FastMath.atan2
import com.flamedroid.engine.FastMath.cos
import com.flamedroid.engine.FastMath.sin

private const val M_1_2PI = 1.0 / (2.0 * Math.PI)
private fun rnd(x: Double) = Math.round(x).toDouble()
private fun sqrtSafe(x: Double) = if (x <= 0.0) 0.0 else sqrt(x)

// ---------------------------------------------------------------- complex-plane & elliptic

class Mobius : Variation("mobius") {
    private var reA by param("re_a", 0.1); private var reB by param("re_b", 0.2)
    private var reC by param("re_c", -0.15); private var reD by param("re_d", 0.21)
    private var imA by param("im_a", 0.2); private var imB by param("im_b", -0.12)
    private var imC by param("im_c", -0.15); private var imD by param("im_d", 0.1)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val reU = reA * a.x - imA * a.y + reB
        val imU = reA * a.y + imA * a.x + imB
        val reV = reC * a.x - imC * a.y + reD
        val imV = reC * a.y + imC * a.x + imD
        val d = reV * reV + imV * imV
        if (d == 0.0) return
        val radV = amount / d
        v.x += radV * (reU * reV + imU * imV)
        v.y += radV * (imU * reV - reU * imV)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { params.forEach { it.value = r.nextDouble() * 2 - 1 } }
}

class EDisc : Variation("edisc") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val tmp = a.precalcSumsq + 1.0
        val tmp2 = 2.0 * a.x
        val r1 = sqrt(tmp + tmp2); val r2 = sqrt(tmp - tmp2)
        val xmax = (r1 + r2) * 0.5
        val a1 = ln(xmax + sqrt(xmax - 1.0))
        val a2 = -acos(a.x / xmax)
        val w = amount / 11.57034632
        var snv = sin(a1); val csv = cos(a1)
        val snhu = sinh(a2); val cshu = cosh(a2)
        if (a.y > 0.0) snv = -snv
        v.x += w * cshu * csv
        v.y += w * snhu * snv
        z(ctx, a, v, amount)
    }
}

/** Shared elliptic-coordinate helper for the e* family: returns (mu, nu) or null. */
private fun ellipticMuNu(a: Pt, x: Double, r2in: Double): DoubleArray {
    val tmp = r2in + 1.0
    val tmp2 = 2.0 * x
    var xmax = (sqrtSafe(tmp + tmp2) + sqrtSafe(tmp - tmp2)) * 0.5
    if (xmax < 1.0) xmax = 1.0
    val mu = acosh(xmax)
    var t = x / xmax
    if (t > 1.0) t = 1.0 else if (t < -1.0) t = -1.0
    var nu = acos(t)
    if (a.y < 0) nu = -nu
    return doubleArrayOf(mu, nu, xmax)
}

class EJulia : Variation("eJulia") {
    private var power by intParam("power", 2)
    private var sign = 1
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { sign = if (power < 0) -1 else 1; if (power == 0.0) power = 1.0 }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r2 = a.y * a.y + a.x * a.x
        val x: Double
        if (sign == 1) x = a.x else { r2 = 1.0 / r2; x = a.x * r2 }
        val m = ellipticMuNu(a, x, r2)
        var mu = m[0]; var nu = m[1]
        nu = nu / power + M_2PI / power * floor(ctx.random() * power)
        mu /= power
        v.x += amount * cosh(mu) * cos(nu)
        v.y += amount * sinh(mu) * sin(nu)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { power = ((r.nextInt(6) + 2) * (if (r.nextBoolean()) 1 else -1)).toDouble() }
}

class EPush : Variation("ePush") {
    private var push by param("push", 0.0)
    private var dist by param("dist", 1.0)
    private var rotate by param("rotate", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val m = ellipticMuNu(a, a.x, a.y * a.y + a.x * a.x)
        var mu = m[0]; var nu = m[1]
        nu += rotate; mu *= dist; mu += push
        v.x += amount * cosh(mu) * cos(nu)
        v.y += amount * sinh(mu) * sin(nu)
        z(ctx, a, v, amount)
    }
}

class ERotate : Variation("eRotate") {
    private var rotate by param("rotate", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val m = ellipticMuNu(a, a.x, a.y * a.y + a.x * a.x)
        val xmax = m[2]
        val nu = fmod(m[1] + rotate + M_PI, M_2PI) - M_PI
        v.x += amount * xmax * cos(nu)
        v.y += amount * sqrt(xmax - 1.0) * sqrt(xmax + 1.0) * sin(nu)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { rotate = r.nextDouble() * M_2PI - M_PI }
}

class EScale : Variation("eScale") {
    private var scale by param("scale", 1.0)
    private var angle by param("angle", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val m = ellipticMuNu(a, a.x, a.y * a.y + a.x * a.x)
        var mu = m[0]; var nu = m[1]
        mu *= scale
        nu = fmod(fmod(scale * (nu + M_PI + angle), M_2PI * scale) - angle - scale * M_PI, M_2PI)
        if (nu > M_PI) nu -= M_2PI
        if (nu < -M_PI) nu += M_2PI
        v.x += amount * cosh(mu) * cos(nu)
        v.y += amount * sinh(mu) * sin(nu)
        z(ctx, a, v, amount)
    }
}

class CPow : Variation("cpow") {
    private var pr by param("r", 1.0)
    private var pi by param("i", 0.1)
    private var power by param("power", 1.5)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang0 = a.precalcAtanYX
        val lnr = 0.5 * ln(a.precalcSumsq)
        val va = 2.0 * M_PI / power
        val vc = pr / power
        val vd = pi / power
        val ang = vc * ang0 + vd * lnr + va * floor(power * ctx.random())
        val m = amount * exp(vc * lnr - vd * ang0)
        v.x += m * cos(ang); v.y += m * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { pr = r.nextDouble() * 2; pi = r.nextDouble() - 0.5; power = r.nextDouble() * 5 + 1 }
}

class CPow2 : Variation("cpow2") {
    private var pr by param("r", 1.0)
    private var pa by param("a", 0.0)
    private var divisor by param("divisor", 1.0)
    private var range by intParam("range", 1)
    private var ang = 0.0; private var c = 0.0; private var d = 0.0; private var halfC = 0.0; private var halfD = 0.0
    private var invRange = 0.0; private var fullRange = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (divisor == 0.0) divisor = 1.0
        if (range < 1) range = 1.0
        ang = 2.0 * M_PI / divisor
        c = pr * cos(M_PI / 2.0 * pa) / divisor
        d = pr * sin(M_PI / 2.0 * pa) / divisor
        halfC = c / 2.0; halfD = d / 2.0
        invRange = 0.5 / range
        fullRange = 2 * M_PI * range
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var an = a.precalcAtanYX
        var n = ctx.random(range.toInt())
        if (an < 0) n++
        an += 2 * M_PI * n
        if (cos(an * invRange) < ctx.random() * 2.0 - 1.0) an -= fullRange
        val lnr2 = ln(a.precalcSumsq)
        val r = amount * exp(halfC * lnr2 - d * an)
        val th = c * an + halfD * lnr2 + ang * floor(divisor * ctx.random())
        v.x += r * cos(th); v.y += r * sin(th)
        z(ctx, a, v, amount)
    }
}

class CPow3 : Variation("cpow3") {
    private var pr by param("r", 1.0)
    private var pd by param("d", 1.0)
    private var divisor by param("divisor", 1.0)
    private var spread by param("spread", 1.0)
    private var ang = 0.0; private var tc = 0.0; private var td = 0.0; private var halfC = 0.0; private var halfD = 0.0; private var coeff = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (divisor == 0.0) divisor = 1.0
        ang = 2.0 * M_PI / divisor
        val pA = atan2((if (pd < 0) -ln(-pd) else ln(pd)) * pr, 2 * M_PI)
        tc = cos(pA) * pr * cos(pA) / divisor
        td = cos(pA) * pr * sin(pA) / divisor
        halfC = tc / 2.0; halfD = td / 2.0
        coeff = if (td == 0.0) 0.0 else -0.095 * spread / td
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var an = a.precalcAtanYX
        if (an < 0) an += 2 * M_PI
        if (cos(an / 2) < ctx.random() * 2.0 - 1.0) an -= 2 * M_PI
        an += (if (ctx.random() < 0.5) 2 * M_PI else -2 * M_PI) * rnd(ln(ctx.random()) * coeff)
        val lnr2 = ln(a.precalcSumsq)
        val r = amount * exp(halfC * lnr2 - td * an)
        val th = tc * an + halfD * lnr2 + ang * floor(divisor * ctx.random())
        v.x += r * cos(th); v.y += r * sin(th)
        z(ctx, a, v, amount)
    }
}

class Rational3 : Variation("rational3") {
    private var pa by param("a", 0.5); private var pb by param("b", 0.0); private var pc by param("c", 0.25); private var pd by param("d", 1.0)
    private var pe by param("e", 0.0); private var pf by param("f", 0.9); private var pg by param("g", 0.0); private var ph by param("h", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val xsqr = a.x * a.x; val ysqr = a.y * a.y
        val xcb = xsqr * a.x; val ycb = ysqr * a.y
        val zt3 = xcb - 3 * a.x * ysqr; val zt2 = xsqr - ysqr
        val zb3 = 3 * xsqr * a.y - ycb; val zb2 = 2 * a.x * a.y
        val tr = pa * zt3 + pb * zt2 + pc * a.x + pd
        val ti = pa * zb3 + pb * zb2 + pc * a.y
        val br = pe * zt3 + pf * zt2 + pg * a.x + ph
        val bi = pe * zb3 + pf * zb2 + pg * a.y
        val den = 1 / (br * br + bi * bi)
        v.x += amount * (tr * br + ti * bi) * den
        v.y += amount * (ti * br - tr * bi) * den
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { params.forEach { it.value = r.nextDouble() * 2 - 1 } }
}

class Sec : Variation("sec") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cos(2.0 * a.x) + cosh(2.0 * a.y)
        if (d == 0.0) return
        val den = 2.0 / d
        v.x += amount * den * cos(a.x) * cosh(a.y)
        v.y += amount * den * sin(a.x) * sinh(a.y)
        z(ctx, a, v, amount)
    }
}

class Csc : Variation("csc") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cosh(2.0 * a.y) - cos(2.0 * a.x)
        if (d == 0.0) return
        val den = 2.0 / d
        v.x += amount * den * sin(a.x) * cosh(a.y)
        v.y -= amount * den * cos(a.x) * sinh(a.y)
        z(ctx, a, v, amount)
    }
}

class Cot : Variation("cot") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val den = 1.0 / (cosh(2.0 * a.y) - cos(2.0 * a.x))
        v.x += amount * den * sin(2.0 * a.x)
        v.y += amount * den * -1 * sinh(2.0 * a.y)
        z(ctx, a, v, amount)
    }
}

class Sech : Variation("sech") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cos(2.0 * a.y) + cosh(2.0 * a.x)
        if (d == 0.0) return
        val den = 2.0 / d
        v.x += amount * den * cos(a.y) * cosh(a.x)
        v.y -= amount * den * sin(a.y) * sinh(a.x)
        z(ctx, a, v, amount)
    }
}

class Csch : Variation("csch") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cosh(2.0 * a.x) - cos(2.0 * a.y)
        if (d == 0.0) return
        val den = 2.0 / d
        v.x += amount * den * sinh(a.x) * cos(a.y)
        v.y -= amount * den * cosh(a.x) * sin(a.y)
        z(ctx, a, v, amount)
    }
}

class Coth : Variation("coth") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cosh(2.0 * a.x) - cos(2.0 * a.y)
        if (d == 0.0) return
        val den = 1.0 / d
        v.x += amount * den * sinh(2.0 * a.x)
        v.y += amount * den * sin(2.0 * a.y)
        z(ctx, a, v, amount)
    }
}

class Atan : Variation("atan") {
    private var mode by intParam("Mode", 0)
    private var stretch by param("Stretch", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val norm = 1.0 / M_PI_2 * amount
        when (mode.toInt()) {
            0 -> { v.x += amount * a.x; v.y += norm * atan(stretch * a.y) }
            1 -> { v.x += norm * atan(stretch * a.x); v.y += amount * a.y }
            2 -> { v.x += norm * atan(stretch * a.x); v.y += norm * atan(stretch * a.y) }
        }
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { mode = r.nextInt(3).toDouble(); stretch = r.nextDouble() * 3 + 0.2 }
}

class Sigmoid : Variation("sigmoid") {
    private var shiftx by param("shiftx", 1.0)
    private var shifty by param("shifty", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var ax = 1.0; var ay = 1.0
        var sx = shiftx; var sy = shifty
        if (sx < 1 && sx > -1) { if (sx == 0.0) { sx = SMALL_EPSILON; ax = 1.0 } else { ax = if (sx < 0) -1.0 else 1.0; sx = 1 / sx } }
        if (sy < 1 && sy > -1) { if (sy == 0.0) { sy = SMALL_EPSILON; ay = 1.0 } else { ay = if (sy < 0) -1.0 else 1.0; sy = 1 / sy } }
        sx *= -5; sy *= -5
        val vv = abs(amount)
        val c0 = ax / (1.0 + exp(sx * a.x))
        val c1 = ay / (1.0 + exp(sy * a.y))
        v.x += vv * (2 * (c0 - 0.5))
        v.y += vv * (2 * (c1 - 0.5))
        z(ctx, a, v, amount)
    }
}

// ---------------------------------------------------------------- julia family

class PhoenixJulia : Variation("phoenix_julia") {
    private var power by param("power", 3.0)
    private var dist by param("dist", 1.0)
    private var xDistort by param("x_distort", -0.5)
    private var yDistort by param("y_distort", 0.0)
    private var invN = 0.0; private var inv2PiN = 0.0; private var cN = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (power == 0.0) power = 1.0
        invN = dist / power; inv2PiN = M_2PI / power; cN = dist / power / 2.0
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val preX = a.x * (xDistort + 1.0)
        val preY = a.y * (yDistort + 1.0)
        val ang = atan2(preY, preX) * invN + ctx.random(Int.MAX_VALUE) * inv2PiN
        val r = amount * (sqr(a.x) + sqr(a.y)).pow(cN)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        power = ((r.nextInt(5) + 2) * (if (r.nextBoolean()) 1 else -1)).toDouble()
        xDistort = r.nextDouble() - 0.8; yDistort = r.nextDouble() * 0.6 - 0.3
    }
}

class JuliaC : Variation("juliac") {
    private var re by param("re", 3.5)
    private var im by param("im", 0.5)
    private var dist by param("dist", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val reI = 1.0 / (re + SMALL_EPSILON)
        val imI = im / 100.0
        val arg = atan2(a.y, a.x) + fmod(ctx.random(Int.MAX_VALUE).toDouble(), re) * 2.0 * M_PI
        val lnmod = dist * 0.5 * ln(a.x * a.x + a.y * a.y)
        val ang = arg * reI + lnmod * imI
        val mod2 = exp(lnmod * reI - arg * imI)
        v.x += amount * mod2 * cos(ang); v.y += amount * mod2 * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { re = (r.nextInt(5) + 2) + 0.5; im = r.nextDouble() * 100 - 50 }
}

class JuliaN2 : Variation("julian2") {
    private var power by intParam("power", 3)
    private var dist by param("dist", 1.0)
    private var pa by param("a", 1.0); private var pb by param("b", 0.0); private var pc by param("c", 0.0)
    private var pd by param("d", 1.0); private var pe by param("e", 0.0); private var pf by param("f", 0.0)
    private var absN = 1; private var cN = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        absN = abs(power.toInt()).coerceAtLeast(1)
        cN = if (power == 0.0) 0.0 else dist / power * 0.5
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (power == 0.0) return
        val x = pa * a.x + pb * a.y + pe
        val y = pc * a.x + pd * a.y + pf
        val ang = (atan2(y, x) + M_2PI * (ctx.random(Int.MAX_VALUE) % absN)) / power
        val r = amount * (sqr(x) + sqr(y)).pow(cN)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        power = ((r.nextInt(6) + 2) * (if (r.nextBoolean()) 1 else -1)).toDouble()
        pb = r.nextDouble() - 0.5; pc = r.nextDouble() - 0.5; pe = r.nextDouble() - 0.5; pf = r.nextDouble() - 0.5
    }
}

class NPolar : Variation("npolar") {
    private var parity by intParam("parity", 0)
    private var n by intParam("n", 1)
    private var vvar = 0.0; private var vvar2 = 0.0; private var nnz = 1; private var isOdd = 0; private var cn = 0.0; private var absN = 1
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        nnz = if (n == 0.0) 1 else n.toInt()
        vvar = amount / M_PI; vvar2 = vvar * 0.5
        absN = abs(nnz); cn = 1.0 / nnz / 2.0
        isOdd = abs(parity.toInt()) % 2
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var x = if (isOdd != 0) a.x else vvar * atan2(a.x, a.y)
        var y = if (isOdd != 0) a.y else vvar2 * ln(a.x * a.x + a.y * a.y)
        val angle = (atan2(y, x) + M_2PI * (ctx.random(Int.MAX_VALUE) % absN)) / nnz
        val r = amount * (sqr(x) + sqr(y)).pow(cn) * (if (isOdd == 0) 1.0 else parity)
        val sina = sin(angle) * r
        val cosa = cos(angle) * r
        x = if (isOdd != 0) cosa else vvar2 * ln(cosa * cosa + sina * sina)
        y = if (isOdd != 0) sina else vvar * atan2(cosa, sina)
        v.x += x; v.y += y
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { parity = r.nextInt(4).toDouble(); n = (r.nextInt(8) + 1).toDouble() }
}

class Murl : Variation("murl") {
    private var c by param("c", 0.1)
    private var power by intParam("power", 1)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var cc = c
        if (power != 1.0) cc /= (power - 1)
        val p2 = power / 2.0
        val vp = amount * (cc + 1)
        val ang = atan2(a.y, a.x) * power
        val r = cc * (sqr(a.x) + sqr(a.y)).pow(p2)
        val re = r * cos(ang) + 1
        val im = r * sin(ang)
        val rl = vp / (sqr(re) + sqr(im))
        v.x += rl * (a.x * re + a.y * im)
        v.y += rl * (a.y * re - a.x * im)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { c = r.nextDouble() * 2 - 1; power = (r.nextInt(6) + 1).toDouble() }
}

class Murl2 : Variation("murl2") {
    private var c by param("c", 0.1)
    private var power by intParam("power", 3)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val p2 = power / 2.0
        val invp: Double; val vp: Double
        if (power != 0.0) {
            invp = 1.0 / power
            vp = if (c == -1.0) 0.0 else amount * (c + 1).pow(2.0 / power)
        } else {
            invp = 100000000000.0
            vp = amount * (c + 1).pow(4)
        }
        var ang = atan2(a.y, a.x) * power
        var r = c * (sqr(a.x) + sqr(a.y)).pow(p2)
        var re = r * cos(ang) + 1
        var im = r * sin(ang)
        r = (sqr(re) + sqr(im)).pow(invp)
        ang = atan2(im, re) * 2.0 * invp
        re = r * cos(ang); im = r * sin(ang)
        val rl = vp / sqr(r)
        v.x += rl * (a.x * re + a.y * im)
        v.y += rl * (a.y * re - a.x * im)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { c = r.nextDouble() * 2 - 1; power = (r.nextInt(6) + 1).toDouble() }
}

class Hypershift : Variation("hypershift") {
    private var shift by param("shift", 2.0)
    private var stretch by param("stretch", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val scale = 1 - shift * shift
        var rad = 1 / (a.x * a.x + a.y * a.y)
        val x = rad * a.x + shift
        val y = rad * a.y
        rad = amount * scale / (x * x + y * y)
        v.x += rad * x + shift
        v.y += rad * y * stretch
        z(ctx, a, v, amount)
    }
}

class Hypershift2 : Variation("hypershift2") {
    private var p by intParam("p", 3)
    private var q by intParam("q", 7)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val pq = M_PI / q; val pp = M_PI / p
        val spq = sin(pq); val spp = sin(pp)
        var shift = sin(M_PI * 0.5 - pq - pp)
        shift /= sqrt(1 - sqr(spq) - sqr(spp))
        var scale2 = 1 / sqrt(sqr(sin(M_PI / 2 + pp)) / sqr(spq) - 1.0)
        scale2 *= (sin(M_PI / 2 + pp) / spq - 1.0)
        val scale = 1 - shift * shift
        val fx = a.x * scale2; val fy = a.y * scale2
        var rad = 1 / (fx * fx + fy * fy)
        val x = rad * fx + shift
        val y = rad * fy
        rad = amount * scale / (x * x + y * y)
        val angle = ((ctx.random(Int.MAX_VALUE) % p.toInt()) * 2.0 + 1.0) * M_PI / p
        val bx = rad * x + shift; val by = rad * y
        val ca = cos(angle); val sa = sin(angle)
        v.x = ca * bx - sa * by
        v.y = sa * bx + ca * by
        v.z = a.z * rad
    }
}

class Hypertile1 : Variation("hypertile1") {
    private var p by intParam("p", 3)
    private var q by intParam("q", 7)
    private var pa = 0.0; private var r = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        pa = 2 * M_PI / p
        val r2 = 1.0 - (cos(2 * M_PI / p) - 1.0) / (cos(2 * M_PI / p) + cos(2 * M_PI / q))
        r = if (r2 > 0) 1.0 / sqrt(r2) else 1.0
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rpa = ctx.random(Int.MAX_VALUE) * pa
        val re = r * cos(rpa); val im = r * sin(rpa)
        val aa = a.x + re; val b = a.y - im
        val c = re * a.x - im * a.y + 1
        val d = re * a.y + im * a.x
        val vr = amount / (sqr(c) + sqr(d))
        v.x += vr * (aa * c + b * d)
        v.y += vr * (b * c - aa * d)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { p = (r.nextInt(6) + 3).toDouble(); q = (r.nextInt(6) + 3).toDouble() }
}

class Hypertile2 : Variation("hypertile2") {
    private var p by intParam("p", 3)
    private var q by intParam("q", 7)
    private var pa = 0.0; private var r = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        pa = 2.0 * M_PI / p
        val r2 = 1.0 - (cos(2.0 * M_PI / p) - 1.0) / (cos(2.0 * M_PI / p) + cos(2.0 * M_PI / q))
        r = if (r2 > 0) 1.0 / sqrt(r2) else 1.0
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val aa = a.x + r; val b = a.y
        val c = r * a.x + 1.0; val d = r * a.y
        val x = aa * c + b * d; val y = b * c - aa * d
        val vr = amount / (sqr(c) + sqr(d))
        val rpa = pa * ctx.random(Int.MAX_VALUE)
        val sa = sin(rpa); val ca = cos(rpa)
        v.x += vr * (x * ca + y * sa)
        v.y += vr * (y * ca - x * sa)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { p = (r.nextInt(6) + 3).toDouble(); q = (r.nextInt(6) + 3).toDouble() }
}

// ---------------------------------------------------------------- shapes & curves

class Cardioid : Variation("cardioid") {
    private var ca by param("a", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val an = atan2(a.y, a.x)
        val r = amount * sqrt(sqr(a.x) + sqr(a.y) + sin(an * ca) + 1.0)
        v.x += r * cos(an); v.y += r * sin(an)
        z(ctx, a, v, amount)
    }
}

class CannabisCurve : Variation("cannabiscurve_wf") {
    private var filled by param("filled", 0.85)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var an = a.precalcAtan
        var r = (1 + 9.0 / 10.0 * cos(8.0 * an)) * (1 + 1.0 / 10.0 * cos(24.0 * an)) *
                (9.0 / 10.0 + 1.0 / 10.0 * cos(200.0 * an)) * (1.0 + sin(an))
        an += M_PI / 2.0
        if (filled > 0 && filled > ctx.random()) r *= ctx.random()
        v.x += amount * sin(an) * r; v.y += amount * cos(an) * r
        z(ctx, a, v, amount)
    }
}

class CloverLeaf : Variation("cloverleaf_wf") {
    private var filled by param("filled", 0.85)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val an = a.precalcAtan
        var r = sin(2 * an) + 0.25 * sin(6 * an)
        if (filled > 0 && filled > ctx.random()) r *= ctx.random()
        v.x += amount * sin(an) * r; v.y += amount * cos(an) * r
        z(ctx, a, v, amount)
    }
}

class RoseWF : Variation("rose_wf") {
    private var amp by param("amp", 0.5)
    private var waves by intParam("waves", 4)
    private var filled by param("filled", 0.85)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val an = a.precalcAtan
        var r = amp * cos(waves * an)
        if (filled > 0 && filled > ctx.random()) r *= ctx.random()
        v.x += amount * sin(an) * r; v.y += amount * cos(an) * r
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { amp = r.nextDouble() + 0.2; waves = (r.nextInt(9) + 2).toDouble(); filled = r.nextDouble() }
}

class Lissajous : Variation("lissajous") {
    private var tmin by param("tmin", -M_PI); private var tmax by param("tmax", M_PI)
    private var la by param("a", 3.0); private var lb by param("b", 2.0)
    private var lc by param("c", 0.0); private var ld by param("d", 0.0); private var le by param("e", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = (tmax - tmin) * ctx.random() + tmin
        val y = ctx.random() - 0.5
        val x1 = sin(la * t + ld); val y1 = sin(lb * t)
        v.x += amount * (x1 + lc * t + le * y)
        v.y += amount * (y1 + lc * t + le * y)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { la = (r.nextInt(7) + 1).toDouble(); lb = (r.nextInt(7) + 1).toDouble(); ld = r.nextDouble() * M_PI; le = r.nextDouble() * 0.3 }
}

class Spirograph : Variation("spirograph") {
    private var sa by param("a", 3.0); private var sb by param("b", 2.0); private var sd by param("d", 0.0)
    private var tmin by param("tmin", -1.0); private var tmax by param("tmax", 1.0)
    private var ymin by param("ymin", -1.0); private var ymax by param("ymax", 1.0)
    private var c1 by param("c1", 0.0); private var c2 by param("c2", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = (tmax - tmin) * ctx.random() + tmin
        val y = (ymax - ymin) * ctx.random() + ymin
        val x1 = (sa + sb) * cos(t) - c1 * cos((sa + sb) / sb * t)
        val y1 = (sa + sb) * sin(t) - c2 * sin((sa + sb) / sb * t)
        v.x += amount * (x1 + sd * cos(t) + y)
        v.y += amount * (y1 + sd * sin(t) + y)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        sa = r.nextDouble() * 4 + 1; sb = r.nextDouble() * 3 + 0.5; c1 = r.nextDouble() * 2; c2 = r.nextDouble() * 2
        tmin = -M_PI * (1 + r.nextInt(4)); tmax = -tmin; ymin = -0.1; ymax = 0.1
    }
}

class Epispiral : Variation("epispiral") {
    private var n by param("n", 6.0)
    private var thickness by param("thickness", 0.0)
    private var holes by param("holes", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val theta = atan2(a.y, a.x)
        var t = -holes
        val d = cos(n * theta)
        if (d == 0.0) return
        t += if (abs(thickness) > EPSILON) ctx.random() * thickness * (1.0 / d) else 1.0 / d
        v.x += amount * t * cos(theta); v.y += amount * t * sin(theta)
        z(ctx, a, v, amount)
    }
}

class Petal : Variation("petal") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ca = cos(a.x)
        val cxy = cos(a.x) * cos(a.y)
        val sxy = sin(a.x) * cos(a.y)
        v.x += amount * ca * cxy * cxy * cxy
        v.y += amount * ca * sxy * sxy * sxy
        z(ctx, a, v, amount)
    }
}

class XHeart : Variation("xheart") {
    private var angle by param("angle", 0.0)
    private var ratio by param("ratio", 0.0)
    private var rat = 6.0; private var cosa = 0.0; private var sina = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        val ang = M_PI_4 + 0.5 * M_PI_4 * angle
        sina = sin(ang); cosa = cos(ang); rat = 6 + 2 * ratio
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r24 = a.x * a.x + a.y * a.y + 4
        if (r24 == 0.0) r24 = 1.0
        val bx = 4 / r24; val by = rat / r24
        val x = cosa * (bx * a.x) - sina * (by * a.y)
        val y = sina * (bx * a.x) + cosa * (by * a.y)
        v.x += amount * x
        v.y += if (x > 0) amount * y else -amount * y
        z(ctx, a, v, amount)
    }
}

class Squarize : Variation("squarize") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val s = sqrt(sqr(a.x) + sqr(a.y))
        var an = atan2(a.y, a.x)
        if (an < 0.0) an += M_2PI
        val p = 4.0 * s * an * M_1_PI
        when {
            p <= 1.0 * s -> { v.x += amount * s; v.y += amount * p }
            p <= 3.0 * s -> { v.x += amount * (2.0 * s - p); v.y += amount * s }
            p <= 5.0 * s -> { v.x -= amount * s; v.y += amount * (4.0 * s - p) }
            p <= 7.0 * s -> { v.x -= amount * (6.0 * s - p); v.y -= amount * s }
            else -> { v.x += amount * s; v.y -= amount * (8.0 * s - p) }
        }
        z(ctx, a, v, amount)
    }
}

class Squish : Variation("squish") {
    private var power by intParam("power", 2)
    private var invPower = 0.5
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { if (power == 0.0) power = 1.0; invPower = 1.0 / power }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x = abs(a.x); val y = abs(a.y)
        val s: Double; var p: Double
        if (x > y) { s = x; p = if (a.x > 0.0) a.y else 4.0 * s - a.y }
        else { s = y; p = if (a.y > 0.0) 2.0 * s - a.x else 6.0 * s + a.x }
        p = invPower * (p + 8.0 * s * floor(power * ctx.random()))
        when {
            p <= 1.0 * s -> { v.x += amount * s; v.y += amount * p }
            p <= 3.0 * s -> { v.x += amount * (2.0 * s - p); v.y += amount * s }
            p <= 5.0 * s -> { v.x -= amount * s; v.y += amount * (4.0 * s - p) }
            p <= 7.0 * s -> { v.x -= amount * (6.0 * s - p); v.y -= amount * s }
            else -> { v.x += amount * s; v.y -= amount * (8.0 * s - p) }
        }
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { power = (r.nextInt(6) + 1).toDouble() }
}

class Circlize2 : Variation("circlize2") {
    private var hole by param("hole", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val absx = abs(a.x); val absy = abs(a.y)
        val perimeter: Double; val side: Double
        if (absx >= absy) { perimeter = if (a.x >= absy) absx + a.y else 5.0 * absx - a.y; side = absx }
        else { perimeter = if (a.y >= absx) 3.0 * absy - a.x else 7.0 * absy + a.x; side = absy }
        val r = amount * (side + hole)
        val an = M_PI_4 * perimeter / side - M_PI_4
        v.x += r * cos(an); v.y += r * sin(an)
        z(ctx, a, v, amount)
    }
}

class Loonie2 : Variation("loonie2") {
    private var sides by intParam("sides", 4)
    private var star by param("star", 0.15)
    private var circle by param("circle", 0.25)
    private var sqrvvar = 0.0; private var sina = 0.0; private var cosa = 0.0; private var sins = 0.0; private var coss = 0.0; private var sinc = 0.0; private var cosc = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (sides < 1) sides = 1.0
        sqrvvar = amount * amount
        var an = M_2PI / sides
        sina = sin(an); cosa = cos(an)
        an = -M_PI_2 * star; sins = sin(an); coss = cos(an)
        an = M_PI_2 * circle; sinc = sin(an); cosc = cos(an)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var xrt = a.x; var yrt = a.y
        var r2 = xrt * coss + abs(yrt) * sins
        val circ = sqrt(sqr(xrt) + sqr(yrt))
        var i = 0
        val n = sides.toInt()
        while (i < n - 1) {
            val swp = xrt * cosa - yrt * sina
            yrt = xrt * sina + yrt * cosa
            xrt = swp
            r2 = max(r2, xrt * coss + abs(yrt) * sins)
            i++
        }
        r2 = r2 * cosc + circ * sinc
        r2 = if (i > 1) sqr(r2) else abs(r2) * r2
        if (r2 > 0 && r2 < sqrvvar) {
            val r = amount * sqrt(abs(sqrvvar / r2 - 1.0))
            v.x += r * a.x; v.y += r * a.y
        } else if (r2 < 0) {
            val r = amount / sqrt(abs(sqrvvar / r2) - 1.0)
            v.x += r * a.x; v.y += r * a.y
        } else {
            v.x += amount * a.x; v.y += amount * a.y
        }
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { sides = (r.nextInt(8) + 3).toDouble(); star = r.nextDouble() - 0.5; circle = r.nextDouble() - 0.5 }
}

class Scry2 : Variation("scry2") {
    private var sides by intParam("sides", 4)
    private var star by param("star", 0.0)
    private var circle by param("circle", 0.0)
    private var sina = 0.0; private var cosa = 0.0; private var sins = 0.0; private var coss = 0.0; private var sinc = 0.0; private var cosc = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (sides < 1) sides = 1.0
        var an = M_2PI / sides
        sina = sin(an); cosa = cos(an)
        an = -M_PI_2 * star; sins = sin(an); coss = cos(an)
        an = M_PI_2 * circle; sinc = sin(an); cosc = cos(an)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var xrt = a.x; var yrt = a.y
        var r2 = xrt * coss + abs(yrt) * sins
        val circ = sqrt(sqr(xrt) + sqr(yrt))
        var i = 0
        val n = sides.toInt()
        while (i < n - 1) {
            val swp = xrt * cosa - yrt * sina
            yrt = xrt * sina + yrt * cosa
            xrt = swp
            r2 = max(r2, xrt * coss + abs(yrt) * sins)
            i++
        }
        r2 = r2 * cosc + circ * sinc
        val r1 = r2
        r2 = if (i > 1) sqr(r2) else abs(r2) * r2
        val d = r1 * (r2 + 1.0 / amount)
        if (d == 0.0) return
        val r = 1.0 / d
        v.x += a.x * r; v.y += a.y * r
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { sides = (r.nextInt(8) + 3).toDouble(); star = r.nextDouble() - 0.5; circle = r.nextDouble() - 0.5 }
}

class Asteria : Variation("asteria") {
    private var alpha by param("alpha", 0.0)
    private var sina = 0.0; private var cosa = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { sina = sin(M_PI * alpha); cosa = cos(M_PI * alpha) }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x0 = amount * a.x; val y0 = amount * a.y
        var xx = x0; var yy = y0
        val r = sqr(xx) + sqr(yy)
        xx = sqr(abs(xx) - 1.0); yy = sqr(abs(yy) - 1.0)
        val r2 = sqrt(yy + xx)
        var in1 = r < 1.0
        val out2 = r2 < 1.0
        in1 = if (in1 && out2) ctx.random() > 0.35 else !in1
        if (in1) {
            v.x += x0; v.y += y0
        } else {
            xx = x0 * cosa - y0 * sina
            yy = x0 * sina + y0 * cosa
            val nx = xx / sqrt(1.0 - yy * yy) * (1.0 - sqrt(1.0 - sqr(-abs(yy) + 1.0)))
            xx = nx * cosa + yy * sina
            yy = -nx * sina + yy * cosa
            v.x += xx; v.y += yy
        }
        z(ctx, a, v, amount)
    }
}

// ---------------------------------------------------------------- spherical-ish & polar

class RoundSpher : Variation("roundspher") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = sqr(a.x) + sqr(a.y)
        val e = 1 / d + sqr(M_2_PI)
        v.x += amount * (amount / d * a.x / e)
        v.y += amount * (amount / d * a.y / e)
        z(ctx, a, v, amount)
    }
}

class TwoFace : Variation("twoface") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = amount
        if (a.x > 0.0) r /= sqr(a.x) + sqr(a.y)
        v.x += r * a.x; v.y += r * a.y
        z(ctx, a, v, amount)
    }
}

class WedgeSph : Variation("wedge_sph") {
    private var angle by param("angle", 0.2)
    private var hole by param("hole", 0.2)
    private var count by param("count", 2.0)
    private var swirl by param("swirl", 0.3)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = 1.0 / (a.precalcSqrt + SMALL_EPSILON)
        var an = a.precalcAtanYX + swirl * r
        val c = floor((count * an + M_PI) * M_1_PI * 0.5)
        val compFac = 1 - angle * count * M_1_PI * 0.5
        an = an * compFac + c * angle
        r = amount * (r + hole)
        v.x += r * cos(an); v.y += r * sin(an)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { angle = r.nextDouble() * M_PI; hole = r.nextDouble() * 0.5; count = (r.nextInt(6) + 1).toDouble(); swirl = r.nextDouble() * 0.6 }
}

class WDisc : Variation("wdisc") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var an = M_PI / (sqrt(sqr(a.x) + sqr(a.y)) + 1.0)
        val r = atan2(a.y, a.x) * M_1_PI
        if (r > 0.0) an = M_PI - an
        v.x += amount * r * cos(an); v.y += amount * r * sin(an)
        z(ctx, a, v, amount)
    }
}

class Hole : Variation("hole") {
    private var ha by param("a", 1.0)
    private var inside by intParam("inside", 0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val alpha = atan2(a.y, a.x)
        val delta = (alpha / M_PI + 1.0).pow(ha)
        val r = if (inside != 0.0) amount * delta / (a.x * a.x + a.y * a.y + delta)
        else amount * sqrt(a.x * a.x + a.y * a.y + delta)
        v.x += r * cos(alpha); v.y += r * sin(alpha)
        z(ctx, a, v, amount)
    }
}

class Target : Variation("target") {
    private var even by param("even", 0.4)
    private var odd by param("odd", 0.9)
    private var size by param("size", 0.8)
    private var tSize2 = 0.4
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { tSize2 = 0.5 * size }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var an = atan2(a.y, a.x)
        val r = sqrt(sqr(a.x) + sqr(a.y))
        var t = ln(r)
        if (t < 0.0) t -= tSize2
        t = fmod(abs(t), size)
        an += if (t < tSize2) even else odd
        v.x += r * cos(an); v.y += r * sin(an)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { even = -M_PI + r.nextDouble() + M_2PI; odd = -M_PI + r.nextDouble() + M_2PI; size = 0.5 + r.nextDouble() * 0.75 }
}

class Circus : Variation("circus") {
    private var scale by param("scale", 0.92)
    private var scale1 = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { scale1 = 1.0 / scale }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = sqrt(a.x * a.x + a.y * a.y)
        val an = atan2(a.y, a.x)
        r *= if (r <= 1.0) scale else scale1
        v.x += amount * r * cos(an); v.y += amount * r * sin(an)
        z(ctx, a, v, amount)
    }
}

class LayeredSpiral : Variation("layered_spiral") {
    private var radius by param("radius", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val aa = a.x * radius
        val t = sqr(a.x) + sqr(a.y) + EPSILON
        v.x += amount * aa * cos(t); v.y += amount * aa * sin(t)
        z(ctx, a, v, amount)
    }
}

class Panorama1 : Variation("panorama1") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var aux = 1.0 / sqrt(a.x * a.x + a.y * a.y + 1.0)
        val x1 = a.x * aux; val y1 = a.y * aux
        aux = sqrt(x1 * x1 + y1 * y1)
        v.x += amount * atan2(x1, y1) * M_1_PI
        v.y += amount * (aux - 0.5)
        z(ctx, a, v, amount)
    }
}

class Panorama2 : Variation("panorama2") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var aux = 1.0 / (sqrt(a.x * a.x + a.y * a.y) + 1.0)
        val x1 = a.x * aux; val y1 = a.y * aux
        aux = sqrt(x1 * x1 + y1 * y1)
        v.x += amount * atan2(x1, y1) * M_1_PI
        v.y += amount * (aux - 0.5)
        z(ctx, a, v, amount)
    }
}

class Bipolar2 : Variation("bipolar2") {
    private var shift by param("shift", 0.0)
    private var ba by param("a", 1.0); private var bb by param("b", 2.0); private var bc by param("c", 0.5)
    private var bd by param("d", 1.0); private var be by param("e", 2.0); private var f1 by param("f1", 0.25)
    private var g1 by param("g1", 1.0); private var bh by param("h", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x2y2 = (a.x * a.x + a.y * a.y) * g1
        val t = x2y2 + ba
        val x2 = bb * a.x
        val ps = -M_PI_2 * shift
        var y = bc * atan2(be * a.y, x2y2 - bd) + ps
        if (y > M_PI_2) y = -M_PI_2 + fmod(y + M_PI_2, M_PI)
        else if (y < -M_PI_2) y = M_PI_2 - fmod(M_PI_2 - y, M_PI)
        val f = t + x2; val g = t - x2
        if (g == 0.0 || f / g <= 0) return
        v.x += amount * f1 * M_2_PI * ln((t + x2) / (t - x2))
        v.y += amount * M_2_PI * y * bh
        z(ctx, a, v, amount)
    }
}

class Ortho : Variation("ortho") {
    private var pin by param("in", 0.0)
    private var pout by param("out", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = sqr(a.x) + sqr(a.y)
        if (r < 1.0) {
            if (a.x >= 0.0) {
                val xo = (r + 1.0) / (2.0 * a.x)
                val ro = sqrt(sqr(a.x - xo) + sqr(a.y))
                val theta = atan2(1.0, ro)
                val an = fmod(pin * theta + atan2(a.y, xo - a.x) + theta, 2.0 * theta) - theta
                v.x += amount * (xo - cos(an) * ro)
                v.y += amount * sin(an) * ro
            } else {
                val xo = -(r + 1.0) / (2.0 * a.x)
                val ro = sqrt(sqr(-a.x - xo) + sqr(a.y))
                val theta = atan2(1.0, ro)
                val an = fmod(pin * theta + atan2(a.y, xo + a.x) + theta, 2.0 * theta) - theta
                v.x -= amount * (xo - cos(an) * ro)
                v.y += amount * sin(an) * ro
            }
        } else {
            r = 1.0 / sqrt(r)
            var ta = atan2(a.y, a.x)
            var x = r * cos(ta); var y = r * sin(ta)
            if (x >= 0.0) {
                val xo = (sqr(x) + sqr(y) + 1.0) / (2.0 * x)
                val ro = sqrt(sqr(x - xo) + sqr(y))
                val theta = atan2(1.0, ro)
                val an = fmod(pout * theta + atan2(y, xo - x) + theta, 2.0 * theta) - theta
                x = xo - cos(an) * ro; y = sin(an) * ro
                ta = atan2(y, x)
                r = 1.0 / sqrt(sqr(x) + sqr(y))
                v.x += amount * r * cos(ta); v.y += amount * r * sin(ta)
            } else {
                val xo = -(sqr(x) + sqr(y) + 1.0) / (2.0 * x)
                val ro = sqrt(sqr(-x - xo) + sqr(y))
                val theta = atan2(1.0, ro)
                val an = fmod(pout * theta + atan2(y, xo + x) + theta, 2.0 * theta) - theta
                x = xo - cos(an) * ro; y = sin(an) * ro
                ta = atan2(y, x)
                r = 1.0 / sqrt(sqr(x) + sqr(y))
                v.x -= amount * r * cos(ta); v.y += amount * r * sin(ta)
            }
        }
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { pin = r.nextDouble() * 4 - 2; pout = r.nextDouble() * 4 - 2 }
}

class DeltaA : Variation("deltaA") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val avgr = amount * (sqrt(sqr(a.y) + sqr(a.x + 1.0)) / sqrt(sqr(a.y) + sqr(a.x - 1.0)))
        val avga = (atan2(a.y, a.x - 1.0) - atan2(a.y, a.x + 1.0)) / 2.0
        v.x += avgr * cos(avga); v.y += avgr * sin(avga)
        z(ctx, a, v, amount)
    }
}

class Fourth : Variation("fourth") {
    private var spin by param("spin", M_PI)
    private var space by param("space", 0.1)
    private var twist by param("twist", 0.2)
    private var fx by param("x", 0.3)
    private var fy by param("y", 0.12)
    private var sqrvvar = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { sqrvvar = amount * amount }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (a.x > 0.0 && a.y > 0.0) {
            val an = atan2(a.y, a.x)
            val r = 1.0 / sqrt(sqr(a.x) + sqr(a.y))
            v.x += amount * r * cos(an); v.y += amount * r * sin(an)
        } else if (a.x > 0.0 && a.y < 0.0) {
            val r2 = sqr(a.x) + sqr(a.y)
            if (r2 < sqrvvar) {
                val r = amount * sqrt(sqrvvar / r2 - 1.0)
                v.x += r * a.x; v.y += r * a.y
            } else { v.x += amount * a.x; v.y += amount * a.y }
        } else if (a.x < 0.0 && a.y > 0.0) {
            val x = a.x - fx; val y = a.y + fy
            var r = sqrt(x * x + y * y)
            if (r < amount) {
                val an = atan2(y, x) + spin + twist * (amount - r)
                r *= amount
                v.x += r * cos(an) + fx; v.y += r * sin(an) - fy
            } else {
                r = amount * (1.0 + space / r)
                v.x += r * x + fx; v.y += r * y - fy
            }
        } else { v.x += amount * a.x; v.y += amount * a.y }
        z(ctx, a, v, amount)
    }
}

// ---------------------------------------------------------------- tiling, folding, grids

class Boarders : Variation("boarders") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rx = Math.rint(a.x); val ry = Math.rint(a.y)
        val ox = a.x - rx; val oy = a.y - ry
        if (ctx.random() >= 0.75) {
            v.x += amount * (ox * 0.5 + rx); v.y += amount * (oy * 0.5 + ry)
        } else if (abs(ox) >= abs(oy)) {
            if (ox >= 0.0) { v.x += amount * (ox * 0.5 + rx + 0.25); v.y += amount * (oy * 0.5 + ry + 0.25 * oy / ox) }
            else { v.x += amount * (ox * 0.5 + rx - 0.25); v.y += amount * (oy * 0.5 + ry - 0.25 * oy / ox) }
        } else {
            if (oy >= 0.0) { v.y += amount * (oy * 0.5 + ry + 0.25); v.x += amount * (ox * 0.5 + rx + ox / oy * 0.25) }
            else { v.y += amount * (oy * 0.5 + ry - 0.25); v.x += amount * (ox * 0.5 + rx - ox / oy * 0.25) }
        }
        z(ctx, a, v, amount)
    }
}

class Boarders2 : Variation("boarders2") {
    private var bc by param("c", 0.4)
    private var left by param("left", 0.65)
    private var right by param("right", 0.35)
    private var c = 0.4; private var cl = 0.0; private var cr = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        c = abs(bc); cl = abs(left); cr = abs(right)
        if (c == 0.0) c = EPSILON
        if (cl == 0.0) cl = EPSILON
        if (cr == 0.0) cr = EPSILON
        cl *= c; cr = c + c * cr
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rx = Math.rint(a.x); val ry = Math.rint(a.y)
        val ox = a.x - rx; val oy = a.y - ry
        if (ctx.random() >= cr) {
            v.x += amount * (ox * c + rx); v.y += amount * (oy * c + ry)
        } else if (abs(ox) >= abs(oy)) {
            if (ox >= 0.0) { v.x += amount * (ox * c + rx + cl); v.y += amount * (oy * c + ry + cl * oy / ox) }
            else { v.x += amount * (ox * c + rx - cl); v.y += amount * (oy * c + ry - cl * oy / ox) }
        } else {
            if (oy >= 0.0) { v.y += amount * (oy * c + ry + cl); v.x += amount * (ox * c + rx + ox / oy * cl) }
            else { v.y += amount * (oy * c + ry - cl); v.x += amount * (ox * c + rx - ox / oy * cl) }
        }
        z(ctx, a, v, amount)
    }
}

class Modulus : Variation("modulus") {
    private var mx by param("x", 0.2)
    private var my by param("y", 0.5)
    private var xr = 0.4; private var yr = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { xr = 2.0 * mx; yr = 2.0 * my }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += when {
            a.x > mx -> amount * (-mx + fmod(a.x + mx, xr))
            a.x < -mx -> amount * (mx - fmod(mx - a.x, xr))
            else -> amount * a.x
        }
        v.y += when {
            a.y > my -> amount * (-my + fmod(a.y + my, yr))
            a.y < -my -> amount * (my - fmod(my - a.y, yr))
            else -> amount * a.y
        }
        z(ctx, a, v, amount)
    }
}

class Intersection : Variation("intersection") {
    private var xwidth by param("xwidth", 5.0); private var xtilesize by param("xtilesize", 0.5)
    private var xmod1 by param("xmod1", 0.3); private var xmod2 by param("xmod2", 1.0); private var xheight by param("xheight", 0.5)
    private var yheight by param("yheight", 5.0); private var ytilesize by param("ytilesize", 0.5)
    private var ymod1 by param("ymod1", 0.3); private var ymod2 by param("ymod2", 1.0); private var ywidth by param("ywidth", 0.5)
    private var xr1 = 0.3; private var yr1 = 0.3
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { xr1 = xmod2 * xmod1; yr1 = ymod2 * ymod1 }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (ctx.random() < 0.5) {
            val x = if (ctx.random() < 0.5) xwidth else -xwidth
            v.x += xtilesize * (a.x + rnd(x * ln(ctx.random())))
            v.y += when {
                a.y > xmod1 -> xheight * (-xmod1 + fmod(a.y + xmod1, xr1))
                a.y < -xmod1 -> xheight * (xmod1 - fmod(xmod1 - a.y, xr1))
                else -> xheight * a.y
            }
        } else {
            val y = if (ctx.random() < 0.5) yheight else -yheight
            v.y += ytilesize * (a.y + rnd(y * ln(ctx.random())))
            v.x += when {
                a.x > ymod1 -> ywidth * (-ymod1 + fmod(a.x + ymod1, yr1))
                a.x < -ymod1 -> ywidth * (ymod1 - fmod(ymod1 - a.x, yr1))
                else -> ywidth * a.x
            }
        }
        z(ctx, a, v, amount)
    }
}

class TileLog : Variation("tile_log") {
    private var spread by param("spread", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x = if (ctx.random() < 0.5) spread else -spread
        v.x += amount * (a.x + rnd(x * ln(ctx.random())))
        v.y += amount * a.y
        v.z += amount * a.z
    }
}

class Waffle : Variation("waffle") {
    private var slices by intParam("slices", 6)
    private var xthickness by param("xthickness", 0.5)
    private var ythickness by param("ythickness", 0.5)
    private var rotation by param("rotation", 0.0)
    private var vsinr = 0.0; private var vcosr = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (slices < 1) slices = 1.0
        vcosr = amount * cos(rotation); vsinr = amount * sin(rotation)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val s = slices.toInt()
        var aa = 0.0; var r = 0.0
        when (ctx.random(5)) {
            0 -> { aa = (ctx.random(s) + ctx.random() * xthickness) / s; r = (ctx.random(s) + ctx.random() * ythickness) / s }
            1 -> { aa = (ctx.random(s) + ctx.random()) / s; r = (ctx.random(s) + ythickness) / s }
            2 -> { aa = (ctx.random(s) + xthickness) / s; r = (ctx.random(s) + ctx.random()) / s }
            3 -> { aa = ctx.random(); r = (ctx.random(s) + ythickness + ctx.random() * (1 - ythickness)) / s }
            4 -> { aa = (ctx.random(s) + xthickness + ctx.random() * (1 - xthickness)) / s; r = ctx.random() }
        }
        v.x += vcosr * aa + vsinr * r
        v.y += -vsinr * aa + vcosr * r
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { slices = (r.nextInt(10) + 2).toDouble(); xthickness = r.nextDouble(); ythickness = r.nextDouble(); rotation = r.nextDouble() * M_PI }
}

class LazyTravis : Variation("lazyTravis") {
    private var spinIn by param("spin_in", 1.0)
    private var spinOut by param("spin_out", 0.5)
    private var space by param("space", M_PI / 2)
    private var sIn = 4.0; private var sOut = 2.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { sIn = 4.0 * spinIn; sOut = 4.0 * spinOut }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x = abs(a.x); val y = abs(a.y)
        val s: Double; var p: Double
        if (x > amount || y > amount) {
            if (x > y) { s = x; p = if (a.x > 0.0) s + a.y + s * sOut else 5.0 * s - a.y + s * sOut }
            else { s = y; p = if (a.y > 0.0) 3.0 * s - a.x + s * sOut else 7.0 * s + a.x + s * sOut }
            p = fmod(p, s * 8.0)
            var x2: Double; var y2: Double
            if (p <= 2.0 * s) { x2 = s + space; y2 = -(1.0 * s - p); y2 += y2 / s * space }
            else if (p <= 4.0 * s) { y2 = s + space; x2 = 3.0 * s - p; x2 += x2 / s * space }
            else if (p <= 6.0 * s) { x2 = -(s + space); y2 = 5.0 * s - p; y2 += y2 / s * space }
            else { y2 = -(s + space); x2 = -(7.0 * s - p); x2 += x2 / s * space }
            v.x += amount * x2; v.y += amount * y2
        } else {
            if (x > y) { s = x; p = if (a.x > 0.0) s + a.y + s * sIn else 5.0 * s - a.y + s * sIn }
            else { s = y; p = if (a.y > 0.0) 3.0 * s - a.x + s * sIn else 7.0 * s + a.x + s * sIn }
            p = fmod(p, s * 8.0)
            when {
                p <= 2.0 * s -> { v.x += amount * s; v.y -= amount * (s - p) }
                p <= 4.0 * s -> { v.x += amount * (3.0 * s - p); v.y += amount * s }
                p <= 6.0 * s -> { v.x -= amount * s; v.y += amount * (5.0 * s - p) }
                else -> { v.x -= amount * (7.0 * s - p); v.y -= amount * s }
            }
        }
        z(ctx, a, v, amount)
    }
}

class Trade : Variation("trade") {
    private var r1 by param("r1", 1.0); private var d1 by param("d1", 1.0)
    private var r2 by param("r2", 1.0); private var d2 by param("d2", 1.0)
    private var c1 = 2.0; private var c2 = 2.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { c1 = r1 + d1; c2 = r2 + d2 }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (a.x > 0.0) {
            var r = sqrt(sqr(c1 - a.x) + sqr(a.y))
            if (r <= r1) {
                r *= r2 / r1
                val an = atan2(a.y, c1 - a.x)
                v.x += amount * (r * cos(an) - c2); v.y += amount * r * sin(an)
            } else { v.x += amount * a.x; v.y += amount * a.y }
        } else {
            var r = sqrt(sqr(-c2 - a.x) + sqr(a.y))
            if (r <= r2) {
                r *= r1 / r2
                val an = atan2(a.y, -c2 - a.x)
                v.x += amount * (r * cos(an) + c1); v.y += amount * r * sin(an)
            } else { v.x += amount * a.x; v.y += amount * a.y }
        }
        z(ctx, a, v, amount)
    }
}

class Eclipse : Variation("eclipse") {
    private var shift by param("shift", 0.1)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (abs(a.y) <= amount) {
            val c2 = sqrt(sqr(amount) - sqr(a.y))
            if (abs(a.x) <= c2) {
                val x = a.x + shift * amount
                if (abs(x) >= c2) v.x -= amount * a.x else v.x += amount * x
            } else v.x += amount * a.x
            v.y += amount * a.y
        } else { v.x += amount * a.x; v.y += amount * a.y }
        z(ctx, a, v, amount)
    }
}

class FlipCircle : Variation("flipcircle") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (sqr(a.x) + sqr(a.y) > sqr(amount)) v.y += amount * a.y else v.y -= amount * a.y
        v.x += amount * a.x
        z(ctx, a, v, amount)
    }
}

class FlipY : Variation("flipy") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (a.x > 0.0) v.y -= amount * a.y else v.y += amount * a.y
        v.x += amount * a.x
        z(ctx, a, v, amount)
    }
}

class Bent2 : Variation("bent2") {
    private var bx by param("x", 1.0)
    private var bendY by param("y", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var nx = a.x; var ny = a.y
        if (nx < 0.0) nx *= bx
        if (ny < 0.0) ny *= bendY
        v.x += amount * nx; v.y += amount * ny
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { bx = r.nextDouble() * 4 - 2; bendY = r.nextDouble() * 4 - 2 }
}

class Chunk : Variation("chunk") {
    private var ca by param("a", 1.0); private var cb by param("b", 0.0); private var cc by param("c", 1.0)
    private var cd by param("d", 0.0); private var ce by param("e", 0.0); private var cf by param("f", -1.0)
    private var mode by intParam("mode", 0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = amount * ca * sqr(a.x) + amount * cb * a.x * a.y + amount * cc * sqr(a.y) + amount * cd * a.x + amount * ce * a.y + amount * cf
        when (mode.toInt()) {
            0 -> if (r <= 0) { v.x += a.x; v.y += a.y }
            1 -> if (r > 0) { v.x += a.x; v.y += a.y }
        }
        z(ctx, a, v, amount)
    }
}

class Corners : Variation("corners") {
    private var cx by param("x", 1.0); private var cy by param("y", 1.0)
    private var multX by param("mult_x", 1.0); private var multY by param("mult_y", 1.0)
    private var xPower by param("x_power", 0.75); private var yPower by param("y_power", 0.75)
    private var xyPowerAdd by param("xy_power_add", 0.0)
    private var logMode by param("log_mode", 0.0); private var logBase by param("log_base", 2.71828)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val xs = a.x * a.x; val ys = a.y * a.y
        val ex: Double; val ey: Double
        if (logMode == 0.0) {
            ex = xs.pow(xPower + xyPowerAdd) * multX
            ey = ys.pow(yPower + xyPowerAdd) * multY
        } else {
            ex = (ln(xs * multX + 3) / ln(logBase)).pow(xPower + 2.25 + xyPowerAdd) - 1.33
            ey = (ln(ys * multY + 3) / ln(logBase)).pow(yPower + 2.25 + xyPowerAdd) - 1.33
        }
        v.x += if (a.x > 0) amount * ex + cx else amount * -ex - cx
        v.y += if (a.y > 0) amount * ey + cy else amount * -ey - cy
        z(ctx, a, v, amount)
    }
}

class Kaleidoscope : Variation("kaleidoscope") {
    private var pull by param("pull", 0.0)
    private var rotate by param("rotate", 1.0)
    private var lineUp by param("line_up", 1.0)
    private var kx by param("x", 0.0)
    private var ky by param("y", 0.0)
    // JWildfire uses cos(45)/sin(45) with the argument in radians; kept for identical output
    private val c45 = Math.cos(45.0); private val s45 = Math.sin(45.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += (rotate * a.x) * c45 - a.y * s45 + lineUp + kx
        v.y += if (a.y > 0) (rotate * a.y) * c45 + a.x * s45 + pull + lineUp + ky
        else (rotate * a.y) * c45 + a.x * s45 - pull - lineUp
        z(ctx, a, v, amount)
    }
}

class GDOffs : Variation("gdoffs") {
    private var deltaX by param("delta_x", 0.0); private var deltaY by param("delta_y", 0.0)
    private var areaX by param("area_x", 2.0); private var areaY by param("area_y", 2.0)
    private var centerX by param("center_x", 0.0); private var centerY by param("center_y", 0.0)
    private var gamma by intParam("gamma", 1); private var square by intParam("square", 0)
    private var gdodx = 0.0; private var gdody = 0.0; private var gdocx = 0.0; private var gdocy = 0.0; private var gdob = 0.0; private var gdos = 0
    private fun fclp(x: Double) = if (x < 0) -fmod(abs(x), 1.0) else fmod(abs(x), 1.0)
    private fun fscl(x: Double) = fclp((x + 1) / 2)
    private fun fosc(p: Double, x: Double) = fscl(-1 * cos(p * x * M_2PI))
    private fun flip(x: Double, b: Double, c: Double) = c * (b - x) + x
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        gdodx = deltaX * 0.1; gdody = deltaY * 0.1
        val gdoax = (if (abs(areaX) < 0.1) 0.1 else abs(areaX)) * 2.0
        val gdoay = (if (abs(areaY) < 0.1) 0.1 else abs(areaY)) * 2.0
        gdocx = centerX; gdocy = centerY
        gdos = square.toInt()
        gdob = gamma * 2.0 / max(gdoax, gdoay)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val oscX = fosc(gdodx, 1.0); val oscY = fosc(gdody, 1.0)
        val inX = a.x + gdocx; val inY = a.y + gdocy
        val outX = flip(flip(inX, fosc(inX, 4.0), oscX), fosc(fclp(gdob * inX), 4.0), oscX)
        val oy = if (gdos != 0) oscX else oscY
        val outY = flip(flip(inY, fosc(inY, 4.0), oy), fosc(fclp(gdob * inY), 4.0), oy)
        v.x += amount * outX; v.y += amount * outY
        z(ctx, a, v, amount)
    }
}

class Shift : Variation("shift") {
    private var shiftX by param("shift_x", 0.1)
    private var shiftY by param("shift_y", 0.06)
    private var angle by param("angle", 12.25)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = angle / 180 * M_PI
        val sn = sin(ang); val cs = cos(ang)
        v.x += amount * (a.x + cs * shiftX - sn * shiftY)
        v.y += amount * (a.y - cs * shiftY - sn * shiftX)
        z(ctx, a, v, amount)
    }
}

class Shredded : Variation("shredded") {
    private var x1 by param("x1", 1.0); private var x2 by param("x2", 3.0); private var x3 by param("x3", 1.0); private var xBlur by param("x_blur_amount", 0.1)
    private var y1 by param("y1", 1.0); private var y2 by param("y2", 3.0); private var y3 by param("y3", 1.0); private var yBlur by param("y_blur_amount", 0.1)
    private var z1 by param("z1", 1.0); private var z2 by param("z2", 3.0); private var z3 by param("z3", 1.0); private var zBlur by param("z_blur_amount", 0.1)
    private var type by intParam("type", 0)
    private var blur by intParam("blur", 0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = type.toInt()
        if (blur != 0.0) {
            val blX = xBlur * sin(M_2PI * ctx.random()); val blY = yBlur * sin(M_2PI * ctx.random()); val blZ = zBlur * sin(M_2PI * ctx.random())
            when (t) {
                2 -> { v.x += amount * x3 * sin(x1 * floor(x2 * a.x)) + blX * a.x; v.y += amount * y3 * cos(y1 * floor(y2 * a.y)) + blY * a.y; v.z += amount * z3 * sin(z1 * floor(z2 * a.z)) + blZ }
                1 -> { v.x += amount * x3 * sin(x1 * floor(x2 * a.x)) + blX * a.y; v.y += amount * y3 * cos(y1 * floor(y2 * a.y)) + blY * a.x; v.z += amount * z3 * sin(z1 * floor(z2 * a.z)) + blZ }
                0 -> {
                    v.x += amount * x3 * (sin(x1 * floor(x2 * a.x)) + cos(x1 * floor(x2 * a.y))) + blX * a.y
                    v.y += amount * y3 * (cos(y1 * floor(y2 * a.y)) + sin(y1 * floor(y2 * a.x))) + blY * a.x
                    v.z += amount * z3 * (sin(z1 * floor(z2 * a.z)) + cos(z1 * floor(z2 * a.z))) + blZ * a.z
                }
            }
        } else {
            when (t) {
                2 -> { v.x += amount * x3 * sin(x1 * floor(x2 * a.x)) * a.x; v.y += amount * y3 * cos(y1 * floor(y2 * a.y)) * a.y; v.z += amount * z3 * sin(z1 * floor(z2 * a.z)) * a.z }
                1 -> { v.x += amount * x3 * sin(x1 * floor(x2 * a.x)) * a.y; v.y += amount * y3 * cos(y1 * floor(y2 * a.y)) * a.x; v.z += amount * z3 * sin(z1 * floor(z2 * a.z)) * a.z }
                0 -> {
                    v.x += amount * x3 * (sin(x1 * floor(x2 * a.x)) + cos(x1 * floor(x2 * a.y))) * a.y
                    v.y += amount * y3 * (cos(y1 * floor(y2 * a.y)) + sin(y1 * floor(y2 * a.x))) * a.x
                    v.z += amount * z3 * (sin(z1 * floor(z2 * a.z)) + cos(z1 * floor(z2 * a.z))) * a.z
                }
            }
        }
    }
}

// ---------------------------------------------------------------- waves & distortions

class Popcorn2 : Variation("popcorn2") {
    private var px by param("x", 1.0)
    private var py by param("y", 0.5)
    private var pc by param("c", 1.5)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (a.x + px * sin(tan(a.y * pc)))
        v.y += amount * (a.y + py * sin(tan(a.x * pc)))
        z(ctx, a, v, amount)
    }
}

class Waves3 : Variation("waves3") {
    private var scalex by param("scalex", 0.05); private var scaley by param("scaley", 0.05)
    private var freqx by param("freqx", 7.0); private var freqy by param("freqy", 13.0)
    private var sxFreq by param("sx_freq", 0.0); private var syFreq by param("sy_freq", 2.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x0 = a.x; val y0 = a.y
        val sxx = 0.5 * scalex * (1.0 + sin(y0 * sxFreq))
        val syy = 0.5 * scaley * (1.0 + sin(x0 * syFreq))
        v.x += amount * (x0 + sin(y0 * freqx) * sxx)
        v.y += amount * (y0 + sin(x0 * freqy) * syy)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { scalex = r.nextDouble() * 0.3; scaley = r.nextDouble() * 0.3; freqx = r.nextDouble() * 20; freqy = r.nextDouble() * 20 }
}

class Waves4 : Variation("waves4") {
    private var scalex by param("scalex", 0.05); private var scaley by param("scaley", 0.05)
    private var freqx by param("freqx", 7.0); private var freqy by param("freqy", 13.0)
    private var cont by intParam("cont", 0); private var yfact by param("yfact", 0.1)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x0 = a.x; val y0 = a.y
        var ax = floor(y0 * freqx / M_2PI)
        ax = Math.sin(ax * 12.9898 + ax * 78.233 + 1.0 + y0 * 0.001 * yfact) * 43758.5453
        ax -= ax.toInt()
        if (cont == 1.0) ax = if (ax > 0.5) 1.0 else 0.0
        v.x += amount * (x0 + sin(y0 * freqx) * ax * ax * scalex)
        v.y += amount * (y0 + sin(x0 * freqy) * scaley)
        z(ctx, a, v, amount)
    }
}

class Waves42 : Variation("waves42") {
    private var scalex by param("scalex", 0.05); private var scaley by param("scaley", 0.05)
    private var freqx by param("freqx", 7.0); private var freqy by param("freqy", 13.0)
    private var cont by intParam("cont", 0); private var yfact by param("yfact", 0.1); private var freqx2 by param("freqx2", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x0 = a.x; val y0 = a.y
        var ax = floor(y0 * freqx2)
        ax = Math.sin(ax * 12.9898 + ax * 78.233 + 1.0 + y0 * 0.001 * yfact) * 43758.5453
        ax -= ax.toInt()
        if (cont == 1.0) ax = if (ax > 0.5) 1.0 else 0.0
        v.x += amount * (x0 + sin(y0 * freqx) * ax * ax * scalex)
        v.y += amount * (y0 + sin(x0 * freqy) * scaley)
        z(ctx, a, v, amount)
    }
}

class Waves22 : Variation("waves22") {
    private var scalex by param("scalex", 0.05); private var scaley by param("scaley", 0.05)
    private var freqx by param("freqx", 7.0); private var freqy by param("freqy", 13.0)
    private var modex by intParam("modex", 0); private var modey by intParam("modey", 0)
    private var powerx by param("powerx", 2.0); private var powery by param("powery", 2.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x0 = a.x; val y0 = a.y
        val px = powerx.toInt(); val py = powery.toInt()
        val sinx = if (modex < 0.5) sin(y0 * freqx) else 0.5 * (1.0 + sin(y0 * freqx))
        val siny = if (modey < 0.5) sin(x0 * freqy) else 0.5 * (1.0 + sin(x0 * freqy))
        v.x += amount * (x0 + sinx.pow(px) * scalex)
        v.y += amount * (y0 + siny.pow(py) * scaley)
        z(ctx, a, v, amount)
    }
}

class Waves23 : Variation("waves23") {
    private var scalex by param("scalex", 0.05); private var scaley by param("scaley", 0.05)
    private var freqx by param("freqx", 7.0); private var freqy by param("freqy", 13.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x0 = a.x; val y0 = a.y
        val mx = y0 * freqx * M_1_2PI
        var fx = mx - floor(mx); if (fx > 0.5) fx = 0.5 - fx
        val my = x0 * freqy * M_1_2PI
        var fy = my - floor(my); if (fy > 0.5) fy = 0.5 - fy
        v.x += amount * (x0 + fx * scalex)
        v.y += amount * (y0 + fy * scaley)
        z(ctx, a, v, amount)
    }
}

class Pulse : Variation("pulse") {
    private var freqx by param("freqx", 2.0); private var freqy by param("freqy", 2.0)
    private var scalex by param("scalex", 1.0); private var scaley by param("scaley", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (a.x + scalex * sin(a.x * freqx))
        v.y += amount * (a.y + scaley * sin(a.y * freqy))
        z(ctx, a, v, amount)
    }
}

class SinusGrid : Variation("sinusgrid") {
    private var ampx by param("ampx", 0.5); private var ampy by param("ampy", 0.6)
    private var freqx by param("freqx", 1.2); private var freqy by param("freqy", 1.0)
    private var fx = 1.0; private var fy = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        fx = freqx * M_2PI; fy = freqy * M_2PI
        if (fx == 0.0) fx = EPSILON
        if (fy == 0.0) fy = EPSILON
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val sx = -1.0 * cos(a.x * fx)
        val sy = -1.0 * cos(a.y * fy)
        v.x += amount * (a.x + ampx * (sx - a.x))
        v.y += amount * (a.y + ampy * (sy - a.y))
        v.z += amount * a.z
    }
}

class StWin : Variation("stwin") {
    private var distort by param("distort", 1.0)
    private var offsetXY by param("offset_xy", 0.0)
    private var offsetX2 by param("offset_x2", 0.0)
    private var offsetY2 by param("offset_y2", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x = a.x * amount * 0.05; val y = a.y * amount * 0.05
        val x2 = x * x + offsetX2 * 0.0001
        val y2 = y * y + offsetY2 * 0.0001
        var result = (x2 - y2) * sin(M_2PI * distort * (x + y + offsetXY * 0.1))
        var div = x2 + y2
        if (div == 0.0) div = 1.0
        result /= div
        v.x += amount * a.x + result
        v.y += amount * a.y + result
        z(ctx, a, v, amount)
    }
}

class TanCos : Variation("tancos") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d1 = SMALL_EPSILON + sqr(a.x) + sqr(a.y)
        val d2 = amount / d1
        v.x += d2 * (tanh(d1) * (2.0 * a.x))
        v.y += d2 * (cos(d1) * (2.0 * a.y))
        z(ctx, a, v, amount)
    }
}

class Rippled : Variation("rippled") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = sqr(a.x) + sqr(a.y)
        v.x += amount / 2 * (tanh(d + SMALL_EPSILON) * (2.0 * a.x))
        v.y += amount / 2 * (cos(d + SMALL_EPSILON) * (2.0 * a.y))
        z(ctx, a, v, amount)
    }
}

class Funnel : Variation("funnel") {
    private var effect by intParam("effect", 8)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * tanh(a.x) * (1.0 / cos(a.x) + effect * M_PI)
        v.y += amount * tanh(a.y) * (1.0 / cos(a.y) + effect * M_PI)
        z(ctx, a, v, amount)
    }
}

class Oscilloscope : Variation("oscilloscope") {
    private var separation by param("separation", 1.0)
    private var frequency by param("frequency", M_PI)
    private var amplitude by param("amplitude", 1.0)
    private var damping by param("damping", 0.0)
    private var tpf = 0.0; private var noDamping = true
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { tpf = 2.0 * M_PI * frequency; noDamping = abs(damping) <= EPSILON }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = if (noDamping) amplitude * cos(tpf * a.x) + separation
        else amplitude * exp(-abs(a.x) * damping) * cos(tpf * a.x) + separation
        v.x += amount * a.x
        if (abs(a.y) <= t) v.y -= amount * a.y else v.y += amount * a.y
        z(ctx, a, v, amount)
    }
}

class MCarpet : Variation("mcarpet") {
    private var mx by param("x", 1.0); private var my by param("y", 0.75)
    private var twist by param("twist", 0.5); private var tilt by param("tilt", -0.25)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = (sqr(a.x) + sqr(a.y)) / 4.0 + 1.0
        val r = amount / t
        v.x += a.x * r * mx
        v.y += a.y * r * my
        v.x += (1.0 - twist * sqr(a.x) + a.y) * amount
        v.y += tilt * a.x * amount
        z(ctx, a, v, amount)
    }
}

class Cylinder2 : Variation("cylinder2") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * a.x / sqrt(sqr(a.x) + 1.0)
        v.y += amount * a.y
        z(ctx, a, v, amount)
    }
}

class SVF : Variation("svf") {
    private var n by param("n", 2.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val cn = cos(n * a.y)
        val cy = cos(a.y)
        v.x += amount * (cy * (cn * cos(a.x)))
        v.y += amount * (cy * (cn * sin(a.x)))
        v.z += amount * (sin(a.y) * cn)
    }
}

class Rays1 : Variation("rays1") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = sqr(a.x) + sqr(a.y)
        val u = 1.0 / tan(sqrt(t)) + amount * sqr(M_2_PI)
        v.x = amount * u * t / a.x
        v.y = amount * u * t / a.y
    }
}

class Rays2 : Variation("rays2") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = sqr(a.x) + sqr(a.y)
        val u = 1.0 / cos((t + SMALL_EPSILON) * tan(1.0 / t + SMALL_EPSILON))
        v.x = (amount / 10.0) * u * t / a.x
        v.y = (amount / 10.0) * u * t / a.y
    }
}

class Rays3 : Variation("rays3") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = sqr(a.x) + sqr(a.y)
        val u = 1.0 / sqrt(cos(sin(sqr(t) + SMALL_EPSILON) * sin(1.0 / sqr(t) + SMALL_EPSILON)))
        v.x = (amount / 10.0) * u * cos(t) * t / a.x
        v.y = (amount / 10.0) * u * tan(t) * t / a.y
    }
}

// ---------------------------------------------------------------- blurs & random scatter

class BlurCircle : Variation("blur_circle") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x = 2.0 * ctx.random() - 1.0
        val y = 2.0 * ctx.random() - 1.0
        val absx = abs(x); val absy = abs(y)
        val perimeter: Double; val side: Double
        if (absx >= absy) { perimeter = if (x >= absy) absx + y else 5.0 * absx - y; side = absx }
        else { perimeter = if (y >= absx) 3.0 * absy - x else 7.0 * absy + x; side = absy }
        val r = amount * side
        val an = M_PI_4 * perimeter / side - M_PI_4
        v.x += r * cos(an); v.y += r * sin(an)
        z(ctx, a, v, amount)
    }
}

class BlurZoom : Variation("blur_zoom") {
    private var length by param("length", 0.24)
    private var bx by param("x", 0.2)
    private var zoomY by param("y", -0.1)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val zz = 1.0 + length * ctx.random()
        v.x += amount * ((a.x - bx) * zz + bx)
        v.y += amount * ((a.y + zoomY) * zz - zoomY)
        z(ctx, a, v, amount)
    }
}

class BlurLinear : Variation("blur_linear") {
    private var length by param("length", 1.0)
    private var angle by param("angle", 0.5)
    private var s = 0.0; private var c = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { s = sin(angle); c = cos(angle) }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = length * ctx.random()
        v.x += amount * (a.x + r * c)
        v.y += amount * (a.y + r * s)
        z(ctx, a, v, amount)
    }
}

class BlurPixelize : Variation("blur_pixelize") {
    private var size by param("size", 0.1)
    private var scale by param("scale", 1.0)
    private var invSize = 10.0; private var vv = 0.1
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { invSize = 1.0 / size; vv = amount * size }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x = floor(a.x * invSize); val y = floor(a.y * invSize)
        v.x += vv * (x + scale * (ctx.random() - 0.5) + 0.5)
        v.y += vv * (y + scale * (ctx.random() - 0.5) + 0.5)
        z(ctx, a, v, amount)
    }
}

class CircleBlur : Variation("circleblur") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rad = sqrt(ctx.random())
        val an = ctx.random() * M_2PI
        v.x += amount * cos(an) * rad; v.y += amount * sin(an) * rad
        z(ctx, a, v, amount)
    }
}

class SineBlur : Variation("sineblur") {
    private var power by param("power", 1.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val p = max(0.0, power)
        val ang = ctx.random() * M_2PI
        val r = amount * (if (p == 1.0) acos(ctx.random() * 2.0 - 1.0) / M_PI
        else acos(exp(ln(ctx.random()) * p) * 2.0 - 1.0) / M_PI)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
}

class Vogel : Variation("vogel") {
    private var n by intParam("n", 20)
    private var scale by param("scale", 1.0)
    private val m2PiPhi2 = M_2PI / (1.61803398874989484820 * 1.61803398874989484820)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val i = ctx.random(max(1, n.toInt())) + 1
        val an = i * m2PiPhi2
        val r = amount * (a.precalcSqrt + sqrt(i.toDouble()))
        v.x += r * (cos(an) + scale * a.x)
        v.y += r * (sin(an) + scale * a.y)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { n = (r.nextInt(80) + 5).toDouble(); scale = r.nextDouble() }
}

class GlynnSim1 : Variation("glynnSim1") {
    private var radius by param("radius", 1.0); private var radius1 by param("radius1", 0.1)
    private var phi1 by param("phi1", 110.0); private var thickness by param("thickness", 0.1)
    private var pw by param("pow", 1.5); private var contrast by param("contrast", 0.5)
    private var x1 = 0.0; private var y1 = 0.0; private var absPow = 1.5
    private var tx = 0.0; private var ty = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        val an = M_PI * phi1 / 180.0
        x1 = radius * cos(an); y1 = radius * sin(an); absPow = abs(pw)
    }
    private fun circle(ctx: Ctx) {
        val r = radius1 * (thickness + (1.0 - thickness) * ctx.random())
        val phi = 2.0 * M_PI * ctx.random()
        tx = r * cos(phi) + x1; ty = r * sin(phi) + y1
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = sqrt(a.x * a.x + a.y * a.y)
        val alpha = radius / r
        if (r < radius) {
            circle(ctx)
        } else {
            if (ctx.random() > contrast * alpha.pow(absPow)) { tx = a.x; ty = a.y }
            else { tx = alpha * alpha * a.x; ty = alpha * alpha * a.y }
            if (sqr(tx - x1) + sqr(ty - y1) < radius1 * radius1) circle(ctx)
        }
        v.x += amount * tx; v.y += amount * ty
        z(ctx, a, v, amount)
    }
}

class GlynnSim2 : Variation("glynnSim2") {
    private var radius by param("radius", 1.0); private var thickness by param("thickness", 0.1)
    private var contrast by param("contrast", 0.5); private var pw by param("pow", 1.5)
    private var phi1 by param("phi1", 110.0); private var phi2 by param("phi2", 150.0)
    private var phi10 = 0.0; private var gamma = 0.0; private var delta = 0.0; private var absPow = 1.5
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        phi10 = M_PI * phi1 / 180.0
        val phi20 = M_PI * phi2 / 180.0
        gamma = thickness * (2.0 * radius + thickness) / (radius + thickness)
        delta = phi20 - phi10
        absPow = abs(pw)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = sqrt(a.x * a.x + a.y * a.y)
        val alpha = radius / r
        if (r < radius) {
            val rr = radius + thickness - gamma * ctx.random()
            val phi = phi10 + delta * ctx.random()
            v.x += amount * rr * cos(phi); v.y += amount * rr * sin(phi)
        } else if (ctx.random() > contrast * alpha.pow(absPow)) {
            v.x += amount * a.x; v.y += amount * a.y
        } else {
            v.x += amount * alpha * alpha * a.x; v.y += amount * alpha * alpha * a.y
        }
        z(ctx, a, v, amount)
    }
}

class GlynnSim3 : Variation("glynnSim3") {
    private var radius by param("radius", 1.0); private var thickness by param("thickness", 0.1)
    private var contrast by param("contrast", 0.5); private var pw by param("pow", 1.5)
    private var r1 = 1.1; private var r2 = 0.9; private var gamma = 0.5; private var absPow = 1.5
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        r1 = radius + thickness
        r2 = sqr(radius) / r1
        gamma = r1 / (r1 + r2)
        absPow = abs(pw)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = sqrt(a.x * a.x + a.y * a.y)
        val alpha = radius / r
        if (r < r1) {
            val phi = 2.0 * M_PI * ctx.random()
            val rr = if (ctx.random() < gamma) r1 else r2
            v.x += amount * rr * cos(phi); v.y += amount * rr * sin(phi)
        } else if (ctx.random() > contrast * alpha.pow(absPow)) {
            v.x += amount * a.x; v.y += amount * a.y
        } else {
            v.x += amount * alpha * alpha * a.x; v.y += amount * alpha * alpha * a.y
        }
        z(ctx, a, v, amount)
    }
}

/** All batch-2 constructors, registered by [Variations]. */
internal val batch2Variations: List<() -> Variation> = listOf(
    ::Mobius, ::EDisc, ::EJulia, ::EPush, ::ERotate, ::EScale, ::CPow, ::CPow2, ::CPow3, ::Rational3,
    ::Sec, ::Csc, ::Cot, ::Sech, ::Csch, ::Coth, ::Atan, ::Sigmoid,
    ::PhoenixJulia, ::JuliaC, ::JuliaN2, ::NPolar, ::Murl, ::Murl2, ::Hypershift, ::Hypershift2, ::Hypertile1, ::Hypertile2,
    ::Cardioid, ::CannabisCurve, ::CloverLeaf, ::RoseWF, ::Lissajous, ::Spirograph, ::Epispiral, ::Petal, ::XHeart,
    ::Squarize, ::Squish, ::Circlize2, ::Loonie2, ::Scry2, ::Asteria,
    ::RoundSpher, ::TwoFace, ::WedgeSph, ::WDisc, ::Hole, ::Target, ::Circus, ::LayeredSpiral, ::Panorama1, ::Panorama2,
    ::Bipolar2, ::Ortho, ::DeltaA, ::Fourth,
    ::Boarders, ::Boarders2, ::Modulus, ::Intersection, ::TileLog, ::Waffle, ::LazyTravis, ::Trade, ::Eclipse,
    ::FlipCircle, ::FlipY, ::Bent2, ::Chunk, ::Corners, ::Kaleidoscope, ::GDOffs, ::Shift, ::Shredded,
    ::Popcorn2, ::Waves3, ::Waves4, ::Waves42, ::Waves22, ::Waves23, ::Pulse, ::SinusGrid, ::StWin, ::TanCos, ::Rippled,
    ::Funnel, ::Oscilloscope, ::MCarpet, ::Cylinder2, ::SVF, ::Rays1, ::Rays2, ::Rays3,
    ::BlurCircle, ::BlurZoom, ::BlurLinear, ::BlurPixelize, ::CircleBlur, ::SineBlur, ::Vogel,
    ::GlynnSim1, ::GlynnSim2, ::GlynnSim3,
)

/** Batch-2 variations that make good main shapes in random flames. */
internal val batch2RandomFriendly = listOf(
    "mobius", "edisc", "eJulia", "ePush", "eRotate", "eScale", "cpow", "cpow2", "rational3", "sec", "csc", "cot",
    "sech", "csch", "coth", "phoenix_julia", "juliac", "julian2", "npolar", "murl", "murl2", "hypershift",
    "hypertile1", "hypertile2", "cardioid", "rose_wf", "epispiral", "petal", "xheart", "squarize", "squish",
    "circlize2", "loonie2", "scry2", "roundspher", "twoface", "wedge_sph", "wdisc", "hole", "target", "circus",
    "layered_spiral", "panorama1", "panorama2", "bipolar2", "ortho", "deltaA", "fourth", "boarders", "boarders2",
    "modulus", "lazyTravis", "trade", "eclipse", "flipcircle", "flipy", "bent2", "popcorn2", "waves3", "waves22",
    "pulse", "sinusgrid", "stwin", "tancos", "rippled", "oscilloscope", "mcarpet", "cylinder2", "glynnSim1",
    "glynnSim2", "glynnSim3", "vogel", "spirograph", "lissajous"
)
