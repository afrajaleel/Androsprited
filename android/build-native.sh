#!/usr/bin/env bash
# Builds the native part of the Android app:
#   1. fetches third-party sources (fetch-sources.sh)
#   2. builds the "gen" code generator for the build machine
#   3. cross-compiles SDL3, SDL3_image, libpng, libjpeg-turbo, freetype,
#      giflib, tinyxml2 and pixman for Android
#   4. cross-compiles Besprited itself into libmain.so
#   5. stages libmain.so, libSDL3.so, SDL's Java glue and the data/ folder
#      under android/build/ where Gradle picks them up
#
# Usage:
#   ANDROID_NDK_HOME=/path/to/ndk android/build-native.sh
#
# Environment:
#   ANDROID_NDK_HOME  NDK location (falls back to ANDROID_NDK_LATEST_HOME,
#                     ANDROID_NDK_ROOT)
#   ANDROID_ABI       default arm64-v8a
#   ANDROID_API       minimum Android API level, default 28 (Android 9)
#   BUILD_TYPE        default Release
#
# "--host-test" builds everything for the build machine instead, with the
# Android code paths switched on, to catch build errors without an NDK.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
BUILD="$HERE/build"
SRC="$BUILD/src"
ABI="${ANDROID_ABI:-arm64-v8a}"
API="${ANDROID_API:-28}"
BUILD_TYPE="${BUILD_TYPE:-Release}"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 4)}"
MODE=android
[ "${1:-}" = "--host-test" ] && MODE=host-test

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

# ---------------------------------------------------------------------------
# Sources and patches

"$HERE/fetch-sources.sh"

log "Applying Android patches to submodules"
for patch in "$HERE"/patches/*.patch; do
  [ -e "$patch" ] || continue
  # Patch file names are "<submodule dir, with + for />--<name>.patch"
  sub="$(basename "$patch" | sed 's/--.*//; s/+/\//g')"
  if git -C "$ROOT/$sub" apply --reverse --check "$patch" 2>/dev/null; then
    echo "already applied: $(basename "$patch")"
  else
    git -C "$ROOT/$sub" apply "$patch"
    echo "applied: $(basename "$patch")"
  fi
done

# ---------------------------------------------------------------------------
# Host tools: tinyxml2 + gen

HOST="$BUILD/host"
log "Building tinyxml2 for the build machine"
cmake -S "$SRC/tinyxml2" -B "$HOST/tinyxml2" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$HOST/prefix" \
  -DBUILD_SHARED_LIBS=OFF -Dtinyxml2_BUILD_TESTING=OFF >/dev/null
cmake --build "$HOST/tinyxml2" -j "$JOBS" >/dev/null
cmake --install "$HOST/tinyxml2" >/dev/null

log "Building the gen code generator for the build machine"
cmake -S "$ROOT" -B "$HOST/gen" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DGEN_ONLY=ON -DCMAKE_PREFIX_PATH="$HOST/prefix" >/dev/null
cmake --build "$HOST/gen" --target gen -j "$JOBS"
GEN="$HOST/gen/bin/gen"

# ---------------------------------------------------------------------------
# Target toolchain

if [ "$MODE" = android ]; then
  NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_ROOT:-}}}"
  if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
    echo "error: set ANDROID_NDK_HOME to an Android NDK (r28 or newer)" >&2
    exit 1
  fi
  OUT="$BUILD/$ABI"
  TARGET_ARGS=(
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
    -DANDROID_ABI="$ABI"
    -DANDROID_PLATFORM="android-$API"
    -DANDROID_STL=c++_static
  )
  STRIP="$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip | head -1)"
else
  OUT="$BUILD/host-test"
  TARGET_ARGS=()
  STRIP=true
fi
PREFIX="$OUT/prefix"

COMMON_ARGS=(
  -G Ninja
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_INSTALL_PREFIX="$PREFIX"
  -DCMAKE_PREFIX_PATH="$PREFIX"
  -DCMAKE_FIND_ROOT_PATH="$PREFIX"
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON
  -DBUILD_SHARED_LIBS=OFF
  "${TARGET_ARGS[@]}"
)

