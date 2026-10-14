@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.flamedroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flamedroid.FlameViewModel
import com.flamedroid.engine.Palettes
import com.flamedroid.engine.Variations
import com.flamedroid.engine.XForm
import kotlin.math.abs
import kotlin.math.max

// ================================================================== Transforms

@Composable
fun XFormSelector(vm: FlameViewModel) {
    val f = vm.flame
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        f.xforms.forEachIndexed { i, x ->
            FilterChip(
                selected = vm.selected == i,
                onClick = { vm.selected = i },
                label = { Text("${i + 1}" + (x.variations.firstOrNull()?.let { " · ${it.variation.name}" } ?: "")) },
                leadingIcon = { ColorDot(f.palette[(x.color * 255).toInt().coerceIn(0, 255)]) }
            )
        }
        if (f.finalXform != null) FilterChip(selected = vm.selected == -1, onClick = { vm.selected = -1 }, label = { Text("Final") })
    }
}

@Composable
private fun ColorDot(c: Int) {
    Box(Modifier.width(10.dp).aspectRatio(1f).background(Color(c or (0xFF shl 24)), RoundedCornerShape(50)))
}

@Composable
fun TransformsTab(vm: FlameViewModel) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision
    var post by remember { mutableStateOf(false) }
    var step by remember { mutableDoubleStateOf(1.0) }
    XFormSelector(vm)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        FilledTonalButton(onClick = vm::addXForm) { Icon(Icons.Filled.Add, null); Text("Add") }
        OutlinedButton(onClick = vm::duplicateXForm) { Icon(Icons.Filled.ContentCopy, null) }
        OutlinedButton(onClick = vm::deleteXForm) { Icon(Icons.Filled.Delete, null) }
        OutlinedButton(onClick = vm::toggleFinal) { Text(if (vm.flame.finalXform == null) "+ Final" else "− Final") }
    }
    val x = vm.selectedXForm() ?: return
    val isFinal = vm.selected == -1

    SectionTitle("Affine — drag the triangle to move it")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !post, onClick = { post = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Pre") }
        SegmentedButton(selected = post, onClick = { post = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Post") }
    }
    TriangleEditor(vm, post, Modifier.fillMaxWidth().aspectRatio(1.6f).padding(top = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        FilledTonalIconButton(onClick = { vm.edit { x.rotate(-15.0 * step, post) } }) { Icon(Icons.Filled.RotateLeft, "Rotate left") }
        FilledTonalIconButton(onClick = { vm.edit { x.rotate(15.0 * step, post) } }) { Icon(Icons.Filled.RotateRight, "Rotate right") }
        FilledTonalIconButton(onClick = { vm.edit { x.scale(1.0 / (1 + 0.1 * step), post) } }) { Icon(Icons.Filled.ZoomOut, "Shrink") }
        FilledTonalIconButton(onClick = { vm.edit { x.scale(1 + 0.1 * step, post) } }) { Icon(Icons.Filled.ZoomIn, "Grow") }
        TextButton(onClick = { vm.edit { x.resetAffine(post) } }) { Text("Reset") }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        val d = 0.1 * step
        IconButton(onClick = { vm.edit { x.move(-d, 0.0, post) } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") }
        IconButton(onClick = { vm.edit { x.move(d, 0.0, post) } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") }
        IconButton(onClick = { vm.edit { x.move(0.0, -d, post) } }) { Icon(Icons.Filled.KeyboardArrowUp, "Up") }
        IconButton(onClick = { vm.edit { x.move(0.0, d, post) } }) { Icon(Icons.Filled.KeyboardArrowDown, "Down") }
        IconButton(onClick = { vm.edit { if (post) { x.p00 = -x.p00; x.p01 = -x.p01 } else { x.c00 = -x.c00; x.c01 = -x.c01 } } }) {
            Icon(Icons.Filled.SwapHoriz, "Flip")
        }
        TextButton(onClick = { step = when (step) { 1.0 -> 0.2; 0.2 -> 3.0; else -> 1.0 } }) { Text("step ×${fmt(step)}") }
    }
    CoefficientRow(vm, x, post)

    SectionTitle("Transform")
    if (!isFinal) ValueSlider("Weight", x.weight, 0.0..3.0, { v -> vm.edit(true) { x.weight = max(0.0, v) } })
    ValueSlider("Color", x.color, 0.0..1.0, { v -> vm.edit(true) { x.color = v.coerceIn(0.0, 1.0) } })
    ValueSlider("Color speed (symmetry)", x.colorSymmetry, -1.0..1.0, { v -> vm.edit(true) { x.colorSymmetry = v } })
    if (!isFinal) ValueSlider("Opacity", x.opacity, 0.0..1.0, { v -> vm.edit(true) { x.opacity = v.coerceIn(0.0, 1.0) } })

    if (!isFinal && vm.flame.xforms.size > 1) {
        SectionTitle("Xaos — chance to jump to each transform")
        vm.flame.xforms.forEachIndexed { j, _ ->
            val cur = x.xaos?.getOrNull(j) ?: 1.0
            ValueSlider("→ ${j + 1}", cur, 0.0..2.0, { v ->
                vm.edit(true) {
                    val arr = x.xaos ?: DoubleArray(it.xforms.size) { 1.0 }
                    arr[j] = max(0.0, v)
                    x.xaos = if (arr.all { a -> a == 1.0 }) null else arr
                }
            })
        }
    }
    Spacer(Modifier.heightIn(min = 24.dp))
}

@Composable
private fun CoefficientRow(vm: FlameViewModel, x: XForm, post: Boolean) {
    var edit by remember { mutableStateOf<Int?>(null) }
    val vals = if (post) doubleArrayOf(x.p00, x.p01, x.p10, x.p11, x.p20, x.p21) else doubleArrayOf(x.c00, x.c01, x.c10, x.c11, x.c20, x.c21)
    val names = listOf("a", "d", "b", "e", "c", "f")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        vals.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .clickable { edit = i }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(names[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(v), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    edit?.let { i ->
        NumberDialog("Coefficient ${names[i]}", vals[i], onDismiss = { edit = null }) { v ->
            vm.edit {
                if (post) when (i) { 0 -> x.p00 = v; 1 -> x.p01 = v; 2 -> x.p10 = v; 3 -> x.p11 = v; 4 -> x.p20 = v; else -> x.p21 = v }
                else when (i) { 0 -> x.c00 = v; 1 -> x.c01 = v; 2 -> x.c10 = v; 3 -> x.c11 = v; 4 -> x.c20 = v; else -> x.c21 = v }
                Unit
            }
            edit = null
        }
    }
}

/** JWildfire-style triangle view: every transform's affine drawn as a triangle O–X–Y. */
@Composable
private fun TriangleEditor(vm: FlameViewModel, post: Boolean, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision
    val f = vm.flame
    val current by rememberUpdatedState(vm)
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Canvas(
        modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .pointerInput(post) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val x = current.selectedXForm() ?: return@detectDragGestures
                    val unitPx = size.height / 4.0 // same scale as the drawing below
                    current.edit(coalesce = true) { x.move(drag.x / unitPx, drag.y / unitPx, post) }
                }
            }
    ) {
        val unit = size.height / 4f
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun p(x: Double, y: Double) = Offset(cx + x.toFloat() * unit, cy + y.toFloat() * unit)
        // grid
        for (k in -4..4) drawLine(grid, p(k.toDouble(), -2.0), p(k.toDouble(), 2.0), strokeWidth = if (k == 0) 2f else 1f)
        for (k in -2..2) drawLine(grid, p(-4.0, k.toDouble()), p(4.0, k.toDouble()), strokeWidth = if (k == 0) 2f else 1f)
        val all = f.xforms.mapIndexed { i, x -> i to x } + listOfNotNull(f.finalXform?.let { -1 to it })
        all.forEach { (i, x) ->
            val sel = i == vm.selected
            val ox = if (post) x.p20 else x.c20; val oy = if (post) x.p21 else x.c21
            val ax = if (post) x.p00 else x.c00; val ay = if (post) x.p01 else x.c01
            val bx = if (post) x.p10 else x.c10; val by = if (post) x.p11 else x.c11
            val o = p(ox, oy); val xp = p(ox + ax, oy + ay); val yp = p(ox + bx, oy + by)
            val path = Path().apply { moveTo(o.x, o.y); lineTo(xp.x, xp.y); lineTo(yp.x, yp.y); close() }
            val col = if (sel) primary else dim
            drawPath(path, col.copy(alpha = if (sel) 0.18f else 0.06f))
            drawPath(path, col, style = Stroke(width = if (sel) 3f else 1.5f,
                pathEffect = if (i == -1) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null))
            if (sel) {
                drawCircle(primary, 6f, o)
                drawCircle(Color(0xFFFFD36B), 5f, xp)
                drawCircle(Color(0xFF7FE3F0), 5f, yp)
            }
        }
    }
}

// ================================================================== Variations

@Composable
fun VariationsTab(vm: FlameViewModel) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision
    var picking by remember { mutableStateOf(false) }
    XFormSelector(vm)
    val x = vm.selectedXForm() ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        FilledTonalButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, null); Text("Add variation") }
        Spacer(Modifier.weight(1f))
        Text("${Variations.names.size} available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (x.variations.isEmpty()) Text("No variations — this transform does nothing yet.", modifier = Modifier.padding(top = 12.dp))
    x.variations.toList().forEachIndexed { idx, e ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.variation.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (e.variation.params.isNotEmpty())
                        IconButton(onClick = { vm.edit { e.variation.randomize(java.util.Random()) } }) { Icon(Icons.Filled.Casino, "Randomize parameters") }
                    IconButton(onClick = { vm.edit { x.variations.removeAt(idx) } }) { Icon(Icons.Filled.Close, "Remove") }
                }
                ValueSlider("amount", e.amount, -2.0..2.0, { v -> vm.edit(true) { e.amount = v } })
                e.variation.params.forEach { p ->
                    val span = if (p.isInt) max(10.0, abs(p.value) * 2) else max(2.0, abs(p.value) * 2.5)
                    val lo = if (p.isInt && p.value >= 0) 0.0 else -span
                    ValueSlider(p.name, p.value, lo..span, { v -> vm.edit(true) { e.variation.setParam(p.name, v) } }, integer = p.isInt)
                }
            }
        }
    }
    Spacer(Modifier.heightIn(min = 24.dp))
    if (picking) VariationPicker(onDismiss = { picking = false }) { name ->
        picking = false
        vm.edit { x.addVariation(name, if (x.variations.isEmpty()) 1.0 else 0.5) }
    }
}

@Composable
private fun VariationPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) { Variations.names.filter { it.contains(query.trim(), ignoreCase = true) }.sorted() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add variation") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search ${Variations.names.size} variations") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(list) { n ->
                        val params = remember(n) { Variations.create(n).params.joinToString(", ") { it.name } }
                        Column(Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 10.dp)) {
                            Text(n, style = MaterialTheme.typography.bodyLarge)
                            if (params.isNotEmpty()) Text(params, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ================================================================== Colors

@Composable
fun ColorsTab(vm: FlameViewModel) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision
    val f = vm.flame
    SectionTitle("Current gradient")
    PaletteStrip(f.palette)
    ValueSlider("Shift", vm.paletteShift.toDouble(), 0.0..255.0, { v -> vm.setPaletteShift(v.toInt()) }, integer = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { vm.setPalette(Palettes.random(java.util.Random())) }) { Icon(Icons.Filled.Casino, null); Text("Random") }
        OutlinedButton(onClick = vm::reversePalette) { Text("Reverse") }
        OutlinedButton(onClick = { vm.edit { it.distributeColors() } }) { Text("Spread colors") }
    }
    SectionTitle("Gradients")
    Palettes.builtIn.forEach { p ->
        Column(Modifier.fillMaxWidth().clickable { vm.setPalette(p.colors) }.padding(vertical = 4.dp)) {
            Text(p.name, style = MaterialTheme.typography.labelMedium)
            PaletteStrip(p.colors)
        }
    }
    SectionTitle("Background")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Black" to 0x000000, "Night" to 0x0A0A1E, "Charcoal" to 0x1C1C1C, "Paper" to 0xF2EEE6, "White" to 0xFFFFFF).forEach { (n, c) ->
            AssistChip(onClick = { vm.edit { it.bgR = c shr 16 and 255; it.bgG = c shr 8 and 255; it.bgB = c and 255 } }, label = { Text(n) },
                leadingIcon = { ColorDot(c) })
        }
    }
    ValueSlider("Red", f.bgR.toDouble(), 0.0..255.0, { v -> vm.edit(true) { it.bgR = v.toInt().coerceIn(0, 255) } }, integer = true)
    ValueSlider("Green", f.bgG.toDouble(), 0.0..255.0, { v -> vm.edit(true) { it.bgG = v.toInt().coerceIn(0, 255) } }, integer = true)
    ValueSlider("Blue", f.bgB.toDouble(), 0.0..255.0, { v -> vm.edit(true) { it.bgB = v.toInt().coerceIn(0, 255) } }, integer = true)
    Spacer(Modifier.heightIn(min = 24.dp))
}

