package com.flamedroid

import com.flamedroid.engine.RenderPool

/**
 * How hard the preview is allowed to work. Phones heat up from sustained full-speed load on
 * all cores, so the cooler modes use fewer threads, smaller previews, a lower frame rate,
 * a lower quality ceiling, and rest between bursts of work.
 */
enum class PerfMode(
    val label: String,
    val description: String,
    /** preview long edge (px) once the image settles */
    val previewLong: Int,
    /** quality ceiling for the settled preview (samples per pixel) */
    val maxSpp: Double,
    /** long edge and samples per pixel for quick frames while dragging */
    val quickLong: Int,
    val quickSpp: Double,
    /** frame-rate cap while dragging */
    val fps: Int,
    /** pause after each burst of work, as a fraction of the burst's duration */
    val rest: Double,
) {
    ECO("Eco", "Coolest. One or two cores, smaller preview, rests between bursts.", 540, 15.0, 270, 1.5, 15, 0.6),
    BALANCED("Balanced", "Half the cores, medium preview. Good default.", 720, 30.0, 340, 2.0, 24, 0.15),
    FAST("Fast", "All cores, sharp preview. Warms the phone on long sessions.", 1000, 80.0, 420, 2.5, 30, 0.0);

    val previewThreads: Int get() = when (this) {
        ECO -> (RenderPool.cores / 4).coerceIn(1, 2)
        BALANCED -> ((RenderPool.cores + 1) / 2).coerceIn(1, 4)
        FAST -> RenderPool.cores
    }

    /** Threads for "Render & save" — the user is waiting, but Eco still holds back. */
    val exportThreads: Int get() = if (this == ECO) ((RenderPool.cores + 1) / 2).coerceAtLeast(1) else RenderPool.cores
}
