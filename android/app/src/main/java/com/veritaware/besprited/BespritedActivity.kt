// Besprited | Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.libsdl.app.SDLActivity
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * The editor: starts Besprited's native code (libmain.so) through SDL.
 *
 * Runs in its own process (":editor") because the native code keeps global
 * state and can't start twice in one process; closing the editor ends that
 * process and returns to the home screen.
 *
 * Before the native code runs, this copies the "data" folder out of the APK
 * and sets the environment read by src/main/android_glue.cpp:
 *   BESPRITED_DATA_DIR, BESPRITED_STORAGE_DIR, BESPRITED_UI_SCALE,
 *   BESPRITED_FULLSCREEN, HOME, XDG_CONFIG_HOME, TMPDIR.
 */
class BespritedActivity : SDLActivity() {

    /** File to open when the native code starts. */
    private var startupFile: String? = null
    private var fullscreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            prepareFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Could not prepare Besprited's files", e)
        }

        // SDL would pass a content:// URI's path, which isn't a real file.
        startupFile = intent?.getStringExtra(EXTRA_PATH) ?: importIncomingFile(intent)
        intent?.takeIf { it.data != null }?.let { setIntent(Intent(it).setData(null)) }

        super.onCreate(savedInstanceState)
        maybeAskForStorageAccess()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A file was opened while the editor was already running.
        val path = intent.getStringExtra(EXTRA_PATH) ?: importIncomingFile(intent)
        if (path != null) SDLActivity.onNativeDropFile(path)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) System.exit(0)
    }

    override fun getLibraries(): Array<String> = arrayOf("SDL3", "main")

    override fun getArguments(): Array<String> = startupFile?.let { arrayOf(it) } ?: emptyArray()

    // ------------------------------------------------------------------
    // Files and folders

    private fun prepareFiles() {
        val files = filesDir
        val data = File(files, "data")
        val stamp = File(files, "data.version")

        // Copy assets/data to the app's storage after every install/update.
        val version = packageManager.getPackageInfo(packageName, 0).lastUpdateTime.toString()
        if (!data.isDirectory || version != stamp.readTextOrEmpty()) {
            Log.i(TAG, "Extracting data files")
            data.deleteRecursively()
            copyAssets(assets, "data", data)
            stamp.writeText(version)
        }

        val config = File(files, "config").apply { mkdirs() }

        Os.setenv("BESPRITED_DATA_DIR", files.absolutePath, true)
        Os.setenv("BESPRITED_STORAGE_DIR", Storage.documentsFolder(this).absolutePath, true)
        Os.setenv("BESPRITED_UI_SCALE", suggestedUiScale().toString(), true)
        Os.setenv("HOME", files.absolutePath, true)
        Os.setenv("XDG_CONFIG_HOME", config.absolutePath, true)
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        fullscreen = Storage.prefs(this).getBoolean(Storage.KEY_FULLSCREEN, true)
        Os.setenv("BESPRITED_FULLSCREEN", if (fullscreen) "1" else "0", true)
    }

    /**
     * Besprited's UI is drawn in pixels and scaled by a whole number; a bit
     * more than the screen density keeps it readable on a phone. Only the
     * first-run value - Edit > Preferences changes it afterwards.
     */
    private fun suggestedUiScale(): Int =
        Math.round(resources.displayMetrics.density * 1.15f).coerceIn(1, 4)

    private fun copyAssets(assets: AssetManager, path: String, dest: File) {
        val children = assets.list(path)
        if (!children.isNullOrEmpty()) {
            dest.mkdirs()
            for (child in children) copyAssets(assets, "$path/$child", File(dest, child))
            return
        }
        try {
            assets.open(path).use { input ->
                dest.parentFile?.mkdirs()
                dest.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
        } catch (e: IOException) {
            // An empty asset folder can't be opened as a file.
            Log.w(TAG, "Skipped asset $path: ${e.message}")
        }
    }

    // ------------------------------------------------------------------
    // Opening files from other apps

    private fun importIncomingFile(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        val uri: Uri? = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
        return Storage.importUri(this, uri)
    }

    // ------------------------------------------------------------------
    // Android's file picker, called from native code (android_glue.cpp).
    // The result goes back through nativeFilesPicked() (empty if cancelled).

    /** Any "open a file" dialog: picks one file and copies it locally. */
    fun pickFile() = startPicker(REQUEST_PICK_FILE, "*/*")

    /** Preferences > Theme > Import: picks a theme .zip and installs it. */
    fun pickTheme() = startPicker(REQUEST_PICK_THEME, "application/zip")

    private fun startPicker(request: Int, mime: String) = runOnUiThread {
        try {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
            if (mime != "*/*") {
                // Some file managers label zips differently.
                picker.putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf(mime, "application/x-zip-compressed", "application/octet-stream"),
                )
            }
            startActivityForResult(picker, request)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the file picker", e)
            nativeFilesPicked(emptyArray())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_PICK_FILE && requestCode != REQUEST_PICK_THEME) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val uri = if (resultCode == RESULT_OK) data?.data else null
        if (uri == null) {
            nativeFilesPicked(emptyArray())
            return
        }
        // Copying can take a moment for big or cloud files.
        Thread {
            val path = if (requestCode == REQUEST_PICK_THEME) installTheme(uri) else Storage.importUri(this, uri)
            nativeFilesPicked(path?.let { arrayOf(it) } ?: emptyArray())
        }.start()
    }

    /**
     * Unzips a theme into the user theme folder and returns its folder.
     * Accepts zips with the files at the top level or inside one folder.
     */
    private fun installTheme(uri: Uri): String? = try {
        val skins = File(Storage.configFolder(this), "data/skins").apply { mkdirs() }
        var name = Storage.displayName(contentResolver, uri).removeSuffix(".zip").removeSuffix(".ZIP")
        val staging = File(cacheDir, "theme-import")
        staging.deleteRecursively()
        staging.mkdirs()
        val stagingPath = staging.canonicalPath + File.separator

        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(staging, entry.name)
                    // Refuse entries that would land outside the folder.
                    if (!out.canonicalPath.startsWith(stagingPath)) continue
                    if (entry.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }

        // A single top-level folder holding theme.xml is the theme itself.
        var root = staging
        val top = staging.listFiles()
        if (top != null && top.size == 1 && top[0].isDirectory && !File(staging, "theme.xml").exists()) {
            root = top[0]
            name = top[0].name
        }
        if (!File(root, "theme.xml").exists()) {
            Log.w(TAG, "No theme.xml in $uri")
            staging.deleteRecursively()
            null
        } else {
            val dest = File(skins, name.replace('/', '_'))
            dest.deleteRecursively()
            if (root.renameTo(dest)) {
                staging.deleteRecursively()
                dest.absolutePath
            } else {
                Log.w(TAG, "Could not move theme into $dest")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not install theme $uri", e)
        null
    }

    // ------------------------------------------------------------------
    // Fullscreen (View > Fullscreen), called from native code

    fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        Storage.prefs(this).edit().putBoolean(Storage.KEY_FULLSCREEN, enabled).apply()
        // Run after SDL has applied its own window style for the change.
        runOnUiThread { window.decorView.postDelayed(::applySystemBars, 150) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    /** Fullscreen hides the system bars and draws over the camera cutout. */
    private fun applySystemBars() {
        val attrs = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            attrs.layoutInDisplayCutoutMode = if (fullscreen) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = attrs
            window.setDecorFitsSystemWindows(!fullscreen)
            window.insetsController?.let { controller ->
                val bars = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                if (fullscreen) {
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(bars)
                } else {
                    controller.show(bars)
                }
            }
        } else {
            attrs.layoutInDisplayCutoutMode = if (fullscreen) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = attrs
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (fullscreen) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    // ------------------------------------------------------------------
    // Sharing (File > Share), called from native code

    fun shareFile(path: String) = runOnUiThread { shareFile(this, File(path)) }

    // ------------------------------------------------------------------
    // On-screen keyboard, called from native code (she::set_input_rect)

    /**
     * SDL isn't told when the user closes the keyboard (back, swipe), so it
     * would think it's still shown and not show it again. Tell SDL it's
     * hidden when it is, right before a text field asks for it.
     */
    fun syncKeyboardState() {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        if (insets?.isVisible(WindowInsetsCompat.Type.ime()) != true) {
            SDLActivity.onNativeScreenKeyboardHidden()
        }
    }

    // ------------------------------------------------------------------
    // Storage permission

    /** Asks once, on first launch, for access to the shared folders. */
    private fun maybeAskForStorageAccess() {
        if (Storage.hasStorageAccess(this)) return
        val prefs = Storage.prefs(this)
        if (prefs.getBoolean(Storage.KEY_ASKED_STORAGE, false)) return
        prefs.edit().putBoolean(Storage.KEY_ASKED_STORAGE, true).apply()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.storage_title)
            .setMessage(R.string.storage_message)
            .setPositiveButton(R.string.storage_allow) { _, _ -> requestStorageAccess() }
            .setNegativeButton(R.string.storage_later, null)
            .show()
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Storage.storageAccessIntent(this))
            } catch (e: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
                1,
            )
        }
    }

    companion object {
        private const val TAG = "Besprited"
        private const val REQUEST_PICK_FILE = 0x5e17
        private const val REQUEST_PICK_THEME = 0x5e18

        /** Absolute path of a sprite to open. */
        const val EXTRA_PATH = "com.veritaware.besprited.PATH"

        /** Implemented in src/main/android_glue.cpp. */
        @JvmStatic
        external fun nativeFilesPicked(paths: Array<String>)

        fun open(context: Context, path: String? = null) {
            val intent = Intent(context, BespritedActivity::class.java)
            if (path != null) intent.putExtra(EXTRA_PATH, path)
            context.startActivity(intent)
        }

        fun shareFile(context: Context, file: File) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
                val mime = ext?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
                val send = Intent(Intent.ACTION_SEND)
                    .setType(mime ?: "application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title)))
            } catch (e: Exception) {
                Log.e(TAG, "Could not share $file", e)
            }
        }
    }
}

private fun File.readTextOrEmpty(): String = runCatching { readText() }.getOrDefault("")
