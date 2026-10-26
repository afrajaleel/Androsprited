// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritaware.besprited.home.HomeState
import com.veritaware.besprited.home.Sprite
import com.veritaware.besprited.home.SpriteFilter

/** What the home screen asks its host to do. */
interface HomeActions {
    fun newSprite()
    fun openFile()
    fun blankEditor()
    fun open(sprite: Sprite)
    fun share(sprite: Sprite)
    fun rename(sprite: Sprite, name: String)
    fun delete(sprite: Sprite)
    fun setFilter(filter: SpriteFilter)
    fun requestStorageAccess()
    fun openSettings()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var renaming by remember { mutableStateOf<Sprite?>(null) }
    var deleting by remember { mutableStateOf<Sprite?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Besprited", fontWeight = FontWeight.Bold)
                        if (scroll.state.collapsedFraction < 0.5f) {
                            Text(
                                "Pixel art & animation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions::newSprite,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New sprite") },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth { QuickActions(actions) }

            if (!state.hasStorageAccess && !state.loading) {
                fullWidth { StorageBanner(actions::requestStorageAccess) }
            }

            val latest = state.sprites.firstOrNull()
            if (latest != null) {
                fullWidth { ContinueCard(latest, onOpen = { actions.open(latest) }) }
            }

            fullWidth { SectionHeader(state, actions) }

            when {
                state.loading -> fullWidth {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.visibleSprites.isEmpty() -> fullWidth { EmptyState(state.sprites.isEmpty(), actions) }
                else -> items(state.visibleSprites, key = { it.file.absolutePath }) { sprite ->
                    SpriteCard(
                        sprite = sprite,
                        onOpen = { actions.open(sprite) },
                        onShare = { actions.share(sprite) },
                        onRename = { renaming = sprite },
                        onDelete = { deleting = sprite },
                    )
                }
            }
        }
    }

    renaming?.let { sprite ->
        RenameDialog(
            sprite,
            onDismiss = { renaming = null },
            onConfirm = {
                actions.rename(sprite, it)
                renaming = null
            },
        )
    }
    deleting?.let { sprite ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete “${sprite.file.name}”?") },
            text = { Text("The file is removed from your device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.delete(sprite)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun QuickActions(actions: HomeActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(AppIcons.FolderOpen, "Open file", "Gallery, Downloads, Drive…", Modifier.weight(1f), actions::openFile)
        QuickAction(AppIcons.Brush, "Editor", "Start with an empty workspace", Modifier.weight(1f), actions::blankEditor)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(icon, contentDescription = null, Modifier.padding(8.dp).size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun StorageBanner(onAllow: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Save anywhere",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Allow file access to keep sprites in Pictures/Besprited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = onAllow) { Text("Allow") }
        }
    }
}

@Composable
private fun ContinueCard(sprite: Sprite, onOpen: () -> Unit) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SpriteThumbnail(
                sprite,
                Modifier.size(104.dp).clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    sprite.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    details(sprite),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Icon(AppIcons.Brush, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keep drawing")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(state: HomeState, actions: HomeActions) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("Your sprites", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpriteFilter.entries.toList()) { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { actions.setFilter(f) },
                    label = {
                        Text(
                            when (f) {
                                SpriteFilter.ALL -> "All"
                                SpriteFilter.ASEPRITE -> "Aseprite"
                                SpriteFilter.ANIMATED -> "Animated"
                                SpriteFilter.IMAGES -> "Images"
                            },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpriteCard(
    sprite: Sprite,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    ) {
        Box {
            SpriteThumbnail(sprite, Modifier.fillMaxWidth().aspectRatio(1f))
            if (sprite.isAnimated) {
                Badge(AppIcons.Movie, "${sprite.info?.frames ?: ""}", Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sprite.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    details(sprite),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More for ${sprite.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Open", AppIcons.Brush) { menu = false; onOpen() }
                    MenuItem("Share", Icons.Filled.Share) { menu = false; onShare() }
                    MenuItem("Rename", Icons.Filled.Edit) { menu = false; onRename() }
                    MenuItem("Delete", Icons.Filled.Delete) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)

@Composable
private fun Badge(icon: ImageVector, text: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = "Animated", Modifier.size(14.dp))
            if (text.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(text, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyState(nothingAtAll: Boolean, actions: HomeActions) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Icon(
                AppIcons.Brush,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(20.dp).size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (nothingAtAll) "No sprites yet" else "Nothing matches this filter",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (nothingAtAll) "Create one, or open an image from your phone." else "Try another filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (nothingAtAll) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = actions::newSprite) { Text("Create a sprite") }
        }
    }
}

@Composable
private fun RenameDialog(sprite: Sprite, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(sprite.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename sprite") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(64) },
                singleLine = true,
                suffix = { Text(".${sprite.file.extension}") },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && name != sprite.name, onClick = { onConfirm(name) }) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun details(sprite: Sprite): String {
    val parts = mutableListOf<String>()
    sprite.info?.let { parts += "${it.width}×${it.height}" }
    parts += DateUtils.getRelativeTimeSpanString(
        sprite.modified,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    return parts.joinToString(" · ")
}
