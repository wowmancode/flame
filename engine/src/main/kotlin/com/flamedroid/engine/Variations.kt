/*
 * FlameDroid variations.
 *
 * Every variation below is a line-by-line port of the corresponding
 * org.jwildfire.create.tina.variation.*Func class from JWildfire
 * (https://github.com/thargor6/JWildfire), (c) Andreas Maschke and contributors,
 * licensed under the GNU LGPL v2.1 or later. Names and parameter names match
 * JWildfire so that .flame files are interchangeable.
 */
package com.flamedroid.engine

import kotlin.math.*
import com.flamedroid.engine.FastMath.atan2
import com.flamedroid.engine.FastMath.cos
import com.flamedroid.engine.FastMath.sin

// ---------------------------------------------------------------- classic flam3 set

class Linear : Variation("linear") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * a.x; v.y += amount * a.y; z(ctx, a, v, amount)
    }
}

class Sinusoidal : Variation("sinusoidal") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * sin(a.x); v.y += amount * sin(a.y); z(ctx, a, v, amount)
    }
}

class Spherical : Variation("spherical") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = amount / (a.x * a.x + a.y * a.y + SMALL_EPSILON)
        v.x += a.x * r; v.y += a.y * r; z(ctx, a, v, amount)
    }
}

class Swirl : Variation("swirl") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r2 = a.x * a.x + a.y * a.y
        val c1 = sin(r2); val c2 = cos(r2)
        v.x += amount * (c1 * a.x - c2 * a.y)
        v.y += amount * (c2 * a.x + c1 * a.y)
        z(ctx, a, v, amount)
    }
}

class Horseshoe : Variation("horseshoe") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val sinA = a.precalcSinA; val cosA = a.precalcCosA
        v.x += amount * (sinA * a.x - cosA * a.y)
        v.y += amount * (cosA * a.x + sinA * a.y)
        z(ctx, a, v, amount)
    }
}

class Polar : Variation("polar") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ny = sqrt(a.x * a.x + a.y * a.y) - 1.0
        v.x += amount * (a.precalcAtan * R_PI)
        v.y += amount * ny
        z(ctx, a, v, amount)
    }
}

class Handkerchief : Variation("handkerchief") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val angle = a.precalcAtan
        val r = sqrt(a.x * a.x + a.y * a.y)
        v.x += amount * (sin(angle + r) * r)
        v.y += amount * (cos(angle - r) * r)
        z(ctx, a, v, amount)
    }
}

class Heart : Variation("heart") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = sqrt(a.x * a.x + a.y * a.y)
        val angle = a.precalcAtan
        val sinr = sin(r * angle); val cosr = cos(r * angle)
        r *= amount
        v.x += r * sinr; v.y -= r * cosr
        z(ctx, a, v, amount)
    }
}

class Disc : Variation("disc") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rPI = M_PI * sqrt(a.x * a.x + a.y * a.y)
        val sinr = sin(rPI); val cosr = cos(rPI)
        val r = amount * a.precalcAtan / M_PI
        v.x += sinr * r; v.y += cosr * r
        z(ctx, a, v, amount)
    }
}

class Spiral : Variation("spiral") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val sinA = a.precalcSinA; val cosA = a.precalcCosA
        var r = a.precalcSqrt
        val sinr = sin(r); val cosr = cos(r)
        r = amount / r
        v.x += (cosA + sinr) * r; v.y += (sinA - cosr) * r
        z(ctx, a, v, amount)
    }
}

class Hyperbolic : Variation("hyperbolic") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = a.precalcSqrt
        v.x += amount * a.precalcSinA / r
        v.y += amount * a.precalcCosA * r
        z(ctx, a, v, amount)
    }
}

class Diamond : Variation("diamond") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val length = a.precalcSqrt
        val sinr = sin(length); val cosr = cos(length)
        v.x += amount * a.precalcSinA * cosr
        v.y += amount * a.precalcCosA * sinr
        z(ctx, a, v, amount)
    }
}

class Ex : Variation("ex") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = sqrt(a.x * a.x + a.y * a.y)
        val n0 = sin(a.precalcAtan + r); val n1 = cos(a.precalcAtan - r)
        val m0 = n0 * n0 * n0; val m1 = n1 * n1 * n1
        r *= amount
        v.x += r * (m0 + m1); v.y += r * (m0 - m1)
        z(ctx, a, v, amount)
    }
}

class Julia : Variation("julia") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = a.precalcAtan * 0.5 + M_PI * (2.0 * ctx.random()).toInt()
        val r = amount * sqrt(sqrt(a.x * a.x + a.y * a.y))
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
}

class Bent : Variation("bent") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var nx = a.x; var ny = a.y
        if (nx < 0) nx += nx
        if (ny < 0) ny *= 0.5
        v.x += amount * nx; v.y += amount * ny
        z(ctx, a, v, amount)
    }
}

class Waves : Variation("waves") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (a.x + xf.c10 * sin(a.y / (xf.c20 * xf.c20 + SMALL_EPSILON)))
        v.y += amount * (a.y + xf.c11 * sin(a.x / (xf.c21 * xf.c21 + SMALL_EPSILON)))
        z(ctx, a, v, amount)
    }
}

class Fisheye : Variation("fisheye") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = sqrt(a.x * a.x + a.y * a.y)
        r = 2.0 * r / (r + 1.0)
        v.x += amount * r * a.precalcCosA
        v.y += amount * r * a.precalcSinA
        z(ctx, a, v, amount)
    }
}

class Popcorn : Variation("popcorn") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var dx = tan(3 * a.y); if (dx.isNaN()) dx = 0.0
        var dy = tan(3 * a.x); if (dy.isNaN()) dy = 0.0
        v.x += amount * (a.x + xf.c20 * sin(dx))
        v.y += amount * (a.y + xf.c21 * sin(dy))
        z(ctx, a, v, amount)
    }
}

