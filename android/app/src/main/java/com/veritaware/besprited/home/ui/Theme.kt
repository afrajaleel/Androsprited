// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Lime from the launcher icon on the icon's charcoal.
private val Dark = darkColorScheme(
    primary = Color(0xFFB4DE4C),
    onPrimary = Color(0xFF1E2B00),
    primaryContainer = Color(0xFF3A5100),
    onPrimaryContainer = Color(0xFFD0F774),
    secondary = Color(0xFFC1CAAA),
    onSecondary = Color(0xFF2B331C),
    secondaryContainer = Color(0xFF414A31),
    onSecondaryContainer = Color(0xFFDDE6C5),
    tertiary = Color(0xFF9FCFCB),
    onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF1E4E4B),
    onTertiaryContainer = Color(0xFFBBECE7),
    background = Color(0xFF16161B),
    onBackground = Color(0xFFE4E3E9),
    surface = Color(0xFF16161B),
    onSurface = Color(0xFFE4E3E9),
    surfaceVariant = Color(0xFF2E2E36),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF111115),
    surfaceContainerLow = Color(0xFF1E1E24),
    surfaceContainer = Color(0xFF232329),
    surfaceContainerHigh = Color(0xFF2B2B33),
    surfaceContainerHighest = Color(0xFF35353E),
    outline = Color(0xFF8F8F9A),
    outlineVariant = Color(0xFF45454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4D6A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDF06C),
    onPrimaryContainer = Color(0xFF141F00),
    secondary = Color(0xFF596249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE6C6),
    onSecondaryContainer = Color(0xFF171E0A),
    tertiary = Color(0xFF386663),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBECE7),
    onTertiaryContainer = Color(0xFF00201E),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E2EA),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3F8),
    surfaceContainer = Color(0xFFEEEDF2),
    surfaceContainerHigh = Color(0xFFE8E7ED),
    surfaceContainerHighest = Color(0xFFE2E1E7),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C6D0),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BespritedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        content = content,
    )
}
