# Besprited for Android

This folder turns Besprited into an Android app. It is the same editor as
on the desktop (same C++ code, menus, tools and file formats), running
through SDL3 with touch support added for phones.

## Getting the APK (no Android Studio needed)

The `Android` workflow (`.github/workflows/android.yml`) builds the APK on
GitHub:

1. Push this branch to your fork on GitHub (or open the **Actions** tab,
   pick **Android** and press **Run workflow**).
2. When the run finishes (about 20-30 minutes the first time, less later
   because the libraries are cached), open it and download
   **besprited-android** from the *Artifacts* section. It's a zip with the
   `.apk` inside.
3. Copy the `.apk` to the phone and open it. Android asks to allow installing
   apps from that source (the browser or Files app) the first time.

The app is built for 64-bit ARM phones (`arm64-v8a`, practically every
phone from the last several years), Android 9 or newer.

### Installing updates over the old version

Android only installs an update over an app signed with the same key. Out
of the box, each GitHub build is signed with a fresh throwaway key, so you
have to uninstall the old version first (your sprites in Pictures/Besprited
are kept, the app's preferences aren't).

To sign every build with your own key instead, create one once (needs
Java's `keytool`):

```
keytool -genkeypair -v -keystore besprited.keystore -alias besprited \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 besprited.keystore > besprited.keystore.b64
```

and add these repository secrets (Settings > Secrets and variables >
Actions): `ANDROID_KEYSTORE_B64` (contents of the `.b64` file),
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`besprited`) and
`ANDROID_KEY_PASSWORD`. Keep the keystore file safe; never commit it.

## Using it on a phone

The app runs in landscape and uses the whole screen.

| Touch                      | Does                                              |
|----------------------------|---------------------------------------------------|
| One finger                 | Like the left mouse button: draw, click, drag     |
| Two fingers, move          | Scroll the canvas (or any scrollable panel)       |
| Two fingers, pinch         | Zoom in/out around your fingers                   |
| Two-finger tap             | Undo                                              |
| Three-finger tap           | Redo                                              |
| Double tap                 | Double click                                      |
| Android back button/gesture| Esc (closes menus and dialogs)                    |

A stylus works too, with pressure if the phone supports it.

The touch bar along the side has buttons for common shortcuts (undo, redo,
tools); *View > Touchbar* shows or hides it. Things that need a keyboard
modifier (e.g. Shift for straight lines) need a Bluetooth keyboard for now.

If the interface looks too big or too small, change **Edit > Preferences >
UI Elements Scaling**. The first-run value is picked from the screen
density.

### Files

Besprited uses its own file browser. On first launch it asks for **All files
access** so it can open and save anywhere (Pictures, Download, ...). New
sprites are saved to `Pictures/Besprited` by default. Without that
permission it still works, but only inside its own folder
(`Android/data/com.veritaware.besprited/files`).

Images opened with Besprited from another app (*Open with* or *Share*) are
copied into `Pictures/Besprited/Opened` and edited there. *File > Share*
sends the current (saved) sprite to other apps.

## Building locally

Requirements: Linux or macOS, CMake 4.1+, Ninja, Git, Java 17, the Android
SDK (platform 35) and NDK r28 or newer.

```
git submodule update --init --recursive
ANDROID_NDK_HOME=/path/to/ndk android/build-native.sh
cd android
gradle assembleRelease     # or open this folder in Android Studio
```

`build-native.sh` downloads and builds the third-party libraries (pinned
versions, see `fetch-sources.sh`), builds Besprited as `libmain.so` and
puts everything Gradle needs under `android/build/`. It also applies the
small patches in `patches/` to submodules (they show as modified in
`git status`).

`android/build-native.sh --host-test` builds the same configuration for
the computer it runs on (with the Android code paths switched on), which
catches most build errors without an NDK.

## How it fits together

- `build-native.sh` builds SDL3, SDL3_image, libpng, libjpeg-turbo,
  freetype, giflib, tinyxml2 and pixman, then Besprited with
  `-DANDROID=ON`. curl and libarchive aren't used on Android (no network
  features; the extension format they serve is disabled anyway).
- `app/src/main/java/.../BespritedActivity.java` extends SDL's activity: it
  unpacks Besprited's `data/` folder from the APK, sets the folders the
  native code uses (environment variables, see `src/main/android_glue.cpp`),
  handles "Open with", sharing and the storage permission.
- In the C++ code, Android-specific parts are under `#ifdef ANDROID`:
  - `src/she/sdl3/sdl3_touch.cpp` turns fingers into mouse events and
    pan/zoom/undo gestures,
  - `src/she/sdl3/sdl3_display.cpp` uses the full screen and keeps the UI
    out of camera cutouts,
  - `src/she/sdl3/she.cpp` maps the back button to Esc and sets SDL's
    Android options,
  - `src/app/modules/gui.cpp` picks the first-run UI scale.