class Exponential : Variation("exponential") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = M_PI * a.y
        val d = amount * exp(a.x - 1)
        v.x += cos(r) * d; v.y += sin(r) * d
        z(ctx, a, v, amount)
    }
}

class Power : Variation("power") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = amount * a.precalcSqrt.pow(a.precalcSinA)
        v.x += r * a.precalcCosA; v.y += r * a.precalcSinA
        z(ctx, a, v, amount)
    }
}

class Cosine : Variation("cosine") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = a.x * M_PI
        v.x += amount * cos(r) * cosh(a.y)
        v.y -= amount * sin(r) * sinh(a.y)
        z(ctx, a, v, amount)
    }
}

class Rings : Variation("rings") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val dx = xf.c20 * xf.c20 + SMALL_EPSILON
        var r = a.precalcSqrt
        r = r + dx - ((r + dx) / (2 * dx)).toInt() * 2 * dx - dx + r * (1 - dx)
        v.x += r * a.precalcCosA; v.y += r * a.precalcSinA
        z(ctx, a, v, amount)
    }
}

class Fan : Variation("fan") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val dx = M_PI * xf.c20 * xf.c20 + SMALL_EPSILON
        val dx2 = dx / 2
        val at = a.precalcAtan
        val ang = if ((at + xf.c21 - ((at + xf.c21) / dx).toInt() * dx) > dx2) at - dx2 else at + dx2
        val r = amount * sqrt(a.x * a.x + a.y * a.y)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
}

class Blob : Variation("blob") {
    private var low by param("low", 0.3)
    private var high by param("high", 1.2)
    private var waves by param("waves", 6.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = atan2(a.x, a.y)
        var r = sqrt(a.x * a.x + a.y * a.y)
        r *= (low + (high - low) * (0.5 + 0.5 * sin(waves * ang)))
        v.x += amount * sin(ang) * r; v.y += amount * cos(ang) * r
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        if (r.nextDouble() < 0.75) { low = r.nextDouble() * r.nextDouble(); high = low + r.nextDouble() }
        else { low = r.nextDouble() * 2 - 1; high = low + r.nextDouble() * 2 }
        waves = if (r.nextDouble() < 0.5) r.nextDouble() * 6 + 3 else r.nextDouble() * 30 + 1
    }
}

class PDJ : Variation("pdj") {
    private var pa by param("a", 1.0)
    private var pb by param("b", 2.0)
    private var pc by param("c", 3.0)
    private var pd by param("d", 4.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (sin(pa * a.y) - cos(pb * a.x))
        v.y += amount * (sin(pc * a.x) - cos(pd * a.y))
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        pa = r.nextDouble() * 6 - 3; pb = r.nextDouble() * 6 - 3; pc = r.nextDouble() * 6 - 3; pd = r.nextDouble() * 6 - 3
    }
}

class Fan2 : Variation("fan2") {
    private var px by param("x", 0.5)
    private var py by param("y", 1.2)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = sqrt(a.x * a.x + a.y * a.y)
        val angle = if (a.x < -SMALL_EPSILON || a.x > SMALL_EPSILON || a.y < -SMALL_EPSILON || a.y > SMALL_EPSILON) atan2(a.x, a.y) else 0.0
        val dy = py
        val dx = M_PI * (px * px) + SMALL_EPSILON
        val dx2 = dx * 0.5
        val t = angle + dy - ((angle + dy) / dx).toInt() * dx
        val ang = if (t > dx2) angle - dx2 else angle + dx2
        v.x += amount * r * sin(ang); v.y += amount * r * cos(ang)
        z(ctx, a, v, amount)
    }
}

class Rings2 : Variation("rings2") {
    private var value by param("val", 0.01)
    private var dx = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { dx = value * value + EPSILON }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val l = a.precalcSqrt
        if (dx == 0.0 || l == 0.0) return
        val r = amount * (2.0 - dx * (((l / dx + 1) / 2).toInt() * 2 / l + 1))
        v.x += r * a.x; v.y += r * a.y
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { value = r.nextDouble() * 1.2 }
}

class Eyefish : Variation("eyefish") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = 2 * amount / (sqrt(a.x * a.x + a.y * a.y) + 1.0)
        v.x += r * a.x; v.y += r * a.y
        z(ctx, a, v, amount)
    }
}

class Bubble : Variation("bubble") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = (a.x * a.x + a.y * a.y) / 4.0 + 1.0
        val t = amount / r
        v.x += t * a.x; v.y += t * a.y
        v.z += amount * (2.0 / r - 1.0)
    }
}

class Cylinder : Variation("cylinder") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * sin(a.x); v.y += amount * a.y
        z(ctx, a, v, amount)
    }
}

class Perspective : Variation("perspective") {
    private var angle by param("angle", 0.62)
    private var dist by param("dist", 2.2)
    private var vsin = 0.0; private var vfcos = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        val ang = angle * M_PI / 2.0
        vsin = sin(ang); vfcos = dist * cos(ang)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = dist - a.y * vsin
        if (d == 0.0) return
        val t = 1.0 / d
        v.x += amount * dist * a.x * t
        v.y += amount * vfcos * a.y * t
        z(ctx, a, v, amount)
    }
}

class Noise : Variation("noise") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = ctx.random() * 2.0 * M_PI
        val sinr = sin(r); val cosr = cos(r)
        r = amount * ctx.random()
        v.x += a.x * r * cosr; v.y += a.y * r * sinr
        z(ctx, a, v, amount)
    }
}

