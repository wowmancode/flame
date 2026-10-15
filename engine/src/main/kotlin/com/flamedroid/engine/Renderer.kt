package com.flamedroid.engine

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One long-lived pool for all renders, so edits don't spin up new threads. */
object RenderPool {
    val cores: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
    val executor: ExecutorService = Executors.newFixedThreadPool(cores) { r ->
        Thread(r, "flame-render").apply { isDaemon = true; priority = Thread.MIN_PRIORITY + 1 }
    }
}

/**
 * Recycles histogram buffers between renders. Interactive previews create a renderer per
 * frame; without recycling that is megabytes of garbage per second, and the garbage
 * collector's work shows up as heat.
 */
object HistPool {
    private val free = HashMap<Int, ArrayList<FloatArray>>()
    private var held = 0L
    private const val MAX_HELD = 24L * 1024 * 1024 // bytes kept for reuse
    /** Only small (interactive-frame) buffers are pooled; big ones are allocated once per render. */
    private const val MAX_POOLED = 1_100_000

    @Synchronized fun get(size: Int): FloatArray {
        if (size > MAX_POOLED) return FloatArray(size)
        val list = free[size]
        if (list != null && list.isNotEmpty()) {
            val a = list.removeAt(list.size - 1)
            held -= a.size * 4L
            java.util.Arrays.fill(a, 0f)
            return a
        }
        return FloatArray(size)
    }

    @Synchronized fun put(a: FloatArray) {
        if (a.size > MAX_POOLED || held + a.size * 4L > MAX_HELD) return
        free.getOrPut(a.size) { ArrayList() }.add(a)
        held += a.size * 4L
    }

    @Synchronized fun clear() { free.clear(); held = 0 }
}

/**
 * Progressive chaos-game renderer with flam3/JWildfire style log-density tone mapping.
 * Call [addSamples] repeatedly from one background thread and [toArgb] to get the image.
 */
