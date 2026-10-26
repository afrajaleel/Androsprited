// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.veritaware.besprited.home.HomeState

interface SettingsActions {
    fun back()
    fun setFullscreen(value: Boolean)
    fun setSkipHome(value: Boolean)
    fun requestStorageAccess()
    fun openSource()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: HomeState, version: String, actions: SettingsActions) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = actions::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Header("Editor")
            SwitchRow(
                AppIcons.Fullscreen,
                "Fullscreen editor",
                "Hide the status and navigation bars while drawing. Also in View › Toggle Fullscreen.",
                state.fullscreen,
                actions::setFullscreen,
            )
            SwitchRow(
                Icons.Filled.Home,
                "Skip this screen",
                "Open the editor straight away when you launch Besprited.",
                state.skipHome,
                actions::setSkipHome,
            )
            Text(
                "UI size, touch bar, layouts and themes are in the editor under Edit › Preferences and View.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Header("Storage")
            ListItem(
                leadingContent = {
                    Icon(if (state.hasStorageAccess) Icons.Filled.CheckCircle else AppIcons.Folder, contentDescription = null)
                },
                headlineContent = { Text("File access") },
                supportingContent = {
                    Text(
                        if (state.hasStorageAccess) {
                            "Allowed — sprites can be saved anywhere."
                        } else {
                            "Not allowed — Besprited can only use its own folder."
                        },
                    )
                },
                trailingContent = {
                    if (!state.hasStorageAccess) {
                        TextButton(onClick = actions::requestStorageAccess) { Text("Allow") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListItem(
                leadingContent = { Icon(AppIcons.FolderOpen, contentDescription = null) },
                headlineContent = { Text("Sprites folder") },
                supportingContent = { Text(state.folder) },
            )

            Header("About")
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("Besprited for Android") },
                supportingContent = { Text("Version $version · GPL v2") },
            )
            ListItem(
                leadingContent = { Icon(AppIcons.Code, contentDescription = null) },
                headlineContent = { Text("Source code") },
                supportingContent = { Text("github.com/veritaware/Besprited") },
                modifier = Modifier.clickable(onClick = actions::openSource),
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