class JuliaN : Variation("julian") {
    private var power by intParam("power", 3)
    private var dist by param("dist", 1.0)
    private var absPower = 1; private var cPower = 0.0; private var pw = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (power == 0.0) power = 1.0
        absPower = abs(power.toInt()); cPower = dist / power * 0.5; pw = power
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = (atan2(a.y, a.x) + 2 * M_PI * ctx.random(absPower)) / pw
        val r = amount * (sqr(a.x) + sqr(a.y)).pow(cPower)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        power = (r.nextInt(10) + 2).toDouble() * (if (r.nextBoolean()) -1 else 1)
        val x = r.nextDouble()
        dist = when { x < 0.4 -> r.nextDouble() * 0.5 + 0.75; x < 0.8 -> r.nextDouble() * 3.3 + 0.2; else -> 1.0 }
        if (r.nextDouble() < 0.4) dist = -dist
    }
}

class JuliaScope : Variation("juliascope") {
    private var power by intParam("power", 3)
    private var dist by param("dist", 1.0)
    private var absPower = 1; private var cPower = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (power == 0.0) power = 1.0
        absPower = abs(power.toInt()); cPower = dist / power * 0.5
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rnd = ctx.random(absPower)
        val ang = if (rnd and 1 == 0) (2 * M_PI * rnd + atan2(a.y, a.x)) / power
        else (2 * M_PI * rnd - atan2(a.y, a.x)) / power
        val r = amount * (sqr(a.x) + sqr(a.y)).pow(cPower)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        power = (r.nextInt(10) + 2).toDouble() * (if (r.nextBoolean()) -1 else 1)
        dist = r.nextDouble() * 3 + 0.2
    }
}

class Blur : Variation("blur") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = ctx.random() * (M_PI + M_PI)
        val r2 = amount * ctx.random()
        v.x += r2 * cos(r); v.y += r2 * sin(r)
        z(ctx, a, v, amount)
    }
}

class GaussianBlur : Variation("gaussian_blur") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = ctx.random() * 2 * M_PI
        val sina = sin(r); val cosa = cos(r)
        r = amount * (ctx.random() + ctx.random() + ctx.random() + ctx.random() - 2.0)
        v.x += r * cosa; v.y += r * sina
        z(ctx, a, v, amount)
    }
}

class RadialBlur : Variation("radial_blur") {
    private var angle by param("angle", 0.5)
    private val gauss = DoubleArray(4)
    private var gaussN = 0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        for (i in 0..3) gauss[i] = ctx.random()
        gaussN = 0
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rndG = gauss[0] + gauss[1] + gauss[2] + gauss[3] - 2
        gauss[gaussN] = ctx.random()
        gaussN = (gaussN + 1) and 3
        val spin = amount * sin(angle * M_PI * 0.5)
        val zoom = amount * cos(angle * M_PI * 0.5)
        val ra = sqrt(a.x * a.x + a.y * a.y)
        val alpha = atan2(a.y, a.x) + spin * rndG
        val rz = zoom * rndG - 1
        v.x += ra * cos(alpha) + rz * a.x
        v.y += ra * sin(alpha) + rz * a.y
        z(ctx, a, v, amount)
    }
}

class Pie : Variation("pie") {
    private var slices by param("slices", 6.0)
    private var rotation by param("rotation", 0.0)
    private var thickness by param("thickness", 0.5)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val sl = (ctx.random() * slices + 0.5).toInt()
        val ang = rotation + 2.0 * M_PI * (sl + ctx.random() * thickness) / slices
        val r = amount * ctx.random()
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        slices = (r.nextInt(10) + 2).toDouble(); rotation = r.nextDouble() * M_2PI; thickness = r.nextDouble()
    }
}

class Ngon : Variation("ngon") {
    private var circle by param("circle", 1.0)
    private var corners by param("corners", 2.0)
    private var power by param("power", 3.0)
    private var sides by param("sides", 5.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val rFactor = a.precalcSumsq.pow(power / 2.0)
        val theta = a.precalcAtanYX
        val b = 2 * M_PI / sides
        var phi = theta - b * floor(theta / b)
        if (phi > b / 2) phi -= b
        var amp = corners * (1.0 / (cos(phi) + SMALL_EPSILON) - 1.0) + circle
        amp /= (rFactor + SMALL_EPSILON)
        v.x += amount * a.x * amp; v.y += amount * a.y * amp
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        circle = r.nextDouble() * 2; corners = r.nextDouble() * 4; power = r.nextDouble() * 6 - 1; sides = (r.nextInt(10) + 3).toDouble()
    }
}

class Curl : Variation("curl") {
    private var c1 by param("c1", 0.1)
    private var c2 by param("c2", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val re = 1 + c1 * a.x + c2 * (sqr(a.x) - sqr(a.y))
        val im = c1 * a.y + c2 * 2 * a.x * a.y
        val r = amount / (sqr(re) + sqr(im))
        v.x += (a.x * re + a.y * im) * r
        v.y += (a.y * re - a.x * im) * r
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { c1 = r.nextDouble() - 0.5; c2 = r.nextDouble() - 0.5 }
}

class Rectangles : Variation("rectangles") {
    private var px by param("x", 0.3)
    private var py by param("y", 0.2)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += if (abs(px) < EPSILON) amount * a.x else amount * ((2 * floor(a.x / px) + 1) * px - a.x)
        v.y += if (abs(py) < EPSILON) amount * a.y else amount * ((2 * floor(a.y / py) + 1) * py - a.y)
        z(ctx, a, v, amount)
    }
}

class Arch : Variation("arch") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = ctx.random() * amount * M_PI
        val sinr = sin(ang); val cosr = cos(ang)
        if (cosr == 0.0) return
        v.x += amount * sinr
        v.y += amount * (sinr * sinr) / cosr
        z(ctx, a, v, amount)
    }
}