class Renderer(
    flame: Flame,
    val width: Int,
    val height: Int,
    threads: Int = RenderPool.cores,
    seed: Long = System.nanoTime()
) {
    private val flame = flame.deepCopy()
    private val nThreads = threads.coerceIn(1, RenderPool.cores)
    private val pixels = width * height

    /**
     * Each worker gets its own histogram when memory allows: threads writing to one shared
     * array fight over cache lines, which wastes most of the extra cores (and battery).
     */
    private val privateHist = pixels.toLong() * 16 * nThreads <= 96L * 1024 * 1024
    private val shared: FloatArray? = if (privateHist) null else FloatArray(pixels * 4)
    private var mergeBuf: FloatArray? = null
    @Volatile private var released = false
    @Volatile private var running = false

    @Volatile var totalSamples = 0L; private set
    private val cancelled = AtomicBoolean(false)
    private val workers = List(nThreads) { Worker(this.flame.deepCopy(), seed + it * 7919L) }

    // camera
    private val ppu = this.flame.pixelsPerUnit * this.flame.camZoom * (width.toDouble() / this.flame.width)
    private val camCos = cos(Math.toRadians(-this.flame.rotate))
    private val camSin = sin(Math.toRadians(-this.flame.rotate))
    private val halfW = width / 2.0
    private val halfH = height / 2.0
    // fold center + rotation + scale into one affine: px = ax*x + bx*y + cx ; py = ay*x + by*y + cy
    private val mAx = camCos * ppu
    private val mBx = -camSin * ppu
    private val mCx = halfW - (this.flame.centerX * camCos - this.flame.centerY * camSin) * ppu
    private val mAy = camSin * ppu
    private val mBy = camCos * ppu
    private val mCy = halfH - (this.flame.centerX * camSin + this.flame.centerY * camCos) * ppu
    private val palR = FloatArray(256) { (this.flame.palette[it] shr 16 and 255) / 255f }
    private val palG = FloatArray(256) { (this.flame.palette[it] shr 8 and 255) / 255f }
    private val palB = FloatArray(256) { (this.flame.palette[it] and 255) / 255f }

    fun cancel() = cancelled.set(true)
    val isCancelled get() = cancelled.get()

    /** Stops work and returns buffers for reuse. Call once, after the last addSamples/toArgb. */
    fun shutdown() {
        cancel()
        if (released || running) return // still iterating: let GC take the buffers instead
        released = true
        for (w in workers) w.hist?.let { HistPool.put(it) }
        mergeBuf?.let { HistPool.put(it) }
    }

    /** Iterate roughly [samples] more points across the workers. Returns false if cancelled. */
    fun addSamples(samples: Long): Boolean {
        if (cancelled.get() || released || flame.xforms.isEmpty() || samples <= 0) return !cancelled.get()
        running = true
        try { iterate(samples) } finally { running = false }
        return !cancelled.get()
    }

    private fun iterate(samples: Long) {
        val per = samples / workers.size + 1
        if (workers.size == 1) {
            totalSamples += workers[0].run(per)
        } else {
            val futures = workers.map { w -> RenderPool.executor.submit(Callable { w.run(per) }) }
            var done = 0L
            for (f in futures) done += try { f.get() } catch (e: Exception) { 0L }
            totalSamples += done
        }
    }

    fun samplesFor(spp: Double) = (spp * pixels).toLong()

    // ------------------------------------------------------------------ tone mapping

    private fun merged(): FloatArray {
        shared?.let { return it }
        if (workers.size == 1) return workers[0].hist!!
        val out = mergeBuf ?: HistPool.get(pixels * 4).also { mergeBuf = it }
        System.arraycopy(workers[0].hist!!, 0, out, 0, out.size)
        for (k in 1 until workers.size) {
            val h = workers[k].hist!!
            for (i in out.indices) out[i] += h[i]
        }
        return out
    }

    fun toArgb(out: IntArray = IntArray(pixels)): IntArray {
        val f = flame
        val hist = merged()
        val area = pixels.toDouble() / (ppu * ppu)
        val spp = max(1.0, totalSamples.toDouble()) / pixels
        val k1 = f.contrast * f.brightness * 268.0 / 256.0
        val k2 = 1.0 / (f.contrast * area * spp) // flam3: 1/(contrast * area * sample_density)
        val g = 1.0 / max(0.01, f.gamma)
        val thr = max(1e-6, f.gammaThreshold)
        val thrPow = Math.exp(g * ln(thr)) / thr
        val vib = f.vibrancy
        val bgr = f.bgR / 255.0; val bgg = f.bgG / 255.0; val bgb = f.bgB / 255.0
        val bgArgb = (0xFF shl 24) or (f.bgR shl 16) or (f.bgG shl 8) or f.bgB
        val inv10 = k1 / ln(10.0)

        fun gam(x: Double) = if (x <= 0) 0.0 else if (x >= thr) Math.exp(g * ln(x)) else x * thrPow

        if (vib >= 0.999) return toArgbLut(hist, out, k2, inv10, ::gam, bgr, bgg, bgb, bgArgb)

        var i = 0
        var p = 0
        while (p < pixels) {
            val cnt = hist[i + 3].toDouble()
            if (cnt <= 0.0) {
                out[p] = bgArgb
            } else {
                val alpha = inv10 * ln(1.0 + cnt * k2) // = k1*log10(1+cnt*k2)
                val ls = alpha / cnt
                var r = hist[i] * ls
                var gg = hist[i + 1] * ls
                var b = hist[i + 2] * ls
                val ag = gam(alpha)
                if (vib >= 0.999) {
                    val tmp = ag / alpha
                    r *= tmp; gg *= tmp; b *= tmp
                } else {
                    val tmp = ag / alpha
                    r = vib * tmp * r + (1 - vib) * gam(r)
                    gg = vib * tmp * gg + (1 - vib) * gam(gg)
                    b = vib * tmp * b + (1 - vib) * gam(b)
                }
                val a = min(1.0, ag)
                val m = max(r, max(gg, b))
                if (m > 1.0) { r /= m; gg /= m; b /= m }
                r += (1 - a) * bgr; gg += (1 - a) * bgg; b += (1 - a) * bgb
                out[p] = (0xFF shl 24) or
                        ((min(1.0, max(0.0, r)) * 255 + 0.5).toInt() shl 16) or
                        ((min(1.0, max(0.0, gg)) * 255 + 0.5).toInt() shl 8) or
                        (min(1.0, max(0.0, b)) * 255 + 0.5).toInt()
            }
            i += 4; p++
        }
        return out
    }

    /**
     * Fast path for full vibrancy (the default): every pixel's scale factor depends only on
     * its hit count, so it is computed once per distinct count from a lookup table instead
     * of a log and a pow per pixel.
     */
    private fun toArgbLut(
        hist: FloatArray, out: IntArray, k2: Double, inv10: Double, gam: (Double) -> Double,
        bgr: Double, bgg: Double, bgb: Double, bgArgb: Int
    ): IntArray {
        val lutSize = 4096
        val scale = FloatArray(lutSize) // color multiplier per hit count
        val alph = FloatArray(lutSize)  // coverage alpha per hit count
        for (c in 1 until lutSize) {
            val alpha = inv10 * ln(1.0 + c * k2)
            val ag = gam(alpha)
            scale[c] = (ag / c).toFloat()
            alph[c] = min(1.0, ag).toFloat()
        }
        val br = bgr.toFloat(); val bgF = bgg.toFloat(); val bb = bgb.toFloat()
        var i = 0
        var p = 0
        while (p < pixels) {
            val cntF = hist[i + 3]
            if (cntF <= 0f) {
                out[p] = bgArgb
            } else {
                val c = cntF.toInt()
                val sc: Float; val a: Float
                if (c < lutSize && cntF == c.toFloat()) { sc = scale[c]; a = alph[c] }
                else {
                    val alpha = inv10 * ln(1.0 + cntF * k2)
                    val ag = gam(alpha)
                    sc = (ag / cntF).toFloat(); a = min(1.0, ag).toFloat()
                }
                var r = hist[i] * sc
                var g = hist[i + 1] * sc
                var b = hist[i + 2] * sc
                val m = max(r, max(g, b))
                if (m > 1f) { r /= m; g /= m; b /= m }
                val ia = 1f - a
                r += ia * br; g += ia * bgF; b += ia * bb
                out[p] = (0xFF shl 24) or
                        ((min(1f, max(0f, r)) * 255f + 0.5f).toInt() shl 16) or
                        ((min(1f, max(0f, g)) * 255f + 0.5f).toInt() shl 8) or
                        (min(1f, max(0f, b)) * 255f + 0.5f).toInt()
            }
            i += 4; p++
        }
        return out
    }

    /** Fraction of pixels that received at least one hit. */
    fun coverage(): Double {
        val hist = merged()
        var c = 0
        var i = 3
        while (i < hist.size) { if (hist[i] > 0f) c++; i += 4 }
        return c.toDouble() / pixels
    }

    // ------------------------------------------------------------------ worker

    private inner class Worker(val fl: Flame, seed: Long) {
        val hist: FloatArray? = if (privateHist) HistPool.get(pixels * 4) else null
        val ctx = Ctx(seed)
        val xfs = fl.xforms.toTypedArray()
        val fin = fl.finalXform
        var p = Pt()
        var q = Pt()
        val fp = Pt()
        val tables: Array<IntArray>
        val startTable: IntArray
        var xfIdx = 0
        var fused = false

        init {
            xfs.forEach { it.init(ctx) }
            fin?.init(ctx)
            startTable = buildTable(DoubleArray(xfs.size) { xfs[it].weight })
            tables = Array(xfs.size) { from ->
                val xa = xfs[from].xaos
                if (xa == null) startTable
                else buildTable(DoubleArray(xfs.size) { xfs[it].weight * (if (it < xa.size) xa[it] else 1.0) })
                    .let { t -> if (t.isEmpty()) startTable else t }
            }
        }

        private fun buildTable(w: DoubleArray): IntArray {
            val sum = w.sumOf { max(0.0, it) }
            if (sum <= 0.0) return IntArray(0)
            val t = IntArray(1024)
            var acc = 0.0
            var j = 0
            for (k in w.indices) {
                acc += max(0.0, w[k])
                val lim = (acc / sum * 1024).toInt()
                while (j < lim && j < 1024) { t[j++] = k }
            }
            while (j < 1024) t[j++] = w.indices.last { w[it] > 0 }
            return t
        }

        private fun reseed() {
            p.x = ctx.random() * 2 - 1; p.y = ctx.random() * 2 - 1; p.z = 0.0
            p.color = ctx.random(); p.invalidate()
            xfIdx = startTable[ctx.random(1024)]
            for (k in 0 until 20) step()
        }

        /** One chaos-game step; returns the xform used or null if the point blew up. */
        private fun step(): XForm? {
            val t = tables[xfIdx]
            if (t.isEmpty()) return null
            xfIdx = t[(ctx.random() * 1024).toInt() and 1023]
            val xf = xfs[xfIdx]
            xf.transform(ctx, p, q)
            if (bad(q)) { p.x = ctx.random() * 2 - 1; p.y = ctx.random() * 2 - 1; p.invalidate(); return null }
            val tmp = p; p = q; q = tmp // swap instead of copying
            return xf
        }

        private fun bad(t: Pt) = t.x.isNaN() || t.y.isNaN() || abs(t.x) > 1e10 || abs(t.y) > 1e10

        fun run(n: Long): Long {
            if (xfs.isEmpty() || startTable.isEmpty()) return 0
            if (!fused) { reseed(); fused = true }
            val h = hist ?: shared!!
            val w = width; val hh = height
            var i = 0L
            while (i < n) {
                if ((i and 0x3FFF) == 0L && cancelled.get()) break
                val xf = step()
                i++
                if (xf == null) continue
                if (xf.opacity < 1.0 && ctx.random() >= xf.opacity) continue
                val pt: Pt
                if (fin != null) {
                    fin.transform(ctx, p, fp)
                    if (bad(fp)) continue
                    pt = fp
                } else pt = p
                // plot
                val fx = mAx * pt.x + mBx * pt.y + mCx
                val fy = mAy * pt.x + mBy * pt.y + mCy
                if (fx < 0.0 || fy < 0.0) continue
                val px = fx.toInt(); val py = fy.toInt()
                if (px >= w || py >= hh) continue
                val ci = (pt.color * 255.0).toInt().coerceIn(0, 255)
                val idx = (py * w + px) shl 2
                h[idx] += palR[ci]
                h[idx + 1] += palG[ci]
                h[idx + 2] += palB[ci]
                h[idx + 3] += 1f
            }
            return i
        }
    }
}
