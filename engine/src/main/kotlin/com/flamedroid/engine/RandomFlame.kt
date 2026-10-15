package com.flamedroid.engine

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Random flame generators, loosely modelled on JWildfire's built-in generators. */
object RandomFlame {

    enum class Style(val label: String) {
        MIXED("Mixed"), CLASSIC("Classic"), SYMMETRIC("Symmetric"), SPIRAL("Spiral"), JULIA("Julians"),
        BUBBLES("Bubbles"), GALAXY("Galaxy"), KALEIDO("Kaleidoscope"), TILES("Tiles"), CRYSTAL("Crystal")
    }

    fun generate(style: Style = Style.MIXED, width: Int = 800, height: Int = 600, seed: Long = System.nanoTime()): Flame {
        val rnd = Random(seed)
        var best: Flame? = null
        var bestScore = -1.0
        repeat(14) {
            val s = if (style == Style.MIXED) Style.values()[1 + rnd.nextInt(Style.values().size - 1)] else style
            val f = build(s, rnd)
            f.width = width; f.height = height
            if (!autoFit(f, rnd.nextLong())) return@repeat
            // quick quality probe
            val r = Renderer(f, 160, 120, threads = 1, seed = rnd.nextLong())
            r.addSamples(r.samplesFor(8.0))
            // fraction of pixels that are visibly lit (not just hit once)
            val px = r.toArgb()
            r.shutdown()
            val bgL = (f.bgR + f.bgG + f.bgB) / 765.0
            val cov = px.count { c -> Palettes.luma(c) - bgL > 0.12 }.toDouble() / px.size
            val score = if (cov < 0.03 || cov > 0.92) cov * 0.1 else 1.0 - abs(cov - 0.35)
            if (score > bestScore) { bestScore = score; best = f }
            if (score > 0.75) return f
        }
        return best ?: build(Style.CLASSIC, rnd).also { it.width = width; it.height = height; autoFit(it, 1) }
    }

    private fun pickVar(r: Random) = Variations.randomFriendly[r.nextInt(Variations.randomFriendly.size)]

    private fun randomAffine(x: XForm, r: Random, scaleMin: Double = 0.25, scaleMax: Double = 1.0) {
        x.resetAffine()
        val sx = scaleMin + r.nextDouble() * (scaleMax - scaleMin)
        val sy = if (r.nextDouble() < 0.6) sx else scaleMin + r.nextDouble() * (scaleMax - scaleMin)
        x.c00 = sx; x.c11 = sy
        if (r.nextDouble() < 0.3) x.c10 = (r.nextDouble() - 0.5) * 0.6
        if (r.nextDouble() < 0.3) x.c01 = (r.nextDouble() - 0.5) * 0.6
        x.rotate(r.nextDouble() * 360)
        x.c20 = (r.nextDouble() * 2 - 1) * 1.2
        x.c21 = (r.nextDouble() * 2 - 1) * 1.2
    }

    private fun addRandomVars(x: XForm, r: Random, max: Int) {
        val count = 1 + r.nextInt(max)
        for (i in 0 until count) {
            val name = pickVar(r)
            if (x.variations.any { it.variation.name == name }) continue
            val e = x.addVariation(name, if (i == 0) 0.5 + r.nextDouble() * 0.7 else r.nextDouble() * 0.5)
            if (r.nextDouble() < 0.7) e.variation.randomize(r)
        }
    }

