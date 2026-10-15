package com.flamedroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.flamedroid.FlameViewModel
import com.flamedroid.engine.Palettes
import java.util.Locale
import kotlin.math.abs

private fun argb(rgb: Int) = Color(rgb or (0xFF shl 24))

/**
 * Custom gradient editor.
 * - Tap an empty spot on the bar to add a color point there.
 * - Tap an existing point to open the color picker for it (tap again to close).
 * - Drag a point sideways to move it.
 */
@Composable
fun GradientEditor(vm: FlameViewModel) {
    val stops = vm.paletteStops
    var selectedRaw by remember { mutableStateOf<Int?>(null) }
    val selected = selectedRaw?.takeIf { it < stops.size }
    val gradient = remember(stops) { Palettes.fromStops(stops) }
    val hitRadiusPx = with(LocalDensity.current) { 18.dp.toPx() }

    val currentStops by rememberUpdatedState(stops)
    val currentSel by rememberUpdatedState(selected)
    var dragging by remember { mutableStateOf<Int?>(null) }

    fun nearest(x: Float, width: Float): Int? {
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        currentStops.forEachIndexed { i, s ->
            val d = abs((s.pos * width).toFloat() - x)
            if (d < bestD) { bestD = d; best = i }
        }
        return if (bestD <= hitRadiusPx) best else null
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val w = size.width.toFloat()
                        val hit = nearest(o.x, w)
                        if (hit != null) {
                            selectedRaw = if (currentSel == hit) null else hit
                        } else {
                            val pos = (o.x / w).toDouble().coerceIn(0.0, 1.0)
                            val color = Palettes.fromStops(currentStops)[(pos * 255).toInt()] and 0xFFFFFF
                            vm.setStops(currentStops + Palettes.Stop(pos, color))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> dragging = nearest(o.x, size.width.toFloat()) },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        val i = dragging ?: return@detectDragGestures
                        change.consume()
                        val pos = (change.position.x / size.width).toDouble().coerceIn(0.0, 1.0)
                        val list = currentStops.toMutableList()
                        if (i < list.size) {
                            list[i] = list[i].copy(pos = pos)
                            vm.setStops(list, coalesce = true)
                        }
                    }
                }
        ) {
            val outline = MaterialTheme.colorScheme.onSurface
            val accent = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val barH = size.height * 0.55f
                val w = size.width / 256f
                for (i in 0 until 256) {
                    drawRect(argb(gradient[i]), Offset(i * w, 0f), Size(w + 1f, barH))
                }
                // point markers: a stem into the bar plus a swatch below it
                stops.forEachIndexed { i, s ->
                    val x = (s.pos * size.width).toFloat()
                    val isSel = i == selected
                    val col = if (isSel) accent else outline
                    drawLine(col, Offset(x, barH * 0.35f), Offset(x, barH + 6f), strokeWidth = if (isSel) 4f else 2f)
                    val tri = Path().apply {
                        moveTo(x, barH + 2f); lineTo(x - 12f, barH + 18f); lineTo(x + 12f, barH + 18f); close()
                    }
                    drawPath(tri, col)
                    val sw = 26f
                    drawRoundRect(argb(s.color), Offset(x - sw / 2, barH + 18f), Size(sw, size.height - barH - 20f), CornerRadius(5f))
                    drawRoundRect(col, Offset(x - sw / 2, barH + 18f), Size(sw, size.height - barH - 20f), CornerRadius(5f),
                        style = Stroke(if (isSel) 4f else 2f))
                }
            }
        }
        Text(
            "Tap the bar to add a point · tap a point to edit its color · drag to move",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        val sel = selected
        if (sel != null) {
            val stop = stops[sel]
            Spacer(Modifier.height(10.dp))
            ColorPicker(
                key = sel,
                color = stop.color,
                onChange = { c ->
                    val list = vm.paletteStops.toMutableList()
                    if (sel < list.size) { list[sel] = list[sel].copy(color = c); vm.setStops(list, coalesce = true) }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Position ${(stop.pos * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedButton(
                    enabled = stops.size > 2,
                    onClick = {
                        vm.setStops(stops.filterIndexed { i, _ -> i != sel })
                        selectedRaw = null
                    }
                ) { Icon(Icons.Filled.Delete, null); Text("Delete point") }
                TextButton(onClick = { selectedRaw = null }) { Text("Done") }
            }
        }
    }
}

/** HSV color picker: saturation/brightness square, hue bar, hex entry. Emits 0xRRGGBB. */
@Composable
fun ColorPicker(key: Any, color: Int, onChange: (Int) -> Unit) {
    // keep HSV locally so hue isn't lost when saturation or brightness reach zero
    val hsv = remember(key) { FloatArray(3).also { android.graphics.Color.colorToHSV(color or (0xFF shl 24), it) } }
    var h by remember(key) { mutableStateOf(hsv[0]) }
    var sat by remember(key) { mutableStateOf(hsv[1]) }
    var v by remember(key) { mutableStateOf(hsv[2]) }
    val emit by rememberUpdatedState(onChange)

    fun push() {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF
        emit(c)
    }

    val hueColor = Color.hsv(h, 1f, 1f)
    val shape = RoundedCornerShape(8.dp)

    // saturation (x) / brightness (y) square
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .pointerInput(key) {
                fun set(o: Offset) {
                    sat = (o.x / size.width).coerceIn(0f, 1f)
                    v = 1f - (o.y / size.height).coerceIn(0f, 1f)
                    push()
                }
                detectTapGestures { set(it) }
            }
            .pointerInput(key) {
                fun set(o: Offset) {
                    sat = (o.x / size.width).coerceIn(0f, 1f)
                    v = 1f - (o.y / size.height).coerceIn(0f, 1f)
                    push()
                }
                detectDragGestures(onDragStart = { set(it) }) { change, _ -> change.consume(); set(change.position) }
            }
    ) {
        drawRoundRect(Brush.horizontalGradient(listOf(Color.White, hueColor)), cornerRadius = CornerRadius(16f))
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = CornerRadius(16f))
        val c = Offset(sat * size.width, (1f - v) * size.height)
        drawCircle(Color.Black, 13f, c, style = Stroke(5f))
        drawCircle(Color.White, 13f, c, style = Stroke(2.5f))
    }

    Spacer(Modifier.height(10.dp))
    // hue bar
    val hues = remember { List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) } }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(key) {
                detectTapGestures { o -> h = (o.x / size.width).coerceIn(0f, 1f) * 359.9f; push() }
            }
            .pointerInput(key) {
                detectDragGestures(onDragStart = { o -> h = (o.x / size.width).coerceIn(0f, 1f) * 359.9f; push() }) { change, _ ->
                    change.consume()
                    h = (change.position.x / size.width).coerceIn(0f, 1f) * 359.9f
                    push()
                }
            }
    ) {
        drawRoundRect(Brush.horizontalGradient(hues), cornerRadius = CornerRadius(14f))
        val x = h / 360f * size.width
        drawRoundRect(Color.White, Offset(x - 5f, -2f), Size(10f, size.height + 4f), CornerRadius(4f), style = Stroke(4f))
    }

    Spacer(Modifier.height(10.dp))
    val current = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)) and 0xFFFFFF
    var hex by remember(key, current) { mutableStateOf(String.format(Locale.US, "%06X", current)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(argb(current), shape).border(1.dp, MaterialTheme.colorScheme.outline, shape))
        OutlinedTextField(
            value = hex,
            onValueChange = { t ->
                val clean = t.uppercase(Locale.US).filter { it in "0123456789ABCDEF" }.take(6)
                hex = clean
                if (clean.length == 6) {
                    val out = FloatArray(3)
                    android.graphics.Color.colorToHSV(clean.toInt(16) or (0xFF shl 24), out)
                    h = out[0]; sat = out[1]; v = out[2]
                    push()
                }
            },
            prefix = { Text("#") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.width(160.dp)
        )
    }
}