class Tangent : Variation("tangent") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cos(a.y)
        if (d == 0.0) return
        v.x += amount * sin(a.x) / d
        v.y += amount * tan(a.y)
        z(ctx, a, v, amount)
    }
}

class Square : Variation("square") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (ctx.random() - 0.5); v.y += amount * (ctx.random() - 0.5)
        z(ctx, a, v, amount)
    }
}

class Rays : Variation("rays") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = amount * ctx.random() * M_PI
        val r = amount / (a.precalcSumsq + SMALL_EPSILON)
        val tanr = amount * tan(ang) * r
        v.x += tanr * cos(a.x); v.y += tanr * sin(a.y)
        z(ctx, a, v, amount)
    }
}

class Blade : Variation("blade") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = ctx.random() * amount * sqrt(a.x * a.x + a.y * a.y)
        val sinr = sin(r); val cosr = cos(r)
        v.x += amount * a.x * (cosr + sinr)
        v.y += amount * a.x * (cosr - sinr)
        z(ctx, a, v, amount)
    }
}

class Secant2 : Variation("secant2") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = amount * a.precalcSqrt
        val cr = cos(r)
        if (cr == 0.0) return
        val icr = 1.0 / cr
        v.x += amount * a.x
        v.y += if (cr < 0) amount * (icr + 1) else amount * (icr - 1)
        z(ctx, a, v, amount)
    }
}

class Twintrian : Variation("twintrian") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = ctx.random() * amount * a.precalcSqrt
        val sinr = sin(r); val cosr = cos(r)
        var diff = log10(sinr * sinr) + cosr
        if (abs(diff) < SMALL_EPSILON || diff.isInfinite()) diff = -30.0
        v.x += amount * a.x * diff
        v.y += amount * a.x * (diff - sinr * M_PI)
        z(ctx, a, v, amount)
    }
}

class Cross : Variation("cross") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val s = a.x * a.x - a.y * a.y
        val r = amount * sqrt(1.0 / (s * s + SMALL_EPSILON))
        v.x += a.x * r; v.y += a.y * r
        z(ctx, a, v, amount)
    }
}

class Disc2 : Variation("disc2") {
    private var rot by param("rot", 2.0)
    private var twist by param("twist", 0.5)
    private var timespi = 0.0; private var sinadd = 0.0; private var cosadd = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        val add = twist
        timespi = rot * M_PI
        sinadd = sin(add); cosadd = cos(add) - 1
        if (add > 2.0 * M_PI) { val k = 1 + add - 2.0 * M_PI; cosadd *= k; sinadd *= k }
        if (add < -2.0 * M_PI) { val k = 1 + add + 2.0 * M_PI; cosadd *= k; sinadd *= k }
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = timespi * (a.x + a.y)
        val r = amount * a.precalcAtan / M_PI
        v.x += (sin(t) + cosadd) * r
        v.y += (cos(t) + sinadd) * r
        z(ctx, a, v, amount)
    }
}

class SuperShape : Variation("super_shape") {
    private var rnd by param("rnd", 3.0)
    private var m by param("m", 1.0)
    private var n1 by param("n1", 1.0)
    private var n2 by param("n2", 1.0)
    private var n3 by param("n3", 1.0)
    private var holes by param("holes", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val pm4 = m / 4.0
        val pneg1n1 = -1.0 / n1
        val theta = pm4 * a.precalcAtanYX + M_PI_4
        val t1 = abs(cos(theta)).pow(n2)
        val t2 = abs(sin(theta)).pow(n3)
        val r = amount * ((rnd * ctx.random() + (1.0 - rnd) * a.precalcSqrt) - holes) *
                (t1 + t2).pow(pneg1n1) / a.precalcSqrt
        v.x += r * a.x; v.y += r * a.y
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        rnd = r.nextDouble(); m = (r.nextInt(12) + 1).toDouble(); n1 = r.nextDouble() * 4 + 0.2
        n2 = r.nextDouble() * 4 + 0.2; n3 = r.nextDouble() * 4 + 0.2; holes = r.nextDouble() * 0.4
    }
}

class Flower : Variation("flower") {
    private var holes by param("holes", 0.4)
    private var petals by param("petals", 7.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val theta = a.precalcAtanYX
        val d = a.precalcSqrt
        if (d == 0.0) return
        val r = amount * (ctx.random() - holes) * cos(petals * theta) / d
        v.x += r * a.x; v.y += r * a.y
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { holes = r.nextDouble() * 0.8; petals = (r.nextInt(12) + 2).toDouble() }
}

class Conic : Variation("conic") {
    private var eccentricity by param("eccentricity", 1.0)
    private var holes by param("holes", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ct = a.x / a.precalcSqrt
        val r = amount * (ctx.random() - holes) * eccentricity / (1 + eccentricity * ct) / a.precalcSqrt
        v.x += r * a.x; v.y += r * a.y
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { eccentricity = r.nextDouble() * 1.5; holes = r.nextDouble() * 0.5 }
}

class Parabola : Variation("parabola") {
    private var width by param("width", 1.0)
    private var height by param("height", 0.5)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = a.precalcSqrt
        val sr = sin(r); val cr = cos(r)
        v.x += height * amount * sr * sr * ctx.random()
        v.y += width * amount * cr * ctx.random()
        z(ctx, a, v, amount)
    }
}

class Bipolar : Variation("bipolar") {
    private var shift by param("shift", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val x2y2 = a.x * a.x + a.y * a.y
        val t = x2y2 + 1.0
        val x2 = 2 * a.x
        val ps = -M_PI_2 * shift
        var y = 0.5 * atan2(2.0 * a.y, x2y2 - 1.0) + ps
        if (y > M_PI_2) y = -M_PI_2 + fmod(y + M_PI_2, M_PI)
        else if (y < -M_PI_2) y = M_PI_2 - fmod(M_PI_2 - y, M_PI)
        val f = t + x2; val g = t - x2
        if (g == 0.0 || f / g <= 0) return
        v.x += amount * 0.25 * M_2_PI * ln((t + x2) / (t - x2))
        v.y += amount * M_2_PI * y
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { shift = r.nextDouble() * 2 - 1 }
}

class Butterfly : Variation("butterfly") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val wx = amount * 1.3029400317411197908970256609023
        val y2 = a.y * 2.0
        val r = wx * sqrt(abs(a.y * a.x) / (SMALL_EPSILON + a.x * a.x + y2 * y2))
        v.x += r * a.x; v.y += r * y2
        z(ctx, a, v, amount)
    }
}

