@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.flamedroid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flamedroid.FlameViewModel
import com.flamedroid.PerfMode
import com.flamedroid.engine.RandomFlame
import com.flamedroid.engine.Templates

/**
 * "New fractal": start from a template, or generate a batch of random fractals in a chosen
 * style and pick the one you like.
 */
@Composable
fun NewFractalScreen(vm: FlameViewModel, onClose: () -> Unit) {
    var style by remember { mutableStateOf(RandomFlame.Style.MIXED) }
    LaunchedEffect(Unit) {
        vm.loadTemplateThumbs()
        if (vm.gallery.isEmpty() && !vm.galleryBusy) vm.generateGallery(style)
    }
    val aspect = vm.flame.width.toFloat() / vm.flame.height

    Dialog(
        onDismissRequest = { vm.stopGallery(); onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("New fractal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.stopGallery(); onClose() }) { Icon(Icons.Filled.Close, "Close") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            FilledTonalButton(
                                onClick = { vm.stopGallery(); vm.newBlank(); onClose() },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.NoteAdd, null); Text("  Blank") }
                            Button(
                                onClick = { vm.stopGallery(); vm.randomFlame(RandomFlame.Style.PURE); onClose() },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Filled.Casino, null); Text("  Pure random") }
                        }
                        Text(
                            "Pure random: any variations, every setting random. Expect wild results — tap again for another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Random — tap one to open it") }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RandomFlame.Style.entries.forEach { s ->
                                    FilterChip(selected = style == s, onClick = { style = s; vm.generateGallery(s) }, label = { Text(s.label) })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = { vm.generateGallery(style) }) {
                                    Icon(Icons.Filled.Refresh, null); Text("  More like this")
                                }
                                if (vm.galleryBusy) {
                                    CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
                                    Text("  generating ${vm.gallery.size + 1} of 6…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    itemsIndexed(vm.gallery) { _, item ->
                        Thumb(item.thumb, item.flame.name, null, aspect) { vm.stopGallery(); vm.openGalleryItem(item); onClose() }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Templates") }
                    itemsIndexed(Templates.all) { i, t ->
                        Thumb(vm.templateThumbs[i], t.name, t.description, 1080f / 1350f) {
                            vm.stopGallery(); vm.newFromTemplate(i); onClose()
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(bottom = 24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Thumb(img: ImageBitmap?, title: String, subtitle: String?, aspect: Float, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.Black), contentAlignment = Alignment.Center) {
            if (img != null) Image(img, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Column(Modifier.padding(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Eco / Balanced / Fast picker. */
@Composable
fun PerformanceDialog(vm: FlameViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Performance") },
        text = {
            Column {
                PerfMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.changePerfMode(m) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = vm.perfMode == m, onClick = { vm.changePerfMode(m) })
                        Column {
                            Text(m.label, fontWeight = FontWeight.SemiBold)
                            Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}