    private fun build(style: Style, r: Random): Flame {
        val f = Flame()
        f.name = "${style.label} ${Integer.toHexString(r.nextInt(0xFFFFF))}"
        f.palette = if (r.nextDouble() < 0.6) Palettes.random(r) else Palettes.builtIn[r.nextInt(Palettes.builtIn.size)].colors.copyOf()
        f.brightness = 3.0 + r.nextDouble() * 2
        f.gamma = 3.0 + r.nextDouble() * 1.5
        f.vibrancy = 1.0
        when (style) {
            Style.CLASSIC, Style.MIXED -> {
                val n = 2 + r.nextInt(3)
                repeat(n) {
                    val x = XForm()
                    randomAffine(x, r)
                    addRandomVars(x, r, 2)
                    x.weight = 0.2 + r.nextDouble()
                    f.xforms += x
                }
            }
            Style.SYMMETRIC -> {
                val n = 1 + r.nextInt(3)
                repeat(n) {
                    val x = XForm(); randomAffine(x, r); addRandomVars(x, r, 2); x.weight = 0.3 + r.nextDouble(); f.xforms += x
                }
                val order = 2 + r.nextInt(7)
                for (k in 1 until order) {
                    val x = XForm()
                    x.rotate(360.0 / order * k)
                    x.addVariation("linear", 1.0)
                    x.weight = 0.6
                    x.colorSymmetry = 1.0
                    f.xforms += x
                }
                if (r.nextBoolean()) { // mirror
                    val m = XForm(); m.c00 = -1.0; m.addVariation("linear", 1.0); m.weight = 0.6; m.colorSymmetry = 1.0; f.xforms += m
                }
            }
            Style.SPIRAL -> {
                val main = XForm()
                main.c00 = 0.85 + r.nextDouble() * 0.12; main.c11 = main.c00
                main.rotate(10 + r.nextDouble() * 40)
                main.addVariation("linear", 1.0)
                main.weight = 4.0 + r.nextDouble() * 6
                f.xforms += main
                repeat(1 + r.nextInt(2)) {
                    val x = XForm(); randomAffine(x, r, 0.15, 0.6); addRandomVars(x, r, 2); x.weight = 0.4; f.xforms += x
                }
            }
            Style.JULIA -> {
                repeat(2 + r.nextInt(2)) {
                    val x = XForm(); randomAffine(x, r, 0.4, 1.1)
                    val e = x.addVariation(if (r.nextBoolean()) "julian" else "juliascope", 0.6 + r.nextDouble() * 0.5)
                    e.variation.randomize(r)
                    if (r.nextDouble() < 0.5) x.addVariation(pickVar(r), r.nextDouble() * 0.4).variation.randomize(r)
                    x.weight = 0.3 + r.nextDouble(); f.xforms += x
                }
            }
            Style.BUBBLES -> {
                repeat(2 + r.nextInt(3)) {
                    val x = XForm(); randomAffine(x, r, 0.3, 0.9)
                    x.addVariation(listOf("spherical", "bubble", "eyefish", "fisheye", "hemisphere", "loonie")[r.nextInt(6)], 0.5 + r.nextDouble() * 0.6)
                    if (r.nextDouble() < 0.6) x.addVariation(pickVar(r), r.nextDouble() * 0.3).variation.randomize(r)
                    x.weight = 0.3 + r.nextDouble(); f.xforms += x
                }
            }
            Style.GALAXY -> {
                // a strongly weighted rotating contraction makes the arms; satellites add detail
                val arms = XForm()
                val sc = 0.88 + r.nextDouble() * 0.09
                arms.c00 = sc; arms.c11 = sc
                arms.rotate(15 + r.nextDouble() * 35)
                arms.addVariation("linear", 1.0)
                if (r.nextBoolean()) arms.addVariation("swirl", 0.02 + r.nextDouble() * 0.05)
                arms.weight = 6.0 + r.nextDouble() * 8
                arms.colorSymmetry = 0.8
                f.xforms += arms
                repeat(1 + r.nextInt(2)) {
                    val x = XForm()
                    x.scale(0.15 + r.nextDouble() * 0.35)
                    x.move(0.6 + r.nextDouble() * 0.8, (r.nextDouble() - 0.5) * 0.4)
                    x.addVariation(listOf("linear", "julia", "spherical", "bubble", "disc", "eyefish")[r.nextInt(6)], 0.8)
                    if (r.nextDouble() < 0.6) x.addVariation(if (r.nextBoolean()) "gaussian_blur" else "blur", 0.01 + r.nextDouble() * 0.05)
                    x.weight = 0.5 + r.nextDouble()
                    f.xforms += x
                }
            }
            Style.KALEIDO -> {
                repeat(1 + r.nextInt(2)) {
                    val x = XForm(); randomAffine(x, r, 0.3, 0.9); addRandomVars(x, r, 2); x.weight = 0.5 + r.nextDouble(); f.xforms += x
                }
                val order = 5 + r.nextInt(8)
                for (k in 1 until order) {
                    val x = XForm()
                    x.rotate(360.0 / order * k)
                    x.addVariation("linear", 1.0)
                    x.weight = 0.4
                    x.colorSymmetry = 1.0
                    f.xforms += x
                }
                val m = XForm(); m.c00 = -1.0; m.addVariation("linear", 1.0); m.weight = 0.4; m.colorSymmetry = 1.0; f.xforms += m
                // final transforms that keep rotational symmetry (julian/polar would fold it away)
                if (r.nextDouble() < 0.3) {
                    val fin = XForm(); fin.colorSymmetry = 1.0
                    val n = listOf("spherical", "eyefish", "bubble", "fisheye", "swirl", "hemisphere")[r.nextInt(6)]
                    fin.addVariation(n, if (n == "swirl") 0.1 + r.nextDouble() * 0.3 else 0.7 + r.nextDouble() * 0.4)
                    f.finalXform = fin
                }
            }
            Style.TILES -> {
                val tilers = listOf("rectangles", "checks", "hypertile", "cell", "bwraps", "splits", "stripes", "collideoscope")
                repeat(2 + r.nextInt(2)) {
                    val x = XForm(); randomAffine(x, r, 0.4, 1.0)
                    x.addVariation(tilers[r.nextInt(tilers.size)], 0.5 + r.nextDouble() * 0.6).variation.randomize(r)
                    if (r.nextDouble() < 0.6) x.addVariation(pickVar(r), r.nextDouble() * 0.4).variation.randomize(r)
                    x.weight = 0.3 + r.nextDouble(); f.xforms += x
                }
            }
            Style.CRYSTAL -> {
                val crystals = listOf("ngon", "super_shape", "flower", "wedge", "circlize", "pie", "julian", "conic")
                repeat(2 + r.nextInt(2)) {
                    val x = XForm(); randomAffine(x, r, 0.3, 0.9)
                    val v = x.addVariation(crystals[r.nextInt(crystals.size)], 0.6 + r.nextDouble() * 0.5).variation
                    v.randomize(r)
                    v.setParam("holes", 0.0) // holes scatter points into fog
                    if (r.nextDouble() < 0.6) x.addVariation("linear", 0.2 + r.nextDouble() * 0.5)
                    x.weight = 0.3 + r.nextDouble(); f.xforms += x
                }
                if (r.nextBoolean()) {
                    val order = 3 + r.nextInt(6)
                    for (k in 1 until order) {
                        val x = XForm(); x.rotate(360.0 / order * k); x.addVariation("linear", 1.0)
                        x.weight = 0.5; x.colorSymmetry = 1.0; f.xforms += x
                    }
                }
            }
        }
        if (f.finalXform == null && r.nextDouble() < 0.3) {
            val fin = XForm()
            fin.colorSymmetry = 1.0
            fin.addVariation(listOf("spherical", "julian", "bubble", "eyefish", "polar2", "swirl", "disc", "hyperbolic")[r.nextInt(8)], 0.6 + r.nextDouble() * 0.5)
                .variation.randomize(r)
            f.finalXform = fin
        }
        if (r.nextDouble() < 0.25) f.bgR.let { val v = r.nextInt(30); f.bgR = v; f.bgG = v; f.bgB = v + r.nextInt(20) }
        f.distributeColors()
        f.xforms.forEach { it.colorSymmetry = if (it.colorSymmetry == 1.0) 1.0 else r.nextDouble() * 0.6 - 0.3 }
        return f
    }