class Cell : Variation("cell") {
    private var size by param("size", 0.6)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val inv = 1.0 / size
        var x = floor(a.x * inv).toInt()
        var y = floor(a.y * inv).toInt()
        val dx = a.x - x * size
        val dy = a.y - y * size
        if (y >= 0) {
            if (x >= 0) { y *= 2; x *= 2 } else { y *= 2; x = -(2 * x + 1) }
        } else {
            if (x >= 0) { y = -(2 * y + 1); x *= 2 } else { y = -(2 * y + 1); x = -(2 * x + 1) }
        }
        v.x += amount * (dx + x * size)
        v.y -= amount * (dy + y * size)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { size = r.nextDouble() * 1.5 + 0.1 }
}

class Escher : Variation("escher") {
    private var beta by param("beta", 0.3)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val ang = a.precalcAtanYX
        val lnr = 0.5 * ln(a.precalcSumsq)
        val seb = sin(beta); val ceb = cos(beta)
        val vc = 0.5 * (1.0 + ceb); val vd = 0.5 * seb
        val m = amount * exp(vc * lnr - vd * ang)
        val n = vc * ang + vd * lnr
        v.x += m * cos(n); v.y += m * sin(n)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { beta = r.nextDouble() * M_2PI - M_PI }
}

class Loonie : Variation("loonie") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r2 = a.precalcSumsq
        val w2 = amount * amount
        if (r2 < w2 && r2 != 0.0) {
            val r = amount * sqrt(w2 / r2 - 1.0)
            v.x += r * a.x; v.y += r * a.y
        } else {
            v.x += amount * a.x; v.y += amount * a.y
        }
        z(ctx, a, v, amount)
    }
}

class Polar2 : Variation("polar2") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val p2v = amount / M_PI
        v.x += p2v * a.precalcAtan
        v.y += p2v / 2.0 * ln(a.precalcSumsq)
        z(ctx, a, v, amount)
    }
}

class Scry : Variation("scry") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = a.x * a.x + a.y * a.y
        val d = sqrt(t) * (t + 1.0 / amount)
        if (d == 0.0) return
        val r = 1.0 / d
        v.x += a.x * r; v.y += a.y * r
        z(ctx, a, v, amount)
    }
}

class Split : Variation("split") {
    private var xsize by param("xsize", 0.4)
    private var ysize by param("ysize", 0.6)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (cos(a.x * xsize * M_PI) >= 0) v.y += amount * a.y else v.y -= amount * a.y
        if (cos(a.y * ysize * M_PI) >= 0) v.x += amount * a.x else v.x -= amount * a.x
        z(ctx, a, v, amount)
    }
}

class Stripes : Variation("stripes") {
    private var space by param("space", 0.2)
    private var warp by param("warp", 0.6)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val roundx = floor(a.x + 0.5)
        val offsetx = a.x - roundx
        v.x += amount * (offsetx * (1.0 - space) + roundx)
        v.y += amount * (a.y + offsetx * offsetx * warp)
        z(ctx, a, v, amount)
    }
}

class Wedge : Variation("wedge") {
    private var angle by param("angle", M_PI_2)
    private var hole by param("hole", 0.0)
    private var count by intParam("count", 1)
    private var swirl by param("swirl", 0.1)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = a.precalcSqrt
        var ang = a.precalcAtanYX + swirl * r
        val c = floor((count * ang + M_PI) * M_1_PI * 0.5)
        val compFac = 1 - angle * count * M_1_PI * 0.5
        ang = ang * compFac + c * angle
        r = amount * (r + hole)
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        angle = r.nextDouble() * M_PI; hole = r.nextDouble() * 0.5; count = (r.nextInt(6) + 1).toDouble(); swirl = r.nextDouble() * 0.6
    }
}

class Whorl : Variation("whorl") {
    private var inside by param("inside", 0.1)
    private var outside by param("outside", 0.2)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = a.precalcSqrt
        val ang = if (r < amount) a.precalcAtanYX + inside / (amount - r) else a.precalcAtanYX + outside / (amount - r)
        v.x += amount * r * cos(ang); v.y += amount * r * sin(ang)
        z(ctx, a, v, amount)
    }
}

class Waves2 : Variation("waves2") {
    private var scalex by param("scalex", 0.25)
    private var scaley by param("scaley", 0.5)
    private var freqx by param("freqx", M_PI / 2)
    private var freqy by param("freqy", M_PI / 4)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (a.x + scalex * sin(a.y * freqx))
        v.y += amount * (a.y + scaley * sin(a.x * freqy))
        z(ctx, a, v, amount)
    }
}

// ---------------------------------------------------------------- complex functions

