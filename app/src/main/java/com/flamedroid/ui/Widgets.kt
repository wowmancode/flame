package com.flamedroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

fun fmt(v: Double): String = when {
    v == Math.rint(v) && kotlin.math.abs(v) < 1e6 -> v.toLong().toString()
    kotlin.math.abs(v) >= 100 -> String.format(Locale.US, "%.1f", v)
    else -> String.format(Locale.US, "%.3f", v)
}

/**
 * Slider with a label and a tappable value that opens a dialog for exact entry.
 * The slider range expands automatically when a typed value lies outside it.
 */
@Composable
fun ValueSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    val lo = minOf(range.start, value)
    val hi = maxOf(range.endInclusive, value)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                fmt(value),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { editing = true }.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v -> onChange(if (integer) Math.round(v).toDouble() else v.toDouble()) },
            valueRange = lo.toFloat()..hi.toFloat(),
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier.height(32.dp)
        )
    }
    if (editing) NumberDialog(label, value, onDismiss = { editing = false }) {
        onChange(if (integer) Math.round(it).toDouble() else it)
        editing = false
    }
}

@Composable
fun NumberDialog(title: String, initial: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(fmt(initial)) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PaletteStrip(colors: IntArray, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val w = size.width / colors.size
        colors.forEachIndexed { i, c ->
            drawRect(Color(c or (0xFF shl 24)), topLeft = Offset(i * w, 0f), size = Size(w + 1f, size.height))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}
