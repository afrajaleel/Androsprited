# Minimal CMake build of giflib (normally built with make), used only for the
# Android build. Installs libgif.a + gif_lib.h so CMake's FindGIF finds them.
cmake_minimum_required(VERSION 3.16)
project(giflib C)

if(NOT GIFLIB_SOURCE_DIR)
  message(FATAL_ERROR "Pass -DGIFLIB_SOURCE_DIR=<giflib checkout>")
endif()
set(G ${GIFLIB_SOURCE_DIR})

add_library(gif STATIC
  ${G}/dgif_lib.c
  ${G}/egif_lib.c
  ${G}/gif_err.c
  ${G}/gif_font.c
  ${G}/gif_hash.c
  ${G}/gifalloc.c
  ${G}/openbsd-reallocarray.c
  ${G}/quantize.c)
target_include_directories(gif PRIVATE ${G})
set_target_properties(gif PROPERTIES POSITION_INDEPENDENT_CODE ON)

install(TARGETS gif ARCHIVE DESTINATION lib)
install(FILES ${G}/gif_lib.h DESTINATION include)
