package com.flamedroid.engine

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Progressive chaos-game renderer with flam3/JWildfire style log-density tone mapping.
 * Call [addSamples] repeatedly (e.g. from a background thread) and [toArgb] to get the
 * current image. Not thread-safe across concurrent addSamples calls.
 */
class Renderer(
    flame: Flame,
    val width: Int,
    val height: Int,
    threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    seed: Long = System.nanoTime()
) {
    private val flame = flame.deepCopy()
    private val hist = FloatArray(width * height * 4)
    @Volatile var totalSamples = 0L; private set
    private val cancelled = AtomicBoolean(false)
    private val pool: ExecutorService = Executors.newFixedThreadPool(threads) { r ->
        Thread(r, "flame-render").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    private val workers = List(threads) { Worker(this.flame.deepCopy(), seed + it * 7919L) }

    // camera
    private val ppu = this.flame.pixelsPerUnit * this.flame.camZoom * (width.toDouble() / this.flame.width)
    private val camCos = cos(Math.toRadians(-this.flame.rotate))
    private val camSin = sin(Math.toRadians(-this.flame.rotate))
    private val cx = this.flame.centerX
    private val cy = this.flame.centerY
    private val halfW = width / 2.0
    private val halfH = height / 2.0
    private val palR = FloatArray(256) { (this.flame.palette[it] shr 16 and 255) / 255f }
    private val palG = FloatArray(256) { (this.flame.palette[it] shr 8 and 255) / 255f }
    private val palB = FloatArray(256) { (this.flame.palette[it] and 255) / 255f }

    fun cancel() = cancelled.set(true)
    val isCancelled get() = cancelled.get()

    fun shutdown() { cancel(); pool.shutdownNow() }

    /** Iterate roughly [samples] more points, split across worker threads. Returns false if cancelled. */
    fun addSamples(samples: Long): Boolean {
        if (cancelled.get() || flame.xforms.isEmpty()) return false
        val per = samples / workers.size + 1
        val futures = workers.map { w -> pool.submit(Callable { w.run(per) }) }
        var done = 0L
        for (f in futures) done += try { f.get() } catch (e: Exception) { 0L }
        totalSamples += done
        return !cancelled.get()
    }

    /** Samples needed for a given number of samples per pixel. */
    fun samplesFor(spp: Double) = (spp * width * height).toLong()

    // ------------------------------------------------------------------ tone mapping

    fun toArgb(out: IntArray = IntArray(width * height)): IntArray {
        val f = flame
        val area = (width.toDouble() * height) / (ppu * ppu)
        val spp = max(1.0, totalSamples.toDouble()) / (width.toDouble() * height)
        val k1 = f.contrast * f.brightness * 268.0 / 256.0
        val k2 = 1.0 / (f.contrast * area * spp) // flam3: 1/(contrast * area * sample_density)
        val g = 1.0 / max(0.01, f.gamma)
        val thr = max(1e-6, f.gammaThreshold)
        val thrPow = thr.pow(g) / thr
        val vib = f.vibrancy
        val bgr = f.bgR / 255.0; val bgg = f.bgG / 255.0; val bgb = f.bgB / 255.0
        val bgArgb = (0xFF shl 24) or (f.bgR shl 16) or (f.bgG shl 8) or f.bgB

        fun gam(x: Double) = if (x <= 0) 0.0 else if (x >= thr) x.pow(g) else x * thrPow

        var i = 0
        var p = 0
        val n = width * height
        while (p < n) {
            val cnt = hist[i + 3].toDouble()
            if (cnt <= 0.0) {
                out[p] = bgArgb
            } else {
                val ls = k1 * log10(1.0 + cnt * k2) / cnt
                var r = hist[i] * ls
                var gg = hist[i + 1] * ls
                var b = hist[i + 2] * ls
                val alpha = cnt * ls
                val ag = gam(alpha)
                val tmp = ag / alpha
                r = vib * tmp * r + (1 - vib) * gam(r)
                gg = vib * tmp * gg + (1 - vib) * gam(gg)
                b = vib * tmp * b + (1 - vib) * gam(b)
                val a = ag.coerceIn(0.0, 1.0)
                // keep hue when over-exposed
                val m = max(r, max(gg, b))
                if (m > 1.0) { r /= m; gg /= m; b /= m }
                r += (1 - a) * bgr; gg += (1 - a) * bgg; b += (1 - a) * bgb
                out[p] = (0xFF shl 24) or
                        ((r.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 16) or
                        ((gg.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 8) or
                        (b.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
            }
            i += 4; p++
        }
        return out
    }

    /** Fraction of pixels that received at least one hit — used to reject empty random flames. */
    fun coverage(): Double {
        var c = 0
        var i = 3
        while (i < hist.size) { if (hist[i] > 0f) c++; i += 4 }
        return c.toDouble() / (width * height)
    }

    // ------------------------------------------------------------------ worker

    private inner class Worker(val fl: Flame, seed: Long) {
        val ctx = Ctx(seed)
        val xfs = fl.xforms.toTypedArray()
        val fin = fl.finalXform
        val p = Pt()
        val q = Pt()
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

        private fun step(): XForm? {
            val t = tables[xfIdx]
            if (t.isEmpty()) return null
            xfIdx = t[(ctx.random() * 1024).toInt() and 1023]
            val xf = xfs[xfIdx]
            xf.transform(ctx, p, q)
            if (bad(q)) { p.x = ctx.random() * 2 - 1; p.y = ctx.random() * 2 - 1; p.invalidate(); return null }
            p.assign(q)
            return xf
        }

        private fun bad(t: Pt) = t.x.isNaN() || t.y.isNaN() || abs(t.x) > 1e10 || abs(t.y) > 1e10

        fun run(n: Long): Long {
            if (xfs.isEmpty() || startTable.isEmpty()) return 0
            if (!fused) { reseed(); fused = true }
            var i = 0L
            while (i < n) {
                if ((i and 0xFFFF) == 0L && cancelled.get()) break
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
                plot(pt)
            }
            return i
        }

        private fun plot(pt: Pt) {
            val dx = pt.x - cx
            val dy = pt.y - cy
            val rx = dx * camCos - dy * camSin
            val ry = dx * camSin + dy * camCos
            val px = (rx * ppu + halfW).toInt()
            val py = (ry * ppu + halfH).toInt()
            if (px < 0 || py < 0 || px >= width || py >= height || rx * ppu + halfW < 0 || ry * ppu + halfH < 0) return
            val ci = (pt.color * 255.0).toInt().coerceIn(0, 255)
            val idx = (py * width + px) shl 2
            hist[idx] += palR[ci]
            hist[idx + 1] += palG[ci]
            hist[idx + 2] += palB[ci]
            hist[idx + 3] += 1f
        }
    }
}
