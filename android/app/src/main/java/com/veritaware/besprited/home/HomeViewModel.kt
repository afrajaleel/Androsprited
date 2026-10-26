// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veritaware.besprited.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SpriteFilter { ALL, ASEPRITE, ANIMATED, IMAGES }

data class HomeState(
    val loading: Boolean = true,
    val sprites: List<Sprite> = emptyList(),
    val filter: SpriteFilter = SpriteFilter.ALL,
    val hasStorageAccess: Boolean = false,
    val folder: String = "",
    val palettes: List<String> = emptyList(),
    val fullscreen: Boolean = true,
    val skipHome: Boolean = false,
) {
    val visibleSprites: List<Sprite>
        get() = when (filter) {
            SpriteFilter.ALL -> sprites
            SpriteFilter.ASEPRITE -> sprites.filter { it.isAseprite }
            SpriteFilter.ANIMATED -> sprites.filter { it.isAnimated }
            SpriteFilter.IMAGES -> sprites.filter { !it.isAseprite }
        }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SpriteRepository(app)
    private val prefs = Storage.prefs(app)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val (sprites, palettes, folder) = withContext(Dispatchers.IO) {
                Triple(repo.loadSprites(), repo.paletteNames(), repo.documentsFolder.absolutePath)
            }
            _state.update {
                it.copy(
                    loading = false,
                    sprites = sprites,
                    palettes = palettes,
                    folder = folder,
                    hasStorageAccess = Storage.hasStorageAccess(getApplication()),
                    fullscreen = prefs.getBoolean(Storage.KEY_FULLSCREEN, true),
                    skipHome = prefs.getBoolean(Storage.KEY_SKIP_HOME, false),
                )
            }
        }
    }

    fun setFilter(filter: SpriteFilter) = _state.update { it.copy(filter = filter) }

    fun setFullscreen(value: Boolean) {
        // commit(): the editor runs in another process and reads this at start.
        prefs.edit().putBoolean(Storage.KEY_FULLSCREEN, value).commit()
        _state.update { it.copy(fullscreen = value) }
    }

    fun setSkipHome(value: Boolean) {
        prefs.edit().putBoolean(Storage.KEY_SKIP_HOME, value).commit()
        _state.update { it.copy(skipHome = value) }
    }

    fun createSprite(spec: NewSpriteSpec, onCreated: (File) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { repo.createSprite(spec) }.getOrNull() }
            if (file != null) onCreated(file)
            refresh()
        }
    }

    fun import(uri: Uri, onImported: (String) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { Storage.importUri(getApplication(), uri) }
            if (path != null) onImported(path)
        }
    }

    fun rename(sprite: Sprite, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.rename(sprite, name) }
            refresh()
        }
    }

    fun delete(sprite: Sprite) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(sprite) }
            refresh()
        }
    }
}
