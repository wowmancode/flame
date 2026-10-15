@file:OptIn(ExperimentalMaterial3Api::class)

package com.flamedroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flamedroid.FlameViewModel
import com.flamedroid.engine.RandomFlame

private val tabs = listOf("Transforms", "Variations", "Colors", "Camera")

@Composable
fun FlameApp(vm: FlameViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var randomMenu by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showPerf by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision

    val openFlame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFlame(it) }
    }
    val saveFlame = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri ->
        uri?.let { vm.exportFlame(it) }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                    IconButton(onClick = vm::newBlank) { Icon(Icons.Filled.NoteAdd, "New blank fractal") }
                    IconButton(onClick = { showNew = true }) { Icon(Icons.Filled.AutoAwesome, "New fractal") }
                    Box {
                        IconButton(onClick = { randomMenu = true }) { Icon(Icons.Filled.Casino, "Random flame") }
                        DropdownMenu(expanded = randomMenu, onDismissRequest = { randomMenu = false }) {
                            RandomFlame.Style.values().forEach { s ->
                                DropdownMenuItem(text = { Text("Random: ${s.label}") }, onClick = {
                                    randomMenu = false; vm.randomFlame(s)
                                })
                            }
                        }
                    }
                    IconButton(onClick = { showExport = true }) { Icon(Icons.Filled.Save, "Render & save") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Open .flame…") }, onClick = {
                                menu = false; openFlame.launch(arrayOf("*/*"))
                            })
                            DropdownMenuItem(text = { Text("Save .flame…") }, onClick = {
                                menu = false
                                saveFlame.launch(vm.flame.name.replace(Regex("[^A-Za-z0-9_ -]+"), "_") + ".flame")
                            })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Auto-fit camera") }, onClick = { menu = false; vm.autoFit() })
                            DropdownMenuItem(text = { Text("New blank fractal") }, onClick = { menu = false; vm.newBlank() })
                            DropdownMenuItem(text = { Text("New fractal…") }, onClick = { menu = false; showNew = true })
                            DropdownMenuItem(text = { Text("Mutate (small random change)") },
                                leadingIcon = { Icon(Icons.Filled.Shuffle, null) }, onClick = { menu = false; vm.mutate() })
                            DropdownMenuItem(
                                text = { Text("Performance: ${vm.perfMode.label}…") },
                                onClick = { menu = false; showPerf = true })
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Preview(vm, Modifier.fillMaxWidth().weight(1f))
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            Box(
                Modifier.fillMaxWidth().weight(1.15f).background(MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (tab) {
                        0 -> TransformsTab(vm)
                        1 -> VariationsTab(vm)
                        2 -> ColorsTab(vm)
                        else -> CameraTab(vm)
                    }
                }
            }
        }
    }

    if (showExport) ExportDialog(vm) { showExport = false }
    if (showNew) NewFractalScreen(vm) { showNew = false }
    if (showPerf) PerformanceDialog(vm) { showPerf = false }

    if (vm.pendingFlames.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.pendingFlames = emptyList() },
            title = { Text("Choose a flame") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(vm.pendingFlames) { i, f ->
                        Text(
                            "${i + 1}. ${f.name}  (${f.xforms.size} transforms)",
                            modifier = Modifier.fillMaxWidth().clickable {
                                val pick = vm.pendingFlames[i]
                                vm.pendingFlames = emptyList()
                                vm.replaceFlame(pick)
                            }.padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.pendingFlames = emptyList() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Preview(vm: FlameViewModel, modifier: Modifier) {
    val f = vm.flame
    @Suppress("UNUSED_VARIABLE") val rev = vm.revision
    var viewWidth by remember { mutableIntStateOf(1) }
    val currentVm by rememberUpdatedState(vm)
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(f.width.toFloat() / f.height, matchHeightConstraintsFirst = f.height > f.width * 0.9)
                .onSizeChanged { viewWidth = it.width }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        currentVm.gesture(pan.x, pan.y, zoom, rotation, viewWidth)
                    }
                }
        ) {
            vm.preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Flame preview",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
        }
        if (f.xforms.isEmpty()) {
            Text(
                "Blank canvas\nOpen Transforms and tap Add,\nthen add variations to shape it",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            "${fmt(vm.previewSpp.toDouble())} spp",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
        )
    }
}

private data class SizeOpt(val label: String, val w: Int, val h: Int)

@Composable
private fun ExportDialog(vm: FlameViewModel, onClose: () -> Unit) {
    val f = vm.flame
    val aspect = f.width.toDouble() / f.height
    fun sized(long: Int): SizeOpt = if (aspect >= 1) SizeOpt("", long, (long / aspect).toInt()) else SizeOpt("", (long * aspect).toInt(), long)
    val sizes = listOf(1080, 1920, 2560, 3840).map { sized(it).let { s -> s.copy(label = "${s.w}×${s.h}") } }
    val qualities = listOf(50.0 to "Draft", 150.0 to "Good", 400.0 to "High", 1000.0 to "Ultra")
    var sizeIdx by remember { mutableIntStateOf(1) }
    var qIdx by remember { mutableIntStateOf(1) }
    val progress = vm.exportProgress

    AlertDialog(
        onDismissRequest = { if (progress == null) onClose() },
        title = { Text("Render & save image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Size", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sizes.take(2).forEachIndexed { i, s -> FilterChip(selected = sizeIdx == i, onClick = { sizeIdx = i }, label = { Text(s.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sizes.drop(2).forEachIndexed { i, s -> FilterChip(selected = sizeIdx == i + 2, onClick = { sizeIdx = i + 2 }, label = { Text(s.label) }) }
                }
                Text("Quality (samples per pixel)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    qualities.forEachIndexed { i, q -> FilterChip(selected = qIdx == i, onClick = { qIdx = i }, label = { Text(q.second) }) }
                }
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Saved as PNG to Pictures/FlameDroid. Higher sizes and qualities take longer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (progress == null) Button(onClick = {
                val s = sizes[sizeIdx]
                vm.exportImage(s.w, s.h, qualities[qIdx].first)
            }) { Text("Render") }
            else TextButton(onClick = vm::cancelExport) { Text("Cancel render") }
        },
        dismissButton = { if (progress == null) TextButton(onClick = onClose) { Text("Close") } }
    )
}
