package com.flamedroid.engine

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads and writes .flame files compatible with JWildfire / Apophysis / flam3.
 * Unsupported variations are skipped and reported in [ParseResult.skipped].
 */
object FlameXml {

    class ParseResult(val flames: List<Flame>, val skipped: Set<String>)

    private val nonVariationAttrs = setOf(
        "weight", "color", "symmetry", "color_speed", "opacity", "coefs", "post", "chaos", "name", "animate",
        "var_color", "plotmode", "targetcolor", "material", "material_speed", "mod_gamma", "mod_gamma_speed",
        "mod_contrast", "mod_contrast_speed", "mod_saturation", "mod_saturation_speed", "mod_hue", "mod_hue_speed",
        "yzCoefs", "yzPost", "zxCoefs", "zxPost", "coefsYZ", "coefsZX", "color_type", "enabled", "linear3D_fx_priority",
        "draw_mode", "xyCoefs"
    )

    fun parse(xml: String): ParseResult {
        val dbf = DocumentBuilderFactory.newInstance()
        try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
        val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val skipped = linkedSetOf<String>()
        val nodes = doc.getElementsByTagName("flame")
        val result = ArrayList<Flame>()
        for (i in 0 until nodes.length) result += parseFlame(nodes.item(i) as Element, skipped)
        return ParseResult(result, skipped)
    }

    private fun children(e: Element, tag: String): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = e.firstChild
        while (n != null) {
            if (n is Element && n.tagName == tag) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun d(s: String?, def: Double) = s?.trim()?.toDoubleOrNull() ?: def
    private fun nums(s: String?): List<Double> = s?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toDoubleOrNull() } ?: emptyList()

    private fun parseFlame(e: Element, skipped: MutableSet<String>): Flame {
        val f = Flame()
        f.name = e.getAttribute("name").ifBlank { "Untitled" }
        nums(e.getAttribute("size")).let { if (it.size >= 2) { f.width = it[0].toInt(); f.height = it[1].toInt() } }
        nums(e.getAttribute("center")).let { if (it.size >= 2) { f.centerX = it[0]; f.centerY = it[1] } }
        f.pixelsPerUnit = d(e.getAttribute("scale"), 200.0)
        f.camZoom = d(e.getAttribute("cam_zoom"), 1.0)
        f.rotate = d(e.getAttribute("rotate"), 0.0)
        f.brightness = d(e.getAttribute("brightness"), 4.0)
        f.contrast = d(e.getAttribute("contrast"), 1.0)
        f.gamma = d(e.getAttribute("gamma"), 4.0)
        f.gammaThreshold = d(e.getAttribute("gamma_threshold"), 0.01)
        f.vibrancy = d(e.getAttribute("vibrancy"), 1.0)
        f.quality = d(e.getAttribute("quality"), 50.0)
        nums(e.getAttribute("background")).let {
            if (it.size >= 3) {
                val scale = if (it.any { v -> v > 1.0 }) 1.0 else 255.0
                f.bgR = (it[0] * scale).toInt().coerceIn(0, 255)
                f.bgG = (it[1] * scale).toInt().coerceIn(0, 255)
                f.bgB = (it[2] * scale).toInt().coerceIn(0, 255)
            }
        }

        // JWildfire may wrap content in <layer>; use the first layer if there are no direct xforms
        var src = e
        if (children(e, "xform").isEmpty()) children(e, "layer").firstOrNull()?.let { src = it }

        for (x in children(src, "xform")) f.xforms += parseXForm(x, skipped, false)
        children(src, "finalxform").firstOrNull()?.let { f.finalXform = parseXForm(it, skipped, true) }

        parsePalette(src)?.let { f.palette = it } ?: parsePalette(e)?.let { f.palette = it }
        f.normalizeXaos()
        return f
    }

