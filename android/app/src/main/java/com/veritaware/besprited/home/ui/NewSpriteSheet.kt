// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veritaware.besprited.home.Background
import com.veritaware.besprited.home.ColorMode
import com.veritaware.besprited.home.NewSpriteSpec
import kotlin.math.max

private data class Preset(val label: String, val w: Int, val h: Int)

private val PRESETS = listOf(
    Preset("16²", 16, 16),
    Preset("32²", 32, 32),
    Preset("64²", 64, 64),
    Preset("128²", 128, 128),
    Preset("256²", 256, 256),
    Preset("Game Boy", 160, 144),
    Preset("16:9", 320, 180),
    Preset("Phone", 180, 320),
)

private const val MAX_SIZE = 4096

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSpriteSheet(
    palettes: List<String>,
    onDismiss: () -> Unit,
    onCreate: (NewSpriteSpec) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("Sprite") }
    var width by rememberSaveable { mutableStateOf("32") }
    var height by rememberSaveable { mutableStateOf("32") }
    var mode by rememberSaveable { mutableStateOf(ColorMode.RGB) }
    var background by rememberSaveable { mutableStateOf(Background.TRANSPARENT) }
    val defaultPalette = remember(palettes) { palettes.firstOrNull { it == "db32" } ?: palettes.firstOrNull() ?: "" }
    var palette by rememberSaveable(defaultPalette) { mutableStateOf(defaultPalette) }

    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val wValid = w != null && w in 1..MAX_SIZE
    val hValid = h != null && h in 1..MAX_SIZE

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New sprite", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Saved as .aseprite in your sprites folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CanvasPreview(if (wValid) w!! else 1, if (hValid) h!! else 1, background)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(64) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Size") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { p ->
                        FilterChip(
                            selected = w == p.w && h == p.h,
                            onClick = {
                                width = p.w.toString()
                                height = p.h.toString()
                            },
                            label = { Text(p.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Width", width, wValid, Modifier.weight(1f)) { width = it }
                    IconButton(onClick = {
                        val t = width
                        width = height
                        height = t
                    }) {
                        Icon(AppIcons.SwapHoriz, contentDescription = "Swap width and height")
                    }
                    NumberField("Height", height, hValid, Modifier.weight(1f)) { height = it }
                }
            }

            Section("Color mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ColorMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, ColorMode.entries.size),
                        ) {
                            Text(
                                when (m) {
                                    ColorMode.RGB -> "RGBA"
                                    ColorMode.GRAYSCALE -> "Gray"
                                    ColorMode.INDEXED -> "Indexed"
                                },
                            )
                        }
                    }
                }
            }

            Section("Background") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Background.entries.forEachIndexed { i, b ->
                        SegmentedButton(
                            selected = background == b,
                            onClick = { background = b },
                            shape = SegmentedButtonDefaults.itemShape(i, Background.entries.size),
                        ) {
                            Text(b.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            if (mode != ColorMode.GRAYSCALE && palettes.isNotEmpty()) {
                PaletteDropdown(palettes, palette) { palette = it }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = wValid && hValid,
                    onClick = { onCreate(NewSpriteSpec(name, w!!, h!!, mode, background, palette)) },
                ) {
                    Icon(AppIcons.Brush, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create & draw")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, valid: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        suffix = { Text("px") },
        isError = !valid,
        supportingText = { if (!valid) Text("1–$MAX_SIZE") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteDropdown(palettes: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Palette") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            palettes.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p) },
                    onClick = {
                        onSelect(p)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The canvas proportions at a glance. */
@Composable
private fun CanvasPreview(w: Int, h: Int, background: Background) {
    val box = 56.dp
    val longest = max(w, h).toFloat()
    val fill = when (background) {
        Background.TRANSPARENT -> MaterialTheme.colorScheme.surfaceContainerHighest
        Background.WHITE -> Color.White
        Background.BLACK -> Color.Black
    }
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = box * (w / longest), height = box * (h / longest))
                .background(fill, MaterialTheme.shapes.extraSmall)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
        )
    }
}