dep() {
  local name="$1" src="$2"; shift 2
  if [ -f "$PREFIX/.installed-$name" ]; then
    echo "$name: up to date"
    return
  fi
  log "Building $name"
  cmake -S "$src" -B "$OUT/deps/$name" "${COMMON_ARGS[@]}" "$@"
  cmake --build "$OUT/deps/$name" -j "$JOBS"
  cmake --install "$OUT/deps/$name"
  touch "$PREFIX/.installed-$name"
}

# ---------------------------------------------------------------------------
# Libraries

SDL_EXTRA=()
# The host test only needs SDL to build, not to show a window.
[ "$MODE" = host-test ] && SDL_EXTRA=(-DSDL_UNIX_CONSOLE_BUILD=ON -DSDL_X11=OFF -DSDL_WAYLAND=OFF)
dep SDL3 "$SRC/SDL3" \
  -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF \
  -DSDL_TESTS=OFF -DSDL_EXAMPLES=OFF -DSDL_INSTALL_DOCS=OFF "${SDL_EXTRA[@]}"

dep SDL3_image "$SRC/SDL3_image" \
  -DSDLIMAGE_VENDORED=OFF -DSDLIMAGE_DEPS_SHARED=OFF -DSDLIMAGE_BACKEND_STB=ON \
  -DSDLIMAGE_PNG_LIBPNG=OFF -DSDLIMAGE_AVIF=OFF -DSDLIMAGE_JXL=OFF \
  -DSDLIMAGE_TIF=OFF -DSDLIMAGE_WEBP=OFF -DSDLIMAGE_QOI=OFF -DSDLIMAGE_SAMPLES=OFF \
  -DSDLIMAGE_TESTS=OFF
# (QOI is off because Besprited has its own copy of the same QOI code.)

dep libpng "$SRC/libpng" \
  -DPNG_SHARED=OFF -DPNG_STATIC=ON -DPNG_TESTS=OFF -DPNG_TOOLS=OFF \
  -DPNG_FRAMEWORK=OFF

JPEG_SIMD=ON
[ "$MODE" = host-test ] && JPEG_SIMD=OFF   # x86 SIMD would need nasm
dep libjpeg-turbo "$SRC/libjpeg-turbo" \
  -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DWITH_TURBOJPEG=OFF \
  -DWITH_SIMD="$JPEG_SIMD" -DWITH_TOOLS=OFF -DWITH_TESTS=OFF

dep freetype "$SRC/freetype" \
  -DFT_DISABLE_HARFBUZZ=ON -DFT_DISABLE_BROTLI=ON -DFT_DISABLE_BZIP2=ON \
  -DFT_DISABLE_PNG=ON -DFT_REQUIRE_ZLIB=ON

dep tinyxml2 "$SRC/tinyxml2" -Dtinyxml2_BUILD_TESTING=OFF

dep giflib "$HERE/deps/giflib" -DGIFLIB_SOURCE_DIR="$SRC/giflib"

dep pixman "$HERE/deps/pixman" -DPIXMAN_SOURCE_DIR="$SRC/pixman"

# ---------------------------------------------------------------------------
# Besprited

log "Building Besprited (libmain.so)"
cmake -S "$ROOT" -B "$OUT/besprited" \
  "${COMMON_ARGS[@]}" \
  -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
  -DANDROID=ON \
  -DGEN_EXECUTABLE="$GEN"
cmake --build "$OUT/besprited" --target besprited -j "$JOBS"

# ---------------------------------------------------------------------------
# Stage everything for Gradle

if [ "$MODE" = android ]; then
  log "Staging files for Gradle"
  JNI="$BUILD/jniLibs/$ABI"
  mkdir -p "$JNI"
  cp "$OUT/besprited/lib/libmain.so" "$JNI/"
  cp "$PREFIX/lib/libSDL3.so" "$JNI/"
  "$STRIP" --strip-unneeded "$JNI/libmain.so" "$JNI/libSDL3.so"

  # SDL's Java glue must match the SDL version that was built.
  rm -rf "$BUILD/sdl-java"
  mkdir -p "$BUILD/sdl-java"
  cp -r "$SRC/SDL3/android-project/app/src/main/java/org" "$BUILD/sdl-java/"

  # The app copies assets/data/ to its private storage on first launch.
  rm -rf "$BUILD/assets"
  mkdir -p "$BUILD/assets"
  cp -r "$ROOT/data" "$BUILD/assets/data"

  ls -la "$JNI"
fi

log "Done ($MODE)"
