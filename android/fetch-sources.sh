#!/usr/bin/env bash
# Downloads the source code of every third-party library the Android build
# needs into android/build/src. Each library is pinned to a tag or commit so
# the build is reproducible. Safe to run again: existing checkouts are kept.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="${BESPRITED_DEPS_SRC:-$HERE/build/src}"
mkdir -p "$SRC"

# name | repository | tag or commit | sparse path (optional)
DEPS=(
  "SDL3|https://github.com/libsdl-org/SDL.git|release-3.4.18|"
  "SDL3_image|https://github.com/libsdl-org/SDL_image.git|release-3.4.8|"
  "libpng|https://github.com/pnggroup/libpng.git|v1.6.59|"
  "libjpeg-turbo|https://github.com/libjpeg-turbo/libjpeg-turbo.git|3.1.4.1|"
  "freetype|https://github.com/freetype/freetype.git|VER-2-14-3|"
  "tinyxml2|https://github.com/leethomason/tinyxml2.git|11.0.0|"
  # pixman and giflib have no GitHub home of their own; LibreSprite keeps
  # copies of both, so take them from there at a fixed commit.
  "pixman|https://github.com/LibreSprite/LibreSprite.git|0a62d62e6700b1d9b072f815cded5397a4cb0150|third_party/pixman"
  "giflib|https://github.com/LibreSprite/ls-android-deps.git|d4dbf25d55323ffc5b3eef32af1ad233eab3a59a|giflib"
)

fetch() {
  local name="$1" url="$2" ref="$3" sparse="$4"
  local dest="$SRC/$name"
  if [ -f "$dest/.fetched" ]; then
    echo "== $name: already fetched"
    return
  fi
  echo "== $name: fetching $ref"
  rm -rf "$dest" "$dest.tmp"
  if [ -z "$sparse" ]; then
    git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$ref" "$url" "$dest"
  else
    # Only check out one folder of a bigger repository.
    git init --quiet "$dest.tmp"
    git -C "$dest.tmp" remote add origin "$url"
    git -C "$dest.tmp" sparse-checkout set --no-cone "/$sparse/"
    git -C "$dest.tmp" fetch --quiet --depth 1 --filter=blob:none origin "$ref"
    git -C "$dest.tmp" checkout --quiet FETCH_HEAD
    mv "$dest.tmp/$sparse" "$dest"
    rm -rf "$dest.tmp"
  fi
  touch "$dest/.fetched"
}

for entry in "${DEPS[@]}"; do
  IFS='|' read -r name url ref sparse <<<"$entry"
  fetch "$name" "$url" "$ref" "$sparse"
done

echo "Sources are in $SRC"
