# Minimal CMake build of pixman (normally built with meson), used only for
# the Android build. Besprited only needs pixman's region functions, but the
# library is built whole so nothing is missing at link time.
# Adapted from LibreSprite's third_party/pixman/CMakeLists.txt (MIT).
cmake_minimum_required(VERSION 3.16)
project(pixman C ASM)

if(NOT PIXMAN_SOURCE_DIR)
  message(FATAL_ERROR "Pass -DPIXMAN_SOURCE_DIR=<pixman checkout>")
endif()
set(P ${PIXMAN_SOURCE_DIR}/pixman)

file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/gen/pixman-version.h" "
#pragma once
#ifndef PIXMAN_H__
#  error pixman-version.h should only be included by pixman.h
#endif
#define PIXMAN_VERSION_MAJOR 0
#define PIXMAN_VERSION_MINOR 42
#define PIXMAN_VERSION_MICRO 2
#define PIXMAN_VERSION_STRING \"0.42.2\"
#define PIXMAN_VERSION 4202
#ifndef PIXMAN_API
# define PIXMAN_API
#endif
")
# pixman sources include "pixman-config.h" when HAVE_CONFIG_H is set; all
# the settings are passed as compile definitions below instead.
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/gen/pixman-config.h" "")

set(PIXMAN_SRC
  ${P}/pixman.c
  ${P}/pixman-access.c
  ${P}/pixman-access-accessors.c
  ${P}/pixman-bits-image.c
  ${P}/pixman-combine32.c
  ${P}/pixman-combine-float.c
  ${P}/pixman-conical-gradient.c
  ${P}/pixman-filter.c
  ${P}/pixman-x86.c
  ${P}/pixman-mips.c
  ${P}/pixman-arm.c
  ${P}/pixman-ppc.c
  ${P}/pixman-edge.c
  ${P}/pixman-edge-accessors.c
  ${P}/pixman-fast-path.c
  ${P}/pixman-glyph.c
  ${P}/pixman-general.c
  ${P}/pixman-gradient-walker.c
  ${P}/pixman-image.c
  ${P}/pixman-implementation.c
  ${P}/pixman-linear-gradient.c
  ${P}/pixman-matrix.c
  ${P}/pixman-noop.c
  ${P}/pixman-radial-gradient.c
  ${P}/pixman-region16.c
  ${P}/pixman-region32.c
  ${P}/pixman-solid-fill.c
  ${P}/pixman-timer.c
  ${P}/pixman-trap.c
  ${P}/pixman-utils.c)

set(PIXMAN_DEFS
  HAVE_CONFIG_H
  HAVE_BUILTIN_CLZ=1
  HAVE_PTHREADS=1
  PACKAGE="pixman"
  TLS=__thread)

if(CMAKE_SIZEOF_VOID_P EQUAL 8)
  list(APPEND PIXMAN_DEFS SIZEOF_LONG=8)
else()
  list(APPEND PIXMAN_DEFS SIZEOF_LONG=4)
endif()

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64" OR ANDROID_ABI STREQUAL "arm64-v8a")
  list(APPEND PIXMAN_SRC
    ${P}/pixman-arm-neon.c
    ${P}/pixman-arma64-neon-asm.S
    ${P}/pixman-arma64-neon-asm-bilinear.S)
  list(APPEND PIXMAN_DEFS USE_ARM_A64_NEON=1)
endif()

add_library(pixman-1 STATIC ${PIXMAN_SRC})
target_compile_definitions(pixman-1 PRIVATE ${PIXMAN_DEFS})
target_include_directories(pixman-1 PRIVATE ${P} "${CMAKE_CURRENT_BINARY_DIR}/gen")
set_target_properties(pixman-1 PROPERTIES POSITION_INDEPENDENT_CODE ON)

install(TARGETS pixman-1 ARCHIVE DESTINATION lib)
install(FILES ${P}/pixman.h "${CMAKE_CURRENT_BINARY_DIR}/gen/pixman-version.h"
  DESTINATION include/pixman-1)