    private fun parseXForm(e: Element, skipped: MutableSet<String>, isFinal: Boolean): XForm {
        val x = XForm()
        x.weight = d(e.getAttribute("weight"), if (isFinal) 0.0 else 0.5)
        x.color = nums(e.getAttribute("color")).firstOrNull() ?: 0.0
        x.colorSymmetry = when {
            e.hasAttribute("symmetry") -> d(e.getAttribute("symmetry"), 0.0)
            e.hasAttribute("color_speed") -> 1.0 - 2.0 * d(e.getAttribute("color_speed"), 0.5)
            else -> if (isFinal) 1.0 else 0.0
        }
        x.opacity = d(e.getAttribute("opacity"), 1.0)
        x.name = e.getAttribute("name")
        nums(e.getAttribute("coefs")).let {
            if (it.size >= 6) { x.c00 = it[0]; x.c01 = it[1]; x.c10 = it[2]; x.c11 = it[3]; x.c20 = it[4]; x.c21 = it[5] }
        }
        nums(e.getAttribute("post")).let {
            if (it.size >= 6) { x.p00 = it[0]; x.p01 = it[1]; x.p10 = it[2]; x.p11 = it[3]; x.p20 = it[4]; x.p21 = it[5] }
        }
        if (e.hasAttribute("chaos")) x.xaos = nums(e.getAttribute("chaos")).toDoubleArray()

        val attrs = e.attributes
        val entries = HashMap<String, VarEntry>()
        // pass 1: known variations
        for (i in 0 until attrs.length) {
            val name = attrs.item(i).nodeName
            if (name in nonVariationAttrs || !Variations.exists(name)) continue
            val amount = attrs.item(i).nodeValue.toDoubleOrNull() ?: continue
            val ve = VarEntry(Variations.create(name), amount)
            entries[name] = ve
            x.variations += ve
        }
        // pass 2: their parameters "<var>_<param>"; remember everything else that is numeric
        val leftovers = ArrayList<String>()
        for (i in 0 until attrs.length) {
            val name = attrs.item(i).nodeName
            if (name in nonVariationAttrs || entries.containsKey(name)) continue
            val value = attrs.item(i).nodeValue.toDoubleOrNull() ?: continue
            val owner = entries.keys.filter { name.startsWith(it + "_") }.maxByOrNull { it.length }
            if (owner != null && entries[owner]!!.variation.setParam(name.substring(owner.length + 1), value)) continue
            if (owner == null) leftovers += name
        }
        // report unsupported variations (leftovers that aren't parameters of another leftover)
        for (name in leftovers) if (leftovers.none { it != name && name.startsWith(it + "_") }) skipped += name
        if (x.variations.isEmpty() && !isFinal) x.addVariation("linear", 1.0)
        return x
    }

    private fun parsePalette(e: Element): IntArray? {
        children(e, "palette").firstOrNull()?.let { p ->
            val txt = p.textContent.replace(Regex("[^0-9A-Fa-f]"), "")
            val count = p.getAttribute("count").toIntOrNull() ?: (txt.length / 6)
            val n = minOf(count, txt.length / 6)
            if (n > 0) {
                val raw = IntArray(n) { (0xFF shl 24) or txt.substring(it * 6, it * 6 + 6).toInt(16) }
                return resample(raw)
            }
        }
        val colors = children(e, "color")
        if (colors.isNotEmpty()) {
            val pal = IntArray(256) { 0xFF000000.toInt() }
            for (c in colors) {
                val idx = c.getAttribute("index").toIntOrNull() ?: continue
                val rgb = nums(c.getAttribute("rgb"))
                if (idx in 0..255 && rgb.size >= 3)
                    pal[idx] = (0xFF shl 24) or (rgb[0].toInt().coerceIn(0, 255) shl 16) or
                            (rgb[1].toInt().coerceIn(0, 255) shl 8) or rgb[2].toInt().coerceIn(0, 255)
            }
            return pal
        }
        return null
    }