class ExpV : Variation("exp") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val e = exp(a.x)
        v.x += amount * e * cos(a.y); v.y += amount * e * sin(a.y)
        z(ctx, a, v, amount)
    }
}

class LogV : Variation("log") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * 0.5 * ln(a.precalcSumsq)
        v.y += amount * a.precalcAtanYX
        z(ctx, a, v, amount)
    }
}

class SinV : Variation("sin") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * sin(a.x) * cosh(a.y)
        v.y += amount * cos(a.x) * sinh(a.y)
        z(ctx, a, v, amount)
    }
}

class CosV : Variation("cos") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * cos(a.x) * cosh(a.y)
        v.y -= amount * sin(a.x) * sinh(a.y)
        z(ctx, a, v, amount)
    }
}

class TanV : Variation("tan") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cos(2.0 * a.x) + cosh(2.0 * a.y)
        if (d == 0.0) return
        val den = 1.0 / d
        v.x += amount * den * sin(2.0 * a.x)
        v.y += amount * den * sinh(2.0 * a.y)
        z(ctx, a, v, amount)
    }
}

class SinhV : Variation("sinh") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * sinh(a.x) * cos(a.y)
        v.y += amount * cosh(a.x) * sin(a.y)
        z(ctx, a, v, amount)
    }
}

class CoshV : Variation("cosh") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * cosh(a.x) * cos(a.y)
        v.y += amount * sinh(a.x) * sin(a.y)
        z(ctx, a, v, amount)
    }
}

class TanhV : Variation("tanh") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val d = cos(2.0 * a.y) + cosh(2.0 * a.x)
        if (d == 0.0) return
        val den = 1.0 / d
        v.x += amount * den * sinh(2.0 * a.x)
        v.y += amount * den * sin(2.0 * a.y)
        z(ctx, a, v, amount)
    }
}

class Elliptic : Variation("elliptic") {
    private var mode by intParam("mode", 1) // 0 original, 1 mirror-y (JWildfire legacy), 2 precision
    private var vv = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { vv = amount * M_2_PI }
    private fun sqrtSafe(x: Double) = if (x < SMALL_EPSILON) 0.0 else sqrt(x)
    private fun sqrt1pm1(x: Double): Double {
        if (-0.0625 < x && x < 0.0625) {
            // [4,4] Padé approximant of sqrt(1+x)-1, same Horner steps as JWildfire
            val num = (((1.0 / 32.0) * x + 5.0 / 16.0) * x + 3.0 / 4.0) * x * x + 0.5 * x
            val den = ((((1.0 / 256.0) * x + 5.0 / 32.0) * x + 15.0 / 16.0) * x + 7.0 / 4.0) * x + 1.0
            return num / den
        }
        return sqrt(1 + x) - 1
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val m = mode.toInt()
        if (m == 2) {
            val sq = a.y * a.y + a.x * a.x
            val x2 = 2.0 * a.x
            val xmaxm1 = 0.5 * (sqrt1pm1(sq + x2) + sqrt1pm1(sq - x2))
            val ssx = if (xmaxm1 < 0) 0.0 else sqrt(xmaxm1)
            val aa = a.x / (1 + xmaxm1)
            val sign = if (a.y > 0) 1 else -1
            v.x += vv * asin(max(-1.0, min(1.0, aa)))
            v.y += sign * vv * ln1p(xmaxm1 + ssx)
        } else {
            val tmp = a.y * a.y + a.x * a.x + 1.0
            val x2 = 2.0 * a.x
            val xmax = 0.5 * (sqrt(tmp + x2) + sqrt(tmp - x2))
            val aa = a.x / xmax
            val b = sqrtSafe(1.0 - aa * aa)
            var sign = if (a.y > 0) 1 else -1
            if (m == 1) sign = if (ctx.random() < 0.5) 1 else -1
            v.x += vv * atan2(aa, b)
            v.y += sign * vv * ln(xmax + sqrtSafe(xmax - 1.0))
        }
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { mode = r.nextInt(3).toDouble() }
}

// ---------------------------------------------------------------- 3D-ish

class Hemisphere : Variation("hemisphere") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = amount / sqrt(a.x * a.x + a.y * a.y + 1)
        v.x += a.x * r; v.y += a.y * r; v.z += r
    }
}

class Bubble2 : Variation("bubble2") {
    private var px by param("x", 1.0)
    private var py by param("y", 1.0)
    private var pz by param("z", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val t = (sqr(a.x) + sqr(a.y) + sqr(a.z)) / 4.0 + 1.0
        val r = amount / t
        v.x += a.x * r * px
        v.y += a.y * r * py
        v.z += if (a.z >= 0.0) amount * (a.z + pz) else amount * (a.z - pz)
        v.z += a.z * r * pz
    }
}

class Spherical3D : Variation("spherical3D") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val r = amount / (a.x * a.x + a.y * a.y + a.z * a.z + SMALL_EPSILON)
        v.x += a.x * r; v.y += a.y * r; v.z += a.z * r
    }
}

// ---------------------------------------------------------------- Apophysis plugin set

class Auger : Variation("auger") {
    private var freq by param("freq", 1.0)
    private var weight by param("weight", 0.5)
    private var sym by param("sym", 0.1)
    private var scale by param("scale", 0.9)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val s = sin(freq * a.x); val t = sin(freq * a.y)
        val dy = a.y + weight * (scale * s * 0.5 + abs(a.y) * s)
        val dx = a.x + weight * (scale * t * 0.5 + abs(a.x) * t)
        v.x += amount * (a.x + sym * (dx - a.x))
        v.y += amount * dy
        z(ctx, a, v, amount)
    }
}

