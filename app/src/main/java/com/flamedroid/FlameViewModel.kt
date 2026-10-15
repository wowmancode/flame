package com.flamedroid

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flamedroid.engine.Flame
import com.flamedroid.engine.FlameXml
import com.flamedroid.engine.Palettes
import com.flamedroid.engine.RandomFlame
import com.flamedroid.engine.Templates
import com.flamedroid.engine.RenderPool
import com.flamedroid.engine.Renderer
import com.flamedroid.engine.XForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private class PreviewSnap(val flame: Flame, val interactive: Boolean, val mode: PerfMode, val quick: Pair<Int, Int>, val full: Pair<Int, Int>)

class FlameViewModel(app: Application) : AndroidViewModel(app) {

    /** The flame being edited. Mutate it only through [edit]. */
    private val restored = loadAutosave()

    var flame: Flame = restored ?: starterFlame()
        private set

    /** Bumped on every change; composables read it to refresh. */
    var revision by mutableIntStateOf(0)
        private set

    /** -1 = final xform */
    var selected by mutableIntStateOf(0)

    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    var previewSpp by mutableFloatStateOf(0f)
        private set
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    /** Eco / Balanced / Fast — see [PerfMode]. Persisted. */
    var perfMode by mutableStateOf(
        PerfMode.entries.firstOrNull { it.name == prefs.getString("perf", null) } ?: PerfMode.BALANCED
    )
        private set

    fun changePerfMode(m: PerfMode) {
        perfMode = m
        prefs.edit().putString("perf", m.name).apply()
        requestPreview()
    }

    var message by mutableStateOf<String?>(null)

    // palette editing: shift is applied to this base palette
    var paletteBase: IntArray = flame.palette.copyOf()
        private set
    var paletteShift by mutableIntStateOf(0)
        private set

    /** Stops of the custom gradient editor (positions 0..1, colors 0xRRGGBB). */
    var paletteStops by mutableStateOf(Palettes.toStops(flame.palette))
        private set

    // export render state
    var exportProgress by mutableStateOf<Float?>(null)
        private set
    private var exportJob: Job? = null
    private var exportRenderer: Renderer? = null

    var pendingFlames by mutableStateOf<List<Flame>>(emptyList())

    private val undo = ArrayDeque<Flame>()
    private val redo = ArrayDeque<Flame>()
    private var lastUndoPush = 0L
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    // preview loop state
    private val previewRequests = MutableStateFlow(0)
    @Volatile private var refiningRenderer: Renderer? = null
    private var lastInteraction = 0L
    @Volatile private var paused = false

    // ---------------------------------------------------------------- editing

    fun selectedXForm(): XForm? =
        if (selected == -1) flame.finalXform else flame.xforms.getOrNull(selected)

    /**
     * Apply a change. Rapid successive edits (slider drags, gestures) are coalesced into a
     * single undo step.
     */
    fun edit(coalesce: Boolean = false, block: (Flame) -> Unit) {
        val now = System.currentTimeMillis()
        if (!coalesce || now - lastUndoPush > 700) pushUndo()
        lastUndoPush = now
        if (coalesce) lastInteraction = now
        block(flame)
        changed()
    }

    private fun pushUndo() {
        undo.addLast(flame.deepCopy())
        if (undo.size > 60) undo.removeFirst()
        redo.clear()
        updateUndoFlags()
    }