    private fun resample(src: IntArray): IntArray = if (src.size == 256) src else IntArray(256) {
        src[(it * (src.size - 1) / 255.0).toInt().coerceIn(0, src.size - 1)]
    }

    // ------------------------------------------------------------------ writer

    private fun n(v: Double) = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
    else String.format(Locale.US, "%.10g", v).trimEnd('0').trimEnd('.')

    private fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    fun write(flames: List<Flame>): String {
        val sb = StringBuilder()
        sb.append("<flames name=\"FlameDroid\">\n")
        flames.forEach { writeFlame(sb, it) }
        sb.append("</flames>\n")
        return sb.toString()
    }

    fun write(flame: Flame): String = StringBuilder().also { writeFlame(it, flame) }.toString()

    private fun writeFlame(sb: StringBuilder, f: Flame) {
        sb.append("<flame name=\"").append(esc(f.name)).append("\" version=\"FlameDroid 1.0\"")
        sb.append(" size=\"${f.width} ${f.height}\" center=\"${n(f.centerX)} ${n(f.centerY)}\"")
        sb.append(" scale=\"${n(f.pixelsPerUnit)}\" rotate=\"${n(f.rotate)}\" cam_zoom=\"${n(f.camZoom)}\"")
        sb.append(" brightness=\"${n(f.brightness)}\" contrast=\"${n(f.contrast)}\" gamma=\"${n(f.gamma)}\"")
        sb.append(" gamma_threshold=\"${n(f.gammaThreshold)}\" vibrancy=\"${n(f.vibrancy)}\" quality=\"${n(f.quality)}\"")
        sb.append(" background=\"${n(f.bgR / 255.0)} ${n(f.bgG / 255.0)} ${n(f.bgB / 255.0)}\"")
        sb.append(" filter=\"0.75\" filter_kernel=\"GAUSSIAN\" oversample=\"1\" temporal_samples=\"1.0\">\n")
        f.xforms.forEach { writeXForm(sb, "xform", it, f) }
        f.finalXform?.let { writeXForm(sb, "finalxform", it, f) }
        sb.append("  <palette count=\"256\" format=\"RGB\">\n")
        for (row in 0 until 32) {
            sb.append("    ")
            for (k in 0 until 8) sb.append(String.format(Locale.US, "%06X", f.palette[row * 8 + k] and 0xFFFFFF))
            sb.append('\n')
        }
        sb.append("  </palette>\n</flame>\n")
    }

    private fun writeXForm(sb: StringBuilder, tag: String, x: XForm, f: Flame) {
        sb.append("  <").append(tag)
        if (tag == "xform") sb.append(" weight=\"${n(x.weight)}\"")
        sb.append(" color=\"${n(x.color)}\" symmetry=\"${n(x.colorSymmetry)}\"")
        if (tag == "xform") sb.append(" opacity=\"${n(x.opacity)}\"")
        if (x.name.isNotBlank()) sb.append(" name=\"${esc(x.name)}\"")
        for (e in x.variations) {
            sb.append(' ').append(e.variation.name).append("=\"").append(n(e.amount)).append('"')
            for (p in e.variation.params) {
                sb.append(' ').append(e.variation.name).append('_').append(p.name).append("=\"")
                    .append(if (p.isInt) p.value.toLong().toString() else n(p.value)).append('"')
            }
        }
        sb.append(" coefs=\"${n(x.c00)} ${n(x.c01)} ${n(x.c10)} ${n(x.c11)} ${n(x.c20)} ${n(x.c21)}\"")
        if (x.hasPost) sb.append(" post=\"${n(x.p00)} ${n(x.p01)} ${n(x.p10)} ${n(x.p11)} ${n(x.p20)} ${n(x.p21)}\"")
        x.xaos?.let { xa -> if (tag == "xform") sb.append(" chaos=\"").append(xa.joinToString(" ") { n(it) }).append('"') }
        sb.append("/>\n")
    }
}
