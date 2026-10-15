package com.flamedroid.engine

/** Hand-built starting points for the "New fractal" screen. */
object Templates {

    class Template(val name: String, val description: String, val build: () -> Flame)

    private fun flame(name: String, palette: String, block: Flame.() -> Unit): Flame {
        val f = Flame()
        f.name = name
        f.width = 1080; f.height = 1350
        f.palette = (Palettes.builtIn.firstOrNull { it.name == palette } ?: Palettes.builtIn.first()).colors.copyOf()
        f.block()
        if (f.xforms.isNotEmpty()) RandomFlame.autoFit(f, 11)
        return f
    }

    private fun Flame.xf(weight: Double = 1.0, block: XForm.() -> Unit): XForm {
        val x = XForm(); x.weight = weight; x.block(); xforms += x; return x
    }

    /** Barnsley-style affine, written as x' = a x + b y + e ; y' = c x + d y + f, flipped so it grows upward. */
    private fun XForm.barnsley(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double) {
        c00 = a; c10 = -b; c20 = e
        c01 = -c; c11 = d; c21 = -f
    }

    val all: List<Template> = listOf(
        Template("Blank", "Two plain transforms, ready for your own variations") {
            flame("Untitled", "Inferno") {
                xf { addVariation("linear", 1.0); scale(0.6); rotate(30.0); move(0.5, 0.0) }
                xf { addVariation("linear", 1.0); scale(0.65); rotate(-120.0); move(-0.3, 0.3) }
                distributeColors()
            }
        },
        Template("Sierpinski", "The classic triangle; swap in variations to warp it") {
            flame("Sierpinski", "Ocean") {
                listOf(0.0 to -0.5, -0.5 to 0.4, 0.5 to 0.4).forEach { (x, y) ->
                    xf { scale(0.5); c20 = x; c21 = y; addVariation("linear", 1.0) }
                }
                distributeColors()
            }
        },
        Template("Fern", "Barnsley's fern, an IFS of four affine maps") {
            flame("Fern", "Forest") {
                xf(0.01) { barnsley(0.0, 0.0, 0.0, 0.16, 0.0, 0.0); addVariation("linear", 1.0) }
                xf(0.85) { barnsley(0.85, 0.04, -0.04, 0.85, 0.0, 1.6); addVariation("linear", 1.0) }
                xf(0.07) { barnsley(0.2, -0.26, 0.23, 0.22, 0.0, 1.6); addVariation("linear", 1.0) }
                xf(0.07) { barnsley(-0.15, 0.28, 0.26, 0.24, 0.0, 0.44); addVariation("linear", 1.0) }
                distributeColors()
                xforms.forEach { it.colorSymmetry = 0.3 }
            }
        },
        Template("Julia spiral", "A rotating contraction plus a julian transform") {
            flame("Julia spiral", "Aurora") {
                xf(6.0) { scale(0.92); rotate(25.0); addVariation("linear", 1.0); colorSymmetry = 0.9 }
                xf(1.0) {
                    scale(0.6); move(0.8, 0.0)
                    addVariation("julian", 1.0).variation.apply { setParam("power", 3.0); setParam("dist", 1.0) }
                }
                distributeColors()
            }
        },
        Template("Galaxy", "Spiral arms with blurred satellites") {
            flame("Galaxy", "Ice") {
                xf(10.0) { scale(0.93); rotate(28.0); addVariation("linear", 1.0); addVariation("swirl", 0.03); colorSymmetry = 0.85 }
                xf(1.0) { scale(0.3); move(1.0, 0.0); addVariation("linear", 0.8); addVariation("gaussian_blur", 0.15) }
                distributeColors()
            }
        },
        Template("Flower", "Rotational symmetry around a petal shape") {
            flame("Flower", "Candy") {
                xf(1.0) { scale(0.7); move(0.4, 0.0); addVariation("flower", 0.8).variation.setParam("petals", 6.0); addVariation("linear", 0.2) }
                for (k in 1 until 6) xf(0.6) { rotate(60.0 * k); addVariation("linear", 1.0); colorSymmetry = 1.0 }
                xforms[0].color = 0.0
                for (i in 1 until xforms.size) xforms[i].color = 0.6
            }
        },
        Template("Kaleidoscope", "Mirror symmetry and a julian final transform") {
            flame("Kaleidoscope", "Neon") {
                xf(1.0) { scale(0.5); move(0.6, 0.1); rotate(20.0); addVariation("spherical", 0.6); addVariation("linear", 0.4) }
                xf(0.7) { scale(0.4); move(-0.2, 0.5); addVariation("julia", 0.8) }
                for (k in 1 until 8) xf(0.4) { rotate(45.0 * k); addVariation("linear", 1.0); colorSymmetry = 1.0 }
                xf(0.4) { c00 = -1.0; addVariation("linear", 1.0); colorSymmetry = 1.0 }
                finalXform = XForm().apply {
                    colorSymmetry = 1.0
                    addVariation("julian", 1.0).variation.apply { setParam("power", 2.0); setParam("dist", -1.0) }
                }
                xforms[0].color = 0.9
            }
        },
        Template("Bubbles", "Spherical bubbles woven together") {
            RandomFlame.generate(RandomFlame.Style.BUBBLES, 1080, 1350, seed = 567).apply { name = "Bubbles" }
        },
        Template("Dragon", "Heighway's dragon curve from two affine maps") {
            flame("Dragon", "Ember") {
                xf(1.0) { c00 = 0.5; c01 = 0.5; c10 = -0.5; c11 = 0.5; addVariation("linear", 1.0) }
                xf(1.0) { c00 = -0.5; c01 = 0.5; c10 = -0.5; c11 = -0.5; c20 = 1.0; addVariation("linear", 1.0) }
                distributeColors()
            }
        },
        Template("Hyperbolic tiling", "A {3,7} hyperbolic disc via hypertile") {
            flame("Hyperbolic tiling", "Electric") {
                for (n in 1..3) xf(1.0) {
                    addVariation("hypertile", 1.0).variation.apply { setParam("p", 3.0); setParam("q", 7.0); setParam("n", n.toDouble()) }
                }
                xf(0.6) { scale(0.4); addVariation("disc2", 0.3); addVariation("linear", 0.7) }
                distributeColors()
            }
        },
    )
}
