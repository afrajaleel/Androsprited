// Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import java.io.File

/** Folders and settings shared by the home screen and the editor. */
object Storage {
    const val PREFS = "besprited"
    const val KEY_FULLSCREEN = "fullscreen"
    const val KEY_ASKED_STORAGE = "asked_storage"
    const val KEY_SKIP_HOME = "skip_home"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Intent for Android's "All files access" screen (Android 11+). */
    fun storageAccessIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /** Where sprites are opened from and saved to by default. */
    fun documentsFolder(context: Context): File {
        if (hasStorageAccess(context)) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Besprited",
            )
            if (dir.isDirectory || dir.mkdirs()) return dir
        }
        // Always available, no permission needed.
        val dir = context.getExternalFilesDir(null) ?: File(context.filesDir, "sprites")
        dir.mkdirs()
        return dir
    }

    /** Native config folder (XDG_CONFIG_HOME/besprited). */
    fun configFolder(context: Context): File = File(File(context.filesDir, "config"), "besprited")

    /** Copies a content:// file into documents/Opened; returns its path. */
    fun importUri(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme == "file") return uri.path
        return runCatching {
            val resolver = context.contentResolver
            val folder = File(documentsFolder(context), "Opened").apply { mkdirs() }
            val dest = uniqueFile(folder, displayName(resolver, uri))
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it, 64 * 1024) }
            } ?: return null
            dest.absolutePath
        }.getOrNull()
    }

    fun displayName(resolver: ContentResolver, uri: Uri): String {
        var name: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
        }
        if (name.isNullOrEmpty()) name = uri.lastPathSegment
        if (name.isNullOrEmpty()) name = "sprite.png"
        return name!!.replace('/', '_')
    }

    fun uniqueFile(folder: File, name: String): File {
        var file = File(folder, name)
        if (!file.exists()) return file
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (true) {
            file = File(folder, "$base ($i)$ext")
            if (!file.exists()) return file
            i++
        }
    }
}
