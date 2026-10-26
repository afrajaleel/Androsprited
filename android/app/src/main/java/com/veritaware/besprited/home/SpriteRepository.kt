// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited.home

import android.content.Context
import android.graphics.BitmapFactory
import com.veritaware.besprited.Storage
import java.io.File

data class Sprite(
    val file: File,
    val modified: Long,
    val info: SpriteInfo?,
) {
    val name: String get() = file.nameWithoutExtension
    val extension: String get() = file.extension.lowercase()
    val isAseprite: Boolean get() = extension == "ase" || extension == "aseprite"
    val isAnimated: Boolean get() = (info?.frames ?: 1) > 1 || extension == "gif"
}

/** Finds, creates and manages the user's sprites on disk. */
class SpriteRepository(private val context: Context) {

    val documentsFolder: File get() = Storage.documentsFolder(context)

    /** Sprites in the documents folder plus the editor's recent files, newest first. */
    fun loadSprites(): List<Sprite> {
        val files = LinkedHashMap<String, File>()
        for (path in recentFiles()) {
            val f = File(path)
            if (f.isFile) files[f.canonicalPathOrSelf()] = f
        }
        val docs = documentsFolder
        for (dir in listOf(docs, File(docs, "Opened"))) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() in EXTENSIONS) files.putIfAbsent(f.canonicalPathOrSelf(), f)
            }
        }
        return files.values
            .map { Sprite(it, it.lastModified(), readInfo(it)) }
            .sortedByDescending { it.modified }
    }

    /** The editor's File > Open Recent list, from besprited.ini. */
    fun recentFiles(): List<String> {
        val ini = File(Storage.configFolder(context), "besprited.ini")
        if (!ini.isFile) return emptyList()
        var inSection = false
        val entries = sortedMapOf<String, String>()
        runCatching {
            ini.forEachLine { raw ->
                val line = raw.trim()
                if (line.startsWith("[")) {
                    inSection = line.equals("[RecentFiles]", ignoreCase = true)
                } else if (inSection) {
                    val eq = line.indexOf('=')
                    if (eq > 0) entries[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
                }
            }
        }
        // Filename00 is the most recent.
        return entries.values.filter { it.isNotEmpty() }
    }

    private fun readInfo(file: File): SpriteInfo? {
        val ext = file.extension.lowercase()
        if (ext == "ase" || ext == "aseprite") return AseFile.readInfo(file)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0) SpriteInfo(opts.outWidth, opts.outHeight, 1, null) else null
    }

    /** Palette names bundled with the app (data/palettes/<name>.gpl). */
    fun paletteNames(): List<String> =
        runCatching {
            context.assets.list("data/palettes").orEmpty()
                .filter { it.endsWith(".gpl") }
                .map { it.removeSuffix(".gpl") }
                .sorted()
        }.getOrDefault(emptyList())

    private fun loadPalette(name: String): IntArray =
        runCatching { context.assets.open("data/palettes/$name.gpl").use { AseFile.readGpl(it) } }
            .getOrDefault(IntArray(0))

    fun createSprite(spec: NewSpriteSpec): File {
        val base = spec.name.trim().ifEmpty { "Sprite" }.replace('/', '_')
        val file = Storage.uniqueFile(documentsFolder, "$base.aseprite")
        val palette = if (spec.colorMode == ColorMode.GRAYSCALE) {
            IntArray(256) { (0xFF shl 24) or (it shl 16) or (it shl 8) or it }
        } else {
            loadPalette(spec.palette)
        }
        AseFile.writeNew(file, spec.width, spec.height, spec.colorMode, spec.background, palette)
        return file
    }

    fun rename(sprite: Sprite, newName: String): File? {
        val clean = newName.trim().replace('/', '_')
        if (clean.isEmpty()) return null
        val dest = File(sprite.file.parentFile, "$clean.${sprite.file.extension}")
        if (dest.exists()) return null
        return if (sprite.file.renameTo(dest)) dest else null
    }

    fun delete(sprite: Sprite): Boolean = sprite.file.delete()

    companion object {
        val EXTENSIONS = setOf("ase", "aseprite", "png", "gif", "jpg", "jpeg", "bmp", "pcx", "tga", "webp", "flc", "fli")
    }
}

data class NewSpriteSpec(
    val name: String,
    val width: Int,
    val height: Int,
    val colorMode: ColorMode,
    val background: Background,
    val palette: String,
)

private fun File.canonicalPathOrSelf(): String = runCatching { canonicalPath }.getOrDefault(absolutePath)
