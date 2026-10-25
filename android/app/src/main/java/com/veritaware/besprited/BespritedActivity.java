// Besprited | Copyright (C) 2026 Veritaware
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 2 as
// published by the Free Software Foundation.

package com.veritaware.besprited;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.system.Os;
import android.util.DisplayMetrics;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Starts Besprited's native code (libmain.so) through SDL.
 *
 * Before the native code runs, this copies Besprited's "data" folder out of
 * the APK and tells the native side where things live through environment
 * variables (read in src/main/android_glue.cpp):
 *   BESPRITED_DATA_DIR    folder containing "data" (the app's private files)
 *   BESPRITED_STORAGE_DIR default folder for the user's sprites
 *   BESPRITED_UI_SCALE    suggested UI scale for this screen
 *   HOME, XDG_CONFIG_HOME where besprited.ini and other settings are kept
 *   TMPDIR                temporary files
 */
public class BespritedActivity extends SDLActivity {
    private static final String TAG = "Besprited";
    private static final String PREFS = "besprited";

    /** File passed by another app ("Open with") when Besprited starts. */
    private String mStartupFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            prepareFiles();
        } catch (Exception e) {
            Log.e(TAG, "Could not prepare Besprited's files", e);
        }

        // Handle "Open with" here: SDL would otherwise pass the content://
        // URI's path, which isn't a real file.
        Intent intent = getIntent();
        mStartupFile = importIncomingFile(intent);
        if (intent != null && intent.getData() != null) {
            Intent copy = new Intent(intent);
            copy.setData(null);
            setIntent(copy);
        }

        super.onCreate(savedInstanceState);
        maybeAskForStorageAccess();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A file was opened with Besprited while it was already running.
        String path = importIncomingFile(intent);
        if (path != null) {
            SDLActivity.onNativeDropFile(path);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Besprited's native code keeps global state, so it can't be started
        // twice in the same process. End the process when the app closes;
        // the next launch starts fresh.
        if (isFinishing()) {
            System.exit(0);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "main" };
    }

    @Override
    protected String[] getArguments() {
        if (mStartupFile != null) {
            return new String[] { mStartupFile };
        }
        return new String[0];
    }

    // ------------------------------------------------------------------
    // Files and folders

    private void prepareFiles() throws Exception {
        File files = getFilesDir();
        File data = new File(files, "data");
        File stamp = new File(files, "data.version");

        // Copy assets/data to the app's storage after every install/update.
        long updated = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        String version = String.valueOf(updated);
        if (!data.isDirectory() || !version.equals(readText(stamp))) {
            Log.i(TAG, "Extracting data files");
            deleteRecursively(data);
            copyAssets(getAssets(), "data", data);
            writeText(stamp, version);
        }

        File config = new File(files, "config");
        config.mkdirs();

        Os.setenv("BESPRITED_DATA_DIR", files.getAbsolutePath(), true);
        Os.setenv("BESPRITED_STORAGE_DIR", documentsFolder().getAbsolutePath(), true);
        Os.setenv("BESPRITED_UI_SCALE", String.valueOf(suggestedUiScale()), true);
        Os.setenv("HOME", files.getAbsolutePath(), true);
        Os.setenv("XDG_CONFIG_HOME", config.getAbsolutePath(), true);
        Os.setenv("TMPDIR", getCacheDir().getAbsolutePath(), true);
    }

    /** Where sprites are opened from and saved to by default. */
    private File documentsFolder() {
        if (hasStorageAccess()) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Besprited");
            if (dir.isDirectory() || dir.mkdirs()) {
                return dir;
            }
        }
        // Always available, no permission needed; visible to file managers
        // under Android/data/<package>/files.
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = new File(getFilesDir(), "sprites");
        }
        dir.mkdirs();
        return dir;
    }

    /**
     * Besprited's UI is drawn in pixels and scaled up by a whole number. On
     * a phone, roughly the screen density keeps buttons big enough to tap
     * while leaving room for the canvas. Changeable in Preferences.
     */
    private int suggestedUiScale() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int scale = Math.round(metrics.density * 0.8f);
        return Math.max(1, Math.min(4, scale));
    }

    private static void copyAssets(AssetManager assets, String path, File dest) throws IOException {
        String[] children = assets.list(path);
        if (children != null && children.length > 0) {
            dest.mkdirs();
            for (String child : children) {
                copyAssets(assets, path + "/" + child, new File(dest, child));
            }
            return;
        }
        try (InputStream in = assets.open(path)) {
            File parent = dest.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (OutputStream out = new FileOutputStream(dest)) {
                copy(in, out);
            }
        } catch (IOException e) {
            // An empty asset folder can't be opened as a file; skip it.
            Log.w(TAG, "Skipped asset " + path + ": " + e.getMessage());
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static String readText(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            copy(in, out);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    // Opening files from other apps

    /**
     * Copies a file another app opened with Besprited into the documents
     * folder (so it can be edited and saved there) and returns its path, or
     * null if the intent carries no file.
     */
    private String importIncomingFile(Intent intent) {
        if (intent == null) {
            return null;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action)) {
            return null;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) {
            return null;
        }
        if ("file".equals(uri.getScheme())) {
            return uri.getPath();
        }
        try {
            ContentResolver resolver = getContentResolver();
            String name = displayName(resolver, uri);
            File folder = new File(documentsFolder(), "Opened");
            folder.mkdirs();
            File dest = uniqueFile(folder, name);
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(dest)) {
                if (in == null) {
                    return null;
                }
                copy(in, out);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Could not open " + uri, e);
            return null;
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.DISPLAY_NAME },
                                            null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty()) {
            name = "sprite.png";
        }
        return name.replace('/', '_');
    }

    private static File uniqueFile(File folder, String name) {
        File file = new File(folder, name);
        if (!file.exists()) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            file = new File(folder, base + " (" + i + ")" + ext);
            if (!file.exists()) {
                return file;
            }
        }
    }

    // ------------------------------------------------------------------
    // Sharing (File > Share), called from native code

    /** Called from _AndroidShareFile() in src/main/android_glue.cpp. */
    public void shareFile(final String path) {
        runOnUiThread(() -> {
            try {
                File file = new File(path);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
                String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
                String mime = ext != null
                    ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase())
                    : null;
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime != null ? mime : "application/octet-stream");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, getString(R.string.share_title)));
            } catch (Exception e) {
                Log.e(TAG, "Could not share " + path, e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Storage permission

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    /** Asks once (on first launch) for access to the shared folders. */
    private void maybeAskForStorageAccess() {
        if (hasStorageAccess()) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean("asked_storage", false)) {
            return;
        }
        prefs.edit().putBoolean("asked_storage", true).apply();

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.storage_title)
            .setMessage(R.string.storage_message)
            .setPositiveButton(R.string.storage_allow, (dialog, which) -> requestStorageAccess())
            .setNegativeButton(R.string.storage_later, null)
            .show();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                           Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
        }
    }
}