    /**
     * Iterate the attractor and frame the camera on the central 96% of points.
     * Returns false if the system diverges or collapses to a point.
     */
    fun autoFit(f: Flame, seed: Long): Boolean {
        if (f.xforms.isEmpty()) return false
        val ctx = Ctx(seed)
        val fl = f.deepCopy()
        fl.xforms.forEach { it.init(ctx) }
        fl.finalXform?.init(ctx)
        val weights = fl.xforms.map { max(0.0, it.weight) }
        val total = weights.sum()
        if (total <= 0) return false
        val p = Pt(); val q = Pt(); val fp = Pt()
        p.x = ctx.random() * 2 - 1; p.y = ctx.random() * 2 - 1
        val n = 6000
        val xs = DoubleArray(n); val ys = DoubleArray(n)
        var k = 0
        var bad = 0
        var iter = 0
        while (k < n && iter < n * 4) {
            iter++
            var t = ctx.random() * total
            var idx = 0
            while (idx < weights.size - 1 && t >= weights[idx]) { t -= weights[idx]; idx++ }
            fl.xforms[idx].transform(ctx, p, q)
            if (q.x.isNaN() || q.y.isNaN() || abs(q.x) > 1e8 || abs(q.y) > 1e8) {
                bad++; p.x = ctx.random() * 2 - 1; p.y = ctx.random() * 2 - 1; p.invalidate(); continue
            }
            p.assign(q)
            if (iter < 30) continue
            val o = fl.finalXform?.let { it.transform(ctx, p, fp); fp } ?: p
            if (o.x.isNaN() || o.y.isNaN() || abs(o.x) > 1e8 || abs(o.y) > 1e8) continue
            xs[k] = o.x; ys[k] = o.y; k++
        }
        if (k < n / 2 || bad > n) return false
        val sx = xs.copyOf(k).also { it.sort() }
        val sy = ys.copyOf(k).also { it.sort() }
        val lo = (k * 0.02).toInt(); val hi = (k * 0.98).toInt().coerceAtMost(k - 1)
        val minX = sx[lo]; val maxX = sx[hi]; val minY = sy[lo]; val maxY = sy[hi]
        val w = maxX - minX; val h = maxY - minY
        if (w < 1e-6 || h < 1e-6 || w > 1e6 || h > 1e6) return false
        f.centerX = (minX + maxX) / 2
        f.centerY = (minY + maxY) / 2
        f.camZoom = 1.0
        f.rotate = 0.0
        f.pixelsPerUnit = min(f.width / w, f.height / h) * 0.92
        return true
    }

    /** Slightly perturb an existing flame (JWildfire's "mutation"). */
    fun mutate(src: Flame, seed: Long = System.nanoTime()): Flame {
        val r = Random(seed)
        val f = src.deepCopy()
        if (f.xforms.isEmpty()) return f
        when (r.nextInt(5)) {
            0 -> f.xforms[r.nextInt(f.xforms.size)].let { it.rotate((r.nextDouble() - 0.5) * 30); it.move((r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3) }
            1 -> f.xforms[r.nextInt(f.xforms.size)].let { x -> x.variations.randomOrNull(r)?.variation?.randomize(r) }
            2 -> f.xforms[r.nextInt(f.xforms.size)].let { x -> x.addVariation(pickVar(r), r.nextDouble() * 0.4).variation.randomize(r) }
            3 -> f.palette = Palettes.random(r)
            else -> f.xforms.forEach { x -> x.variations.forEach { it.amount *= 0.85 + r.nextDouble() * 0.3 } }
        }
        return f
    }

    private fun <T> List<T>.randomOrNull(r: Random) = if (isEmpty()) null else this[r.nextInt(size)]

    @Suppress("unused") private const val TAU = 2 * PI
}
