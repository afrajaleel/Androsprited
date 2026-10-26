// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veritaware.besprited.BespritedActivity
import com.veritaware.besprited.BuildConfig
import com.veritaware.besprited.Storage
import com.veritaware.besprited.home.ui.BespritedTheme
import com.veritaware.besprited.home.ui.HomeActions
import com.veritaware.besprited.home.ui.HomeScreen
import com.veritaware.besprited.home.ui.NewSpriteSheet
import com.veritaware.besprited.home.ui.SettingsActions
import com.veritaware.besprited.home.ui.SettingsScreen

/** The launcher screen: recent sprites, new sprite, open, settings. */
class HomeActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null && intent?.action == Intent.ACTION_MAIN &&
            Storage.prefs(this).getBoolean(Storage.KEY_SKIP_HOME, false)
        ) {
            BespritedActivity.open(this)
        }

        setContent {
            BespritedTheme { App() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up files saved in the editor and permission changes.
        viewModel.refresh()
    }

    @Composable
    private fun App() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        var inSettings by rememberSaveable { mutableStateOf(false) }
        var showNewSprite by rememberSaveable { mutableStateOf(false) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.import(uri) { BespritedActivity.open(this, it) }
        }

        val homeActions = remember {
            object : HomeActions {
                override fun newSprite() { showNewSprite = true }
                override fun openFile() = picker.launch(arrayOf("image/*", "application/octet-stream"))
                override fun blankEditor() = BespritedActivity.open(this@HomeActivity)
                override fun open(sprite: Sprite) = BespritedActivity.open(this@HomeActivity, sprite.file.absolutePath)
                override fun share(sprite: Sprite) = BespritedActivity.shareFile(this@HomeActivity, sprite.file)
                override fun rename(sprite: Sprite, name: String) = viewModel.rename(sprite, name)
                override fun delete(sprite: Sprite) = viewModel.delete(sprite)
                override fun setFilter(filter: SpriteFilter) = viewModel.setFilter(filter)
                override fun requestStorageAccess() = this@HomeActivity.requestStorageAccess()
                override fun openSettings() { inSettings = true }
            }
        }
        val settingsActions = remember {
            object : SettingsActions {
                override fun back() { inSettings = false }
                override fun setFullscreen(value: Boolean) = viewModel.setFullscreen(value)
                override fun setSkipHome(value: Boolean) = viewModel.setSkipHome(value)
                override fun requestStorageAccess() = this@HomeActivity.requestStorageAccess()
                override fun openSource() {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/veritaware/Besprited")))
                    }
                }
            }
        }

        BackHandler(enabled = inSettings) { inSettings = false }

        AnimatedContent(
            targetState = inSettings,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { settings ->
            if (settings) {
                SettingsScreen(state, BuildConfig.VERSION_NAME, settingsActions)
            } else {
                HomeScreen(state, homeActions)
            }
        }

        if (showNewSprite) {
            NewSpriteSheet(
                palettes = state.palettes,
                onDismiss = { showNewSprite = false },
                onCreate = { spec ->
                    showNewSprite = false
                    viewModel.createSprite(spec) { BespritedActivity.open(this, it.absolutePath) }
                },
            )
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { startActivity(Storage.storageAccessIntent(this)) }
                .onFailure { runCatching { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1,
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        viewModel.refresh()
    }
}
