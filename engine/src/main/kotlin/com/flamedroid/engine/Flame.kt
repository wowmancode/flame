package com.flamedroid.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

class VarEntry(var variation: Variation, var amount: Double) {
    fun copy() = VarEntry(variation.copy(), amount)
}

/** One transform of the IFS (JWildfire XForm). */
class XForm {
    var weight = 0.5
    var color = 0.0
    /** JWildfire "symmetry" / color symmetry, -1..1 */
    var colorSymmetry = 0.0
    var opacity = 1.0
    var name = ""

    // affine: x' = c00*x + c10*y + c20 ; y' = c01*x + c11*y + c21
    var c00 = 1.0; var c01 = 0.0; var c10 = 0.0; var c11 = 1.0; var c20 = 0.0; var c21 = 0.0
    var p00 = 1.0; var p01 = 0.0; var p10 = 0.0; var p11 = 1.0; var p20 = 0.0; var p21 = 0.0

    val variations = ArrayList<VarEntry>()

    /** Xaos weights toward each other xform ("chaos" in .flame), null = all 1. */
    var xaos: DoubleArray? = null

    val hasPost get() = !(p00 == 1.0 && p01 == 0.0 && p10 == 0.0 && p11 == 1.0 && p20 == 0.0 && p21 == 0.0)

    // runtime
    private var cc1 = 1.0
    private var cc2 = 0.0
    private val aff = Pt()
    private val acc = Pt()
    private var vArr: Array<Variation> = emptyArray()
    private var aArr = DoubleArray(0)
    private var post = false

    /** Prepare for rendering: precompute constants and flatten the variation list. */
    fun init(ctx: Ctx) {
        cc1 = (1 + colorSymmetry) * 0.5
        cc2 = color * (1 - colorSymmetry) * 0.5
        for (e in variations) e.variation.init(ctx, this, e.amount)
        val live = variations.filter { it.amount != 0.0 }
        vArr = Array(live.size) { live[it].variation }
        aArr = DoubleArray(live.size) { live[it].amount }
        post = hasPost
    }

    fun transform(ctx: Ctx, src: Pt, dst: Pt) {
        aff.x = c00 * src.x + c10 * src.y + c20
        aff.y = c01 * src.x + c11 * src.y + c21
        aff.z = src.z
        aff.invalidate()
        acc.x = 0.0; acc.y = 0.0; acc.z = 0.0
        val va = vArr; val am = aArr
        for (i in va.indices) va[i].transform(ctx, this, aff, acc, am[i])
        if (post) {
            val px = p00 * acc.x + p10 * acc.y + p20
            val py = p01 * acc.x + p11 * acc.y + p21
            acc.x = px; acc.y = py
        }
        dst.x = acc.x; dst.y = acc.y; dst.z = acc.z
        dst.color = src.color * cc1 + cc2
        dst.invalidate()
    }

    fun deepCopy(): XForm {
        val x = XForm()
        x.weight = weight; x.color = color; x.colorSymmetry = colorSymmetry; x.opacity = opacity; x.name = name
        x.c00 = c00; x.c01 = c01; x.c10 = c10; x.c11 = c11; x.c20 = c20; x.c21 = c21
        x.p00 = p00; x.p01 = p01; x.p10 = p10; x.p11 = p11; x.p20 = p20; x.p21 = p21
        variations.forEach { x.variations += it.copy() }
        x.xaos = xaos?.copyOf()
        return x
    }

    // ---- editing helpers (operate on the pre-affine triangle, like JWildfire's editor)
    fun rotate(deg: Double, post: Boolean = false) {
        val a = Math.toRadians(deg); val c = cos(a); val s = sin(a)
        if (!post) {
            val n00 = c00 * c - c01 * s; val n01 = c00 * s + c01 * c
            val n10 = c10 * c - c11 * s; val n11 = c10 * s + c11 * c
            c00 = n00; c01 = n01; c10 = n10; c11 = n11
        } else {
            val n00 = p00 * c - p01 * s; val n01 = p00 * s + p01 * c
            val n10 = p10 * c - p11 * s; val n11 = p10 * s + p11 * c
            p00 = n00; p01 = n01; p10 = n10; p11 = n11
        }
    }

    fun scale(f: Double, post: Boolean = false) {
        if (!post) { c00 *= f; c01 *= f; c10 *= f; c11 *= f } else { p00 *= f; p01 *= f; p10 *= f; p11 *= f }
    }

    fun move(dx: Double, dy: Double, post: Boolean = false) {
        if (!post) { c20 += dx; c21 += dy } else { p20 += dx; p21 += dy }
    }

    fun resetAffine(post: Boolean = false) {
        if (!post) { c00 = 1.0; c01 = 0.0; c10 = 0.0; c11 = 1.0; c20 = 0.0; c21 = 0.0 }
        else { p00 = 1.0; p01 = 0.0; p10 = 0.0; p11 = 1.0; p20 = 0.0; p21 = 0.0 }
    }

    val angleDeg get() = Math.toDegrees(atan2(c01, c00))
    val scaleX get() = sqrt(c00 * c00 + c01 * c01)

    fun addVariation(name: String, amount: Double = 1.0): VarEntry {
        val e = VarEntry(Variations.create(name), amount)
        variations += e
        return e
    }
}

class Flame {
    var name = "Untitled"
    var width = 800
    var height = 600
    var centerX = 0.0
    var centerY = 0.0
    var pixelsPerUnit = 200.0
    var camZoom = 1.0
    /** camera rotation in degrees */
    var rotate = 0.0
    var brightness = 4.0
    var contrast = 1.0
    var gamma = 4.0
    var gammaThreshold = 0.01
    var vibrancy = 1.0
    var bgR = 0; var bgG = 0; var bgB = 0
    var quality = 50.0 // samples per pixel for final render

    var palette: IntArray = Palettes.builtIn.first().colors.copyOf()
    val xforms = ArrayList<XForm>()
    var finalXform: XForm? = null

    fun deepCopy(): Flame {
        val f = Flame()
        f.name = name; f.width = width; f.height = height; f.centerX = centerX; f.centerY = centerY
        f.pixelsPerUnit = pixelsPerUnit; f.camZoom = camZoom; f.rotate = rotate; f.brightness = brightness
        f.contrast = contrast; f.gamma = gamma; f.gammaThreshold = gammaThreshold; f.vibrancy = vibrancy
        f.bgR = bgR; f.bgG = bgG; f.bgB = bgB; f.quality = quality
        f.palette = palette.copyOf()
        xforms.forEach { f.xforms += it.deepCopy() }
        f.finalXform = finalXform?.deepCopy()
        return f
    }

    /** Fix xaos arrays after xforms are added/removed. */
    fun normalizeXaos() {
        for (x in xforms) {
            val xa = x.xaos ?: continue
            if (xa.size != xforms.size) x.xaos = DoubleArray(xforms.size) { if (it < xa.size) xa[it] else 1.0 }
        }
    }

    fun removeXForm(index: Int) {
        xforms.removeAt(index)
        for (x in xforms) {
            val xa = x.xaos ?: continue
            if (index < xa.size) x.xaos = xa.filterIndexed { i, _ -> i != index }.toDoubleArray()
        }
    }

    fun distributeColors() {
        val n = xforms.size
        xforms.forEachIndexed { i, x -> x.color = if (n <= 1) 0.0 else i.toDouble() / (n - 1) }
    }
}