class Flux : Variation("flux") {
    private var spread by param("spread", 0.3)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val xpw = a.x + amount; val xmw = a.x - amount
        val avgr = amount * (2 + spread) * sqrt(sqrt(a.y * a.y + xpw * xpw) / sqrt(a.y * a.y + xmw * xmw))
        val avga = (atan2(a.y, xmw) - atan2(a.y, xpw)) * 0.5
        v.x += avgr * cos(avga); v.y += avgr * sin(avga)
        z(ctx, a, v, amount)
    }
}

class Splits : Variation("splits") {
    private var px by param("x", 0.4)
    private var py by param("y", 0.6)
    private var lshear by param("lshear", 0.0)
    private var rshear by param("rshear", 0.0)
    private var ushear by param("ushear", 0.0)
    private var dshear by param("dshear", 0.0)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        if (a.x >= 0) { v.x += amount * (a.x + px); v.y += amount * rshear }
        else { v.x += amount * (a.x - px); v.y -= amount * lshear }
        if (a.y >= 0) { v.y += amount * (a.y + py); v.x += amount * ushear }
        else { v.y += amount * (a.y - py); v.x -= amount * dshear }
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        px = r.nextDouble() - 0.5; py = r.nextDouble() - 0.5
        lshear = 0.0; rshear = 0.0; ushear = 0.0; dshear = 0.0
    }
}

class Bwraps : Variation("bwraps") {
    private var scale by param("scale", 0.5)
    private var cellscale by param("cellscale", 0.9)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val bs = max(abs(scale), 0.01)
        val cs = cellscale.coerceIn(0.1, 1.0)
        val cx = Math.round(a.x / bs) * bs
        val cy = Math.round(a.y / bs) * bs
        val dx = a.x - cx; val dy = a.y - cy
        val r2 = dx * dx + dy * dy + 1e-6
        val br2 = bs * bs * cs * cs * 0.25
        val inv = br2 / r2
        v.x += amount * (cx + dx * inv)
        v.y += amount * (cy + dy * inv)
        z(ctx, a, v, amount)
    }
}

class Collideoscope : Variation("collideoscope") {
    private var pa by param("a", 0.2)
    private var num by intParam("num", 1)
    private var knPi = 0.0; private var piKn = 0.0; private var kaKn = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        if (num == 0.0) num = 1.0
        knPi = num * M_1_PI; piKn = M_PI / num; kaKn = M_PI * pa / num
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var ang = atan2(a.y, a.x)
        val r = amount * sqrt(sqr(a.x) + sqr(a.y))
        if (ang >= 0.0) {
            val alt = (ang * knPi).toInt()
            ang = if (alt % 2 == 0) alt * piKn + fmod(kaKn + ang, piKn) else alt * piKn + fmod(-kaKn + ang, piKn)
        } else {
            val alt = (-ang * knPi).toInt()
            ang = if (alt % 2 != 0) -(alt * piKn + fmod(-kaKn - ang, piKn)) else -(alt * piKn + fmod(kaKn - ang, piKn))
        }
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) { pa = r.nextDouble(); num = (r.nextInt(8) + 1).toDouble() }
}

class Curve : Variation("curve") {
    private var xamp by param("xamp", 0.25)
    private var yamp by param("yamp", 0.5)
    private var xlength by param("xlength", 1.0)
    private var ylength by param("ylength", 1.0)
    private var pcx = 1.0; private var pcy = 1.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        pcx = max(xlength * xlength, 1e-20); pcy = max(ylength * ylength, 1e-20)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        v.x += amount * (a.x + xamp * exp(-a.y * a.y / pcx))
        v.y += amount * (a.y + yamp * exp(-a.x * a.x / pcy))
        z(ctx, a, v, amount)
    }
}

class Foci : Variation("foci") {
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val expx = exp(a.x) * 0.5
        val expnx = 0.25 / expx
        if (expx <= SMALL_EPSILON || expnx <= SMALL_EPSILON) return
        val siny = sin(a.y); val cosy = cos(a.y)
        var tmp = expx + expnx - cosy
        if (tmp == 0.0) return
        tmp = amount / tmp
        v.x += (expx - expnx) * tmp
        v.y += siny * tmp
        z(ctx, a, v, amount)
    }
}

class Glynnia : Variation("glynnia") {
    private var vvar2 = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { vvar2 = amount * sqrt(2.0) / 2.0 }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        var r = sqrt(sqr(a.x) + sqr(a.y))
        if (r >= 1.0) {
            if (ctx.random() > 0.5) {
                val d = sqrt(r + a.x); if (d == 0.0) return
                v.x += vvar2 * d; v.y -= vvar2 / d * a.y
            } else {
                val d = r + a.x
                val dx = sqrt(r * (sqr(a.y) + sqr(d))); if (dx == 0.0) return
                r = amount / dx
                v.x += r * d; v.y += r * a.y
            }
        } else {
            if (ctx.random() > 0.5) {
                val d = sqrt(r + a.x); if (d == 0.0) return
                v.x -= vvar2 * d; v.y -= vvar2 / d * a.y
            } else {
                val d = r + a.x
                val dx = sqrt(r * (sqr(a.y) + sqr(d))); if (dx == 0.0) return
                r = amount / dx
                v.x -= r * d; v.y += r * a.y
            }
        }
        z(ctx, a, v, amount)
    }
}