// ================================================================== Camera

@Composable
fun CameraTab(vm: FlameViewModel) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision
    val f = vm.flame
    Text("Tip: pinch, drag and twist the preview to move the camera.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionTitle("Camera")
    ValueSlider("Zoom", f.camZoom, 0.1..5.0, { v -> vm.edit(true) { it.camZoom = max(0.01, v) } })
    ValueSlider("Rotate", f.rotate, -180.0..180.0, { v -> vm.edit(true) { it.rotate = v } })
    ValueSlider("Center X", f.centerX, (f.centerX - 2)..(f.centerX + 2), { v -> vm.edit(true) { it.centerX = v } })
    ValueSlider("Center Y", f.centerY, (f.centerY - 2)..(f.centerY + 2), { v -> vm.edit(true) { it.centerY = v } })
    ValueSlider("Pixels per unit", f.pixelsPerUnit, 10.0..max(1000.0, f.pixelsPerUnit), { v -> vm.edit(true) { it.pixelsPerUnit = max(1.0, v) } })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = vm::autoFit) { Text("Auto-fit") }
    }
    SectionTitle("Aspect")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("4:5" to (1080 to 1350), "1:1" to (1080 to 1080), "9:16" to (1080 to 1920), "16:9" to (1920 to 1080), "4:3" to (800 to 600))
            .forEach { (n, s) ->
                FilterChip(selected = f.width * s.second == f.height * s.first, onClick = { vm.setAspect(s.first, s.second) }, label = { Text(n) })
            }
    }
    SectionTitle("Tone mapping")
    ValueSlider("Brightness", f.brightness, 0.5..20.0, { v -> vm.edit(true) { it.brightness = max(0.01, v) } })
    ValueSlider("Gamma", f.gamma, 1.0..8.0, { v -> vm.edit(true) { it.gamma = max(0.1, v) } })
    ValueSlider("Gamma threshold", f.gammaThreshold, 0.0..0.2, { v -> vm.edit(true) { it.gammaThreshold = max(0.0, v) } })
    ValueSlider("Vibrancy", f.vibrancy, 0.0..1.0, { v -> vm.edit(true) { it.vibrancy = v.coerceIn(0.0, 1.0) } })
    ValueSlider("Contrast", f.contrast, 0.2..3.0, { v -> vm.edit(true) { it.contrast = max(0.05, v) } })
    SectionTitle("Name")
    var naming by remember { mutableStateOf(false) }
    Text(f.name, modifier = Modifier.clickable { naming = true }.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.secondary)
    if (naming) {
        var text by remember { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Flame name") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.edit { it.name = text.ifBlank { "Untitled" } }; naming = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } }
        )
    }
    Spacer(Modifier.heightIn(min = 24.dp))
}
