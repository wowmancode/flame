package com.flamedroid.engine

import kotlin.math.abs
import kotlin.math.floor

class NamedPalette(val name: String, val colors: IntArray)

object Palettes {
    /** Build a 256-entry gradient from evenly spaced key colours (0xRRGGBB). */
    fun gradient(vararg keys: Int): IntArray {
        val out = IntArray(256)
        val n = keys.size - 1
        for (i in 0 until 256) {
            val t = i / 255.0 * n
            val k = floor(t).toInt().coerceAtMost(n - 1)
            val f = t - k
            out[i] = lerp(keys[k], keys[k + 1], f)
        }
        return out
    }

    private fun lerp(a: Int, b: Int, f: Double): Int {
        val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * f).toInt()
        val g = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * f).toInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    fun hsv(h: Double, s: Double, v: Double): Int {
        val hh = ((h % 1.0) + 1.0) % 1.0 * 6.0
        val c = v * s
        val x = c * (1 - abs(hh % 2 - 1))
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        val m = v - c
        return (((r + m) * 255).toInt() shl 16) or (((g + m) * 255).toInt() shl 8) or ((b + m) * 255).toInt()
    }

    fun random(r: java.util.Random): IntArray {
        val n = 3 + r.nextInt(5)
        val baseHue = r.nextDouble()
        val spread = 0.08 + r.nextDouble() * 0.6
        val keys = IntArray(n) {
            val h = baseHue + (r.nextDouble() - 0.5) * spread * 2
            val s = 0.35 + r.nextDouble() * 0.65
            val v = if (r.nextDouble() < 0.1) 0.25 + r.nextDouble() * 0.3 else 0.7 + r.nextDouble() * 0.3
            hsv(h, s, v)
        }
        // guarantee at least one bright key so the flame never renders near-black
        if (keys.none { luma(it) > 0.6 }) keys[r.nextInt(n)] = hsv(baseHue, 0.3 + r.nextDouble() * 0.4, 1.0)
        return gradient(*keys)
    }

    fun luma(c: Int): Double {
        return (0.299 * (c shr 16 and 255) + 0.587 * (c shr 8 and 255) + 0.114 * (c and 255)) / 255.0
    }

    fun shifted(p: IntArray, shift: Int): IntArray = IntArray(256) { p[((it + shift) % 256 + 256) % 256] }

    fun reversed(p: IntArray): IntArray = IntArray(256) { p[255 - it] }

    val builtIn: List<NamedPalette> = listOf(
        NamedPalette("Inferno", gradient(0x000004, 0x420A68, 0x932667, 0xDD513A, 0xFCA50A, 0xFCFFA4)),
        NamedPalette("Ember", gradient(0x1A0500, 0x7A1A00, 0xE04A00, 0xFFA420, 0xFFF1C0)),
        NamedPalette("Ocean", gradient(0x02040F, 0x053B6B, 0x1A8FBF, 0x7FE3F0, 0xF2FFFF)),
        NamedPalette("Aurora", gradient(0x061A2B, 0x0F7B6C, 0x5CF29A, 0xA16BFF, 0xF4E8FF)),
        NamedPalette("Neon", gradient(0xFF00A8, 0x7A00FF, 0x00D4FF, 0x00FF9C, 0xFFF200)),
        NamedPalette("Rainbow", gradient(0xFF0000, 0xFF8800, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0044FF, 0xAA00FF, 0xFF0000)),
        NamedPalette("Sunset", gradient(0x2B0F54, 0xAB1F65, 0xFF4F69, 0xFF8142, 0xFFDA45)),
        NamedPalette("Ice", gradient(0x0B1026, 0x2E4A87, 0x8DB6E8, 0xE6F3FF, 0xFFFFFF)),
        NamedPalette("Forest", gradient(0x081C0E, 0x1F5A2A, 0x6FA83B, 0xD8E07A, 0xFFF6D6)),
        NamedPalette("Gold", gradient(0x140B00, 0x5C3B00, 0xC08A1E, 0xFFD36B, 0xFFF8E0)),
        NamedPalette("Magma", gradient(0x000004, 0x3B0F70, 0x8C2981, 0xDE4968, 0xFE9F6D, 0xFCFDBF)),
        NamedPalette("Viridis", gradient(0x440154, 0x3B528B, 0x21918C, 0x5EC962, 0xFDE725)),
        NamedPalette("Candy", gradient(0xFFB3D9, 0xC58CFF, 0x7FD1FF, 0xA6FFCB, 0xFFF3A6, 0xFFB3D9)),
        NamedPalette("Blood", gradient(0x0A0000, 0x4A0000, 0xA00010, 0xFF3030, 0xFFC0B0)),
        NamedPalette("Electric", gradient(0x000010, 0x0020A0, 0x2080FF, 0xA0F0FF, 0xFFFFFF, 0x2080FF)),
        NamedPalette("Mono", gradient(0x101010, 0x707070, 0xFFFFFF)),
        NamedPalette("Copper", gradient(0x0D0503, 0x5E2B17, 0xB86B3E, 0xF0B48A, 0xFFE9D6)),
        NamedPalette("Toxic", gradient(0x020A00, 0x1E5200, 0x7CFF00, 0xE8FF8A, 0x00FFB0)),
    )
}