class Hypertile : Variation("hypertile") {
    private var p by intParam("p", 3)
    private var q by intParam("q", 7)
    private var n by intParam("n", 1)
    private var re = 0.0; private var im = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) {
        val pa = 2.0 * M_PI / p; val qa = 2.0 * M_PI / q
        var r = (1.0 - cos(pa)) / (cos(pa) + cos(qa)) + 1.0
        r = if (r > 0) 1.0 / sqrt(r) else 1.0
        val ang = n * pa
        re = r * cos(ang); im = r * sin(ang)
    }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val aa = a.x + re; val b = a.y - im
        val c = re * a.x - im * a.y + 1.0
        val d = re * a.y + im * a.x
        val vr = amount / (sqr(c) + sqr(d))
        v.x += vr * (aa * c + b * d)
        v.y += vr * (b * c - aa * d)
        z(ctx, a, v, amount)
    }
    override fun randomize(r: java.util.Random) {
        p = (r.nextInt(6) + 3).toDouble(); q = (r.nextInt(6) + 3).toDouble(); n = (r.nextInt(5) + 1).toDouble()
    }
}

class Checks : Variation("checks") {
    private var px by param("x", 3.0)
    private var py by param("y", 3.0)
    private var size by param("size", 1.0)
    private var rnd by param("rnd", 0.5)
    private var cs = 1.0; private var ncx = 0.0; private var ncy = 0.0
    override fun init(ctx: Ctx, xf: XForm, amount: Double) { cs = 1.0 / (size + EPSILON); ncx = -px; ncy = -py }
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val isXY = Math.rint(a.x * cs).toInt() + Math.rint(a.y * cs).toInt()
        val rnx = rnd * ctx.random(); val rny = rnd * ctx.random()
        val dx: Double; val dy: Double
        if (isXY % 2 == 0) { dx = ncx + rnx; dy = ncy } else { dx = px; dy = py + rny }
        v.x += amount * (a.x + dx); v.y += amount * (a.y + dy)
        z(ctx, a, v, amount)
    }
}

class Circlize : Variation("circlize") {
    private var hole by param("hole", 0.4)
    override fun transform(ctx: Ctx, xf: XForm, a: Pt, v: Pt, amount: Double) {
        val var4PI = amount / M_PI_4
        val absx = abs(a.x); val absy = abs(a.y)
        val perimeter: Double; val side: Double
        if (absx >= absy) {
            perimeter = if (a.x >= absy) absx + a.y else 5.0 * absx - a.y
            side = absx
        } else {
            perimeter = if (a.y >= absx) 3.0 * absy - a.x else 7.0 * absy + a.x
            side = absy
        }
        val r = var4PI * side + hole
        val ang = M_PI_4 * perimeter / side - M_PI_4
        v.x += r * cos(ang); v.y += r * sin(ang)
        z(ctx, a, v, amount)
    }
}

/** Registry of every available variation, keyed by JWildfire name. */
object Variations {
    private val factories: LinkedHashMap<String, () -> Variation> = linkedMapOf()

    private fun reg(f: () -> Variation) { factories[f().name] = f }

    init {
        listOf(
            ::Linear, ::Sinusoidal, ::Spherical, ::Swirl, ::Horseshoe, ::Polar, ::Handkerchief, ::Heart,
            ::Disc, ::Spiral, ::Hyperbolic, ::Diamond, ::Ex, ::Julia, ::Bent, ::Waves, ::Fisheye, ::Popcorn,
            ::Exponential, ::Power, ::Cosine, ::Rings, ::Fan, ::Blob, ::PDJ, ::Fan2, ::Rings2, ::Eyefish,
            ::Bubble, ::Cylinder, ::Perspective, ::Noise, ::JuliaN, ::JuliaScope, ::Blur, ::GaussianBlur,
            ::RadialBlur, ::Pie, ::Ngon, ::Curl, ::Rectangles, ::Arch, ::Tangent, ::Square, ::Rays, ::Blade,
            ::Secant2, ::Twintrian, ::Cross, ::Disc2, ::SuperShape, ::Flower, ::Conic, ::Parabola, ::Bipolar,
            ::Butterfly, ::Cell, ::Escher, ::Loonie, ::Polar2, ::Scry, ::Split, ::Stripes, ::Wedge, ::Whorl,
            ::Waves2, ::ExpV, ::LogV, ::SinV, ::CosV, ::TanV, ::SinhV, ::CoshV, ::TanhV, ::Elliptic,
            ::Hemisphere, ::Bubble2, ::Spherical3D, ::Auger, ::Flux, ::Splits, ::Bwraps, ::Collideoscope,
            ::Curve, ::Foci, ::Glynnia, ::Hypertile, ::Checks, ::Circlize
        ).forEach { reg(it) }
    }

    val names: List<String> get() = factories.keys.toList()

    fun exists(name: String) = factories.containsKey(name)

    fun create(name: String): Variation =
        (factories[name] ?: throw IllegalArgumentException("Unknown variation: $name")).invoke()

    /** Variations that look good as the "main" shape of a random flame. */
    val randomFriendly = listOf(
        "linear", "sinusoidal", "spherical", "swirl", "horseshoe", "polar", "handkerchief", "heart", "disc",
        "spiral", "hyperbolic", "diamond", "ex", "julia", "bent", "fisheye", "exponential", "power",
        "cosine", "blob", "pdj", "fan2", "rings2", "eyefish", "bubble", "cylinder", "perspective", "julian",
        "juliascope", "ngon", "curl", "rectangles", "arch", "tangent", "secant2", "cross", "disc2",
        "super_shape", "flower", "conic", "bipolar", "butterfly", "cell", "escher", "polar2", "scry", "split",
        "stripes", "wedge", "whorl", "waves2", "exp", "log", "sin", "cos", "tan", "sinh", "cosh", "tanh",
        "elliptic", "hemisphere", "auger", "flux", "splits", "bwraps", "collideoscope", "curve", "foci",
        "glynnia", "hypertile", "circlize", "loonie", "popcorn", "waves", "fan", "rings", "blade", "twintrian"
    ).filter { factories.containsKey(it) }
}
