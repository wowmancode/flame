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
import com.flamedroid.engine.Renderer
import com.flamedroid.engine.XForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class FlameViewModel(app: Application) : AndroidViewModel(app) {

    /** The flame being edited. Mutate it only through [edit]. */
    var flame: Flame = loadAutosave() ?: RandomFlame.generate(RandomFlame.Style.MIXED, 1080, 1350)
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
    var highQualityPreview by mutableStateOf(false)

    var message by mutableStateOf<String?>(null)

    // palette editing: shift is applied to this base palette
    var paletteBase: IntArray = flame.palette.copyOf()
        private set
    var paletteShift by mutableIntStateOf(0)
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

    private var renderJob: Job? = null
    private var renderer: Renderer? = null

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
        if (resetPaletteBase) { paletteBase = flame.palette.copyOf(); paletteShift = 0 }
        if (selected >= flame.xforms.size) selected = flame.xforms.size - 1
        if (selected == -1 && flame.finalXform == null) selected = 0
        updateUndoFlags()
        changed()
    }

    private fun changed() {
        revision++
        restartPreview()
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
    fun setPalette(p: IntArray) = edit { paletteBase = p.copyOf(); paletteShift = 0; it.palette = p.copyOf() }

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

    private fun previewSize(): Pair<Int, Int> {
        val long = if (highQualityPreview) 1080 else 640
        val f = flame
        return if (f.width >= f.height) long to max(1, (long.toDouble() * f.height / f.width).roundToInt())
        else max(1, (long.toDouble() * f.width / f.height).roundToInt()) to long
    }

    fun restartPreview() {
        renderer?.cancel()
        renderJob?.cancel()
        val snapshot = flame.deepCopy()
        val (w, h) = previewSize()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            delay(16) // let rapid edits collapse
            val r = Renderer(snapshot, w, h)
            renderer = r
            val pixels = IntArray(w * h)
            try {
                val steps = if (highQualityPreview) doubleArrayOf(0.5, 2.0, 6.0, 15.0, 40.0, 100.0, 200.0)
                else doubleArrayOf(0.5, 2.0, 6.0, 15.0, 40.0)
                var done = 0.0
                for (spp in steps) {
                    if (!isActive) break
                    if (!r.addSamples(r.samplesFor(spp - done))) break
                    done = spp
                    r.toArgb(pixels)
                    val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
                    withContext(Dispatchers.Main) {
                        if (isActive) { preview = bmp; previewSpp = spp.toFloat() }
                    }
                }
            } finally {
                r.shutdown()
            }
        }
    }

    // ---------------------------------------------------------------- export image

    fun exportImage(width: Int, height: Int, spp: Double) {
        if (exportJob?.isActive == true) return
        val snapshot = flame.deepCopy()
        exportProgress = 0f
        exportJob = viewModelScope.launch(Dispatchers.Default) {
            val r = try { Renderer(snapshot, width, height) } catch (e: OutOfMemoryError) {
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
        val xml = FlameXml.write(listOf(flame))
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1500)
            try { autosaveFile.writeText(xml) } catch (_: Exception) {}
        }
    }

    private fun loadAutosave(): Flame? = try {
        val f = File(getApplication<Application>().filesDir, "autosave.flame")
        if (f.exists()) FlameXml.parse(f.readText()).flames.firstOrNull()?.takeIf { it.xforms.isNotEmpty() } else null
    } catch (_: Exception) { null }

    // Must come after all property initializers (they run in declaration order).
    init {
        restartPreview()
    }

    override fun onCleared() {
        renderer?.cancel()
        exportRenderer?.cancel()
        super.onCleared()
    }
}