    private fun updateUndoFlags() { canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty() }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(flame)
        flame = prev
        afterReplace(resetPaletteBase = true)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(flame)
        flame = next
        afterReplace(resetPaletteBase = true)
    }

    private fun afterReplace(resetPaletteBase: Boolean) {
        if (resetPaletteBase) {
            paletteBase = flame.palette.copyOf(); paletteShift = 0
            paletteStops = Palettes.toStops(flame.palette)
        }
        if (selected >= flame.xforms.size) selected = flame.xforms.size - 1
        if (selected == -1 && flame.finalXform == null) selected = 0
        updateUndoFlags()
        changed()
    }

    private fun changed() {
        revision++
        requestPreview()
        scheduleAutosave()
    }

    fun replaceFlame(f: Flame) {
        pushUndo()
        flame = f
        selected = 0
        afterReplace(resetPaletteBase = true)
    }

    fun randomFlame(style: RandomFlame.Style) {
        message = null
        viewModelScope.launch {
            val f = withContext(Dispatchers.Default) { RandomFlame.generate(style, flame.width, flame.height) }
            replaceFlame(f)
        }
    }

    fun mutate() = replaceFlame(RandomFlame.mutate(flame))

    fun autoFit() = edit { RandomFlame.autoFit(it, 7) }

    // ---- xforms
    fun addXForm() = edit {
        val x = XForm()
        x.scale(0.5)
        x.move(Math.random() - 0.5, Math.random() - 0.5)
        x.addVariation("linear", 1.0)
        it.xforms += x
        it.normalizeXaos()
        selected = it.xforms.size - 1
    }

    fun duplicateXForm() {
        val src = selectedXForm() ?: return
        edit {
            val c = src.deepCopy()
            c.move(0.1, 0.1)
            it.xforms += c
            it.normalizeXaos()
            selected = it.xforms.size - 1
        }
    }

    fun deleteXForm() {
        if (selected == -1) { selected = 0; edit { it.finalXform = null }; return }
        if (flame.xforms.size <= 1) { message = "A flame needs at least one transform"; return }
        val idx = selected
        edit { it.removeXForm(idx) }
        selected = (idx - 1).coerceAtLeast(0)
    }

    fun toggleFinal() = edit {
        if (it.finalXform == null) {
            it.finalXform = XForm().apply { colorSymmetry = 1.0; addVariation("linear", 1.0) }
            selected = -1
        } else {
            it.finalXform = null
            selected = 0
        }
    }

    // ---- palette
    fun setPalette(p: IntArray) = edit {
        paletteBase = p.copyOf(); paletteShift = 0; it.palette = p.copyOf()
        paletteStops = Palettes.toStops(p)
    }

    /** Replace the custom gradient stops and apply them. */
    fun setStops(stops: List<Palettes.Stop>, coalesce: Boolean = false) = edit(coalesce) {
        paletteStops = stops
        paletteBase = Palettes.fromStops(stops)
        paletteShift = 0
        it.palette = paletteBase.copyOf()
    }

    fun changePaletteShift(s: Int) = edit(coalesce = true) { paletteShift = s; it.palette = Palettes.shifted(paletteBase, s) }

    fun reversePalette() = edit { paletteBase = Palettes.reversed(paletteBase); it.palette = Palettes.shifted(paletteBase, paletteShift) }

    // ---- camera gesture from the preview (pan in view pixels, zoom factor)
    fun gesture(panX: Float, panY: Float, zoom: Float, rotationDeg: Float, viewWidthPx: Int) {
        edit(coalesce = true) { f ->
            val ppuView = f.pixelsPerUnit * f.camZoom * (viewWidthPx.toDouble() / f.width)
            val a = Math.toRadians(f.rotate)
            val c = Math.cos(a); val s = Math.sin(a)
            // screen = R(-rot) * (world - center) * ppu  =>  dWorld = R(rot) * dScreen / ppu
            val dx = (panX * c - panY * s) / ppuView
            val dy = (panX * s + panY * c) / ppuView
            f.centerX -= dx
            f.centerY -= dy
            f.camZoom = (f.camZoom * zoom).coerceIn(0.01, 1000.0)
            f.rotate -= rotationDeg
        }
    }

    // ---------------------------------------------------------------- preview rendering
    //
    // One long-lived loop renders the preview:
    //  - while the user drags, quick low-resolution frames at a capped frame rate;
    //  - once input settles, progressive refinement in short bursts with rests between them
    //    (so the CPU isn't pinned at full boost clock, which is what heats a phone);
    //  - refinement stops at the mode's quality ceiling, or earlier once the image stops
    //    visibly changing; then the loop sleeps until the next edit.

    private fun previewSize(long: Int): Pair<Int, Int> {
        val f = flame
        return if (f.width >= f.height) long to max(1, (long.toDouble() * f.height / f.width).roundToInt())
        else max(1, (long.toDouble() * f.width / f.height).roundToInt()) to long
    }

    private fun requestPreview() {
        previewRequests.value = previewRequests.value + 1
        refiningRenderer?.cancel() // a newer edit makes the current refinement pointless
    }

    fun restartPreview() = requestPreview()

    /** Called from the activity: stop background rendering while the app isn't visible. */
    fun setPaused(p: Boolean) {
        paused = p
        if (p) refiningRenderer?.cancel() else requestPreview()
    }

    /** Reused pixel buffers and a small ring of bitmaps per size (no per-frame allocation). */
    private class FrameBuffers(val w: Int, val h: Int) {
        val argb = IntArray(w * h)
        val prev = IntArray(w * h)
        val bitmaps = Array(3) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
        var next = 0
    }
    private val frameBuffers = HashMap<Long, FrameBuffers>()

    private fun buffers(w: Int, h: Int): FrameBuffers {
        val key = (w.toLong() shl 32) or h.toLong()
        frameBuffers[key]?.let { return it }
        if (frameBuffers.size > 4) frameBuffers.clear() // aspect/mode changes: drop old sizes
        return FrameBuffers(w, h).also { frameBuffers[key] = it }
    }

    private suspend fun publish(r: Renderer, fb: FrameBuffers, spp: Double) {
        r.toArgb(fb.argb)
        // write into a bitmap that isn't on screen, then hand it over
        val bmp = fb.bitmaps[fb.next]
        fb.next = (fb.next + 1) % fb.bitmaps.size
        bmp.setPixels(fb.argb, 0, fb.w, 0, 0, fb.w, fb.h)
        val img = bmp.asImageBitmap()
        withContext(Dispatchers.Main) { preview = img; previewSpp = spp.toFloat() }
    }

    /** Mean per-channel change between two frames, sampled on a sparse grid (0..255). */
    private fun frameDelta(a: IntArray, b: IntArray): Double {
        var sum = 0L; var n = 0
        var i = 0
        while (i < a.size) {
            val x = a[i]; val y = b[i]
            sum += abs((x shr 16 and 255) - (y shr 16 and 255)) + abs((x shr 8 and 255) - (y shr 8 and 255)) + abs((x and 255) - (y and 255))
            n += 3
            i += 13
        }
        return if (n == 0) 0.0 else sum.toDouble() / n
    }

    private fun startPreviewLoop() = viewModelScope.launch(Dispatchers.Default) {
        var rendered = -1
        while (isActive) {
            val req = previewRequests.first { it != rendered }
            rendered = req
            if (paused) continue
            val snap = withContext(Dispatchers.Main) {
                val m = perfMode
                PreviewSnap(
                    flame.deepCopy(),
                    System.currentTimeMillis() - lastInteraction < 250,
                    m,
                    previewSize(m.quickLong),
                    previewSize(m.previewLong)
                )
            }
            val mode = snap.mode
            if (snap.interactive) {
                val t0 = System.currentTimeMillis()
                val (qw, qh) = snap.quick
                val fb = buffers(qw, qh)
                val q = Renderer(snap.flame, qw, qh, mode.previewThreads)
                try {
                    q.addSamples(q.samplesFor(mode.quickSpp))
                    publish(q, fb, mode.quickSpp)
                } finally { q.shutdown() }
                val spare = 1000L / mode.fps - (System.currentTimeMillis() - t0)
                if (spare > 0) delay(spare)
                // keep showing quick frames while input continues; refine once it settles
                val next = withTimeoutOrNull(250) { previewRequests.first { it != req } }
                if (next != null) continue
            }
            val (w, h) = snap.full
            val fb = buffers(w, h)
            val r = Renderer(snap.flame, w, h, mode.previewThreads)
            refiningRenderer = r
            try {
                // checkpoints at which the image is shown; the work between them is split into
                // ~1 spp bursts so rests can be inserted and edits interrupt quickly
                val shows = doubleArrayOf(0.5, 2.0, 5.0, 10.0, 20.0, 35.0, 60.0, 100.0).filter { it <= mode.maxSpp } +
                        listOf(mode.maxSpp)
                var done = 0.0
                var lastShown = -1.0
                loop@ for (target in shows.distinct().sorted()) {
                    while (done < target) {
                        if (previewRequests.value != req || paused) break@loop
                        val step = minOf(target - done, if (done < 2.0) target - done else 1.5)
                        val t0 = System.nanoTime()
                        if (!r.addSamples(r.samplesFor(step))) break@loop
                        done += step
                        if (mode.rest > 0) delay(((System.nanoTime() - t0) / 1_000_000 * mode.rest).toLong())
                    }
                    System.arraycopy(fb.argb, 0, fb.prev, 0, fb.argb.size)
                    publish(r, fb, done)
                    // stop early once more samples no longer visibly change the image
                    if (lastShown >= 5.0 && frameDelta(fb.argb, fb.prev) < 0.35) break
                    lastShown = done
                }
            } finally {
                refiningRenderer = null
                r.shutdown()
            }
        }
    }

    // ---------------------------------------------------------------- new fractal: gallery & templates

    class GalleryItem(val flame: Flame, val thumb: ImageBitmap)

    var gallery by mutableStateOf<List<GalleryItem>>(emptyList())
        private set
    var galleryBusy by mutableStateOf(false)
        private set
    var templateThumbs by mutableStateOf<Map<Int, ImageBitmap>>(emptyMap())
        private set
    private var galleryJob: Job? = null
    private var templateJob: Job? = null

    private fun thumbSize(w: Int, h: Int, long: Int = 260): Pair<Int, Int> =
        if (w >= h) long to max(1, long * h / w) else max(1, long * w / h) to long

    /** Small preview image of a flame (runs on a background thread). */
    private fun renderThumb(f: Flame, threads: Int): ImageBitmap {
        val (w, h) = thumbSize(f.width, f.height)
        val r = Renderer(f, w, h, threads)
        try {
            r.addSamples(r.samplesFor(25.0))
            return Bitmap.createBitmap(r.toArgb(), w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        } finally { r.shutdown() }
    }

    /** Generate [count] random fractals of a style, adding each to [gallery] as it's ready. */
    fun generateGallery(style: RandomFlame.Style, count: Int = 6) {
        galleryJob?.cancel()
        gallery = emptyList()
        galleryBusy = true
        val w = flame.width; val h = flame.height
        val threads = perfMode.previewThreads
        galleryJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                repeat(count) {
                    if (!isActive) return@launch
                    val f = RandomFlame.generate(style, w, h)
                    val img = renderThumb(f, threads)
                    withContext(Dispatchers.Main) { gallery = gallery + GalleryItem(f, img) }
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { galleryBusy = false }
            }
        }
    }

    fun stopGallery() { galleryJob?.cancel() }

    fun loadTemplateThumbs() {
        if (templateJob?.isActive == true || templateThumbs.size == Templates.all.size) return
        val threads = perfMode.previewThreads
        templateJob = viewModelScope.launch(Dispatchers.Default) {
            Templates.all.forEachIndexed { i, t ->
                if (!isActive || templateThumbs.containsKey(i)) return@forEachIndexed
                val img = renderThumb(t.build(), threads)
                withContext(Dispatchers.Main) { templateThumbs = templateThumbs + (i to img) }
            }
        }
    }

    fun newFromTemplate(index: Int) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.Default) { Templates.all[index].build() }
            replaceFlame(f)
        }
    }

    fun openGalleryItem(item: GalleryItem) = replaceFlame(item.flame.deepCopy())

    // ---------------------------------------------------------------- export image

    fun exportImage(width: Int, height: Int, spp: Double) {
        if (exportJob?.isActive == true) return
        val snapshot = flame.deepCopy()
        exportProgress = 0f
        exportJob = viewModelScope.launch(Dispatchers.Default) {
            val r = try { Renderer(snapshot, width, height, perfMode.exportThreads) } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) { exportProgress = null; message = "Not enough memory for ${width}×$height" }
                return@launch
            }
            exportRenderer = r
            try {
                val chunks = 40
                val per = r.samplesFor(spp) / chunks
                for (i in 0 until chunks) {
                    if (!r.addSamples(per)) break
                    withContext(Dispatchers.Main) { exportProgress = (i + 1f) / chunks }
                }
                if (r.isCancelled) {
                    withContext(Dispatchers.Main) { exportProgress = null; message = "Render cancelled" }
                    return@launch
                }
                val bmp = Bitmap.createBitmap(r.toArgb(), width, height, Bitmap.Config.ARGB_8888)
                val uri = saveToGallery(bmp, snapshot.name)
                bmp.recycle()
                withContext(Dispatchers.Main) {
                    exportProgress = null
                    message = if (uri != null) "Saved to Pictures/FlameDroid" else "Could not save image"
                }
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) { exportProgress = null; message = "Out of memory — try a smaller size" }
            } finally {
                r.shutdown()
                exportRenderer = null
            }
        }
    }

    fun cancelExport() { exportRenderer?.cancel() }

    private fun saveToGallery(bmp: Bitmap, name: String): Uri? {
        val ctx = getApplication<Application>()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = name.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40)
        val fileName = "flame_${safe}_$stamp.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FlameDroid")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FlameDroid")
            dir.mkdirs()
            val file = File(dir, fileName)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            @Suppress("DEPRECATION")
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            Uri.fromFile(file)
        }
    }

    // ---------------------------------------------------------------- .flame import / export

    fun importFlame(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: throw IllegalStateException("Could not open file")
                val result = withContext(Dispatchers.Default) { FlameXml.parse(text) }
                if (result.flames.isEmpty()) { message = "No flames found in that file"; return@launch }
                if (result.skipped.isNotEmpty())
                    message = "Skipped unsupported variations: " + result.skipped.take(8).joinToString(", ") +
                            if (result.skipped.size > 8) " …" else ""
                if (result.flames.size == 1) replaceFlame(result.flames[0]) else pendingFlames = result.flames
            } catch (e: Exception) {
                message = "Import failed: ${e.message}"
            }
        }
    }

    fun exportFlame(uri: Uri) {
        val xml = FlameXml.write(listOf(flame))
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(xml.toByteArray()) } != null
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) { message = if (ok) "Flame saved" else "Could not write file" }
        }
    }

    fun flameXml(): String = FlameXml.write(listOf(flame))

    fun setAspect(w: Int, h: Int) = edit {
        // keep the same horizontal framing in world units
        val ratio = w.toDouble() / it.width
        it.pixelsPerUnit *= ratio
        it.width = w; it.height = h
        RandomFlame.autoFit(it, 3)
    }

    // ---------------------------------------------------------------- autosave

    private val autosaveFile get() = File(getApplication<Application>().filesDir, "autosave.flame")
    private var autosaveJob: Job? = null

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(2000) // only after edits settle
            val xml = FlameXml.write(listOf(flame))
            withContext(Dispatchers.IO) { try { autosaveFile.writeText(xml) } catch (_: Exception) {} }
        }
    }

    private fun starterFlame(): Flame = Flame().apply {
        width = 1080; height = 1350
        listOf(0.0 to -0.5, -0.5 to 0.4, 0.5 to 0.4).forEach { (x, y) ->
            xforms += XForm().apply { scale(0.5); c20 = x; c21 = y; addVariation("linear", 1.0) }
        }
        distributeColors()
        RandomFlame.autoFit(this, 1)
    }

    private fun loadAutosave(): Flame? = try {
        val f = File(getApplication<Application>().filesDir, "autosave.flame")
        if (f.exists()) FlameXml.parse(f.readText()).flames.firstOrNull()?.takeIf { it.xforms.isNotEmpty() } else null
    } catch (_: Exception) { null }

    // Must come after all property initializers (they run in declaration order).
    init {
        startPreviewLoop()
        requestPreview()
        if (restored == null) randomFlameFirst()
    }

    /** First launch: generate a random flame off the main thread, without an undo step. */
    private fun randomFlameFirst() = viewModelScope.launch {
        val f = withContext(Dispatchers.Default) { RandomFlame.generate(RandomFlame.Style.MIXED, 1080, 1350) }
        flame = f
        afterReplace(resetPaletteBase = true)
    }

    override fun onCleared() {
        refiningRenderer?.cancel()
        exportRenderer?.cancel()
        super.onCleared()
    }
}
