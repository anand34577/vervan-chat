cmake_minimum_required(VERSION 3.22.1)
project(vervan_whisper_standalone VERSION 1.9.1 LANGUAGES C CXX)

# Standalone wrapper around a whisper.cpp checkout's own ggml/ and src/ subdirectories, invoked
# by scripts/build-whisper-android.ps1 (one CMake configure+build per ABI). This does NOT use
# whisper.cpp's own top-level CMakeLists.txt: that file builds ggml and `whisper` under one
# global BUILD_SHARED_LIBS toggle, which would produce a second, separately-versioned set of
# libggml*.so files. This app's build already ships llama.cpp's own libggml.so/libggml-base.so/
# libggml-cpu*.so at those exact filenames in jniLibs/<abi>/ (from
# scripts/build-llama-android-vulkan.ps1) — llama.cpp and whisper.cpp are independent checkouts,
# almost certainly pinned to different ggml revisions, so a second same-named .so would silently
# clobber (or be clobbered by) llama.cpp's copy the moment both land in the same jniLibs
# directory. Building ggml STATIC and only the outer `whisper` library SHARED avoids the
# collision entirely: one self-contained libwhisper.so with no external ggml runtime dependency,
# exactly what app/src/main/cpp/CMakeLists.txt's whisper.cpp block already expects (see its
# `add_library(whisper SHARED IMPORTED)` — a single .so, no companion ggml libs to place
# alongside it).
#
# CMake's add_library() without an explicit STATIC/SHARED keyword follows whatever
# BUILD_SHARED_LIBS is set to AT THE MOMENT that particular add_subdirectory() runs (it is a
# live variable read, not a one-time decision) — so toggling it OFF before ggml's subdirectory
# and back ON before whisper's src subdirectory gets both library types from the exact same
# upstream CMakeLists.txt files, with no forked/hand-copied build logic to fall out of sync with
# a future whisper.cpp update.
#
# GGML_VULKAN (see the ABI block below) doesn't change any of this: with GGML_BACKEND_DL left
# off, ggml_add_backend_library() links the Vulkan backend into the static `ggml` target exactly
# like the CPU backend, so it's still one self-contained libwhisper.so. The only new runtime
# dependency is Android's own libvulkan.so — a system-provided stub the OS resolves, never
# bundled in the APK (same as llama.cpp's Vulkan path already relies on).

if(NOT DEFINED WHISPER_CPP_DIR OR WHISPER_CPP_DIR STREQUAL "")
    message(FATAL_ERROR "WHISPER_CPP_DIR not set — pass -DWHISPER_CPP_DIR=<path to a whisper.cpp checkout>.")
endif()
if(NOT EXISTS "${WHISPER_CPP_DIR}/include/whisper.h")
    message(FATAL_ERROR "WHISPER_CPP_DIR (${WHISPER_CPP_DIR}) does not look like a whisper.cpp checkout (no include/whisper.h).")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
# src/CMakeLists.txt references this directly (set_target_properties(whisper PROPERTIES
# SOVERSION ${SOVERSION})) — normally set by whisper.cpp's own top-level CMakeLists.txt, which
# this wrapper deliberately doesn't include (see header comment above).
set(SOVERSION 1)

# Library-only build: no CLI tools/tests/server. GGML_VULKAN (arm64-v8a only — see the ABI block
# below) is passed in via -D from build-whisper-android.ps1, not forced here, so it isn't set at
# all unless the script's Vulkan SDK discovery actually found one; ggml's own `option(GGML_VULKAN
# ... OFF)` default then applies for a CPU-only configure. whisper.cpp's own whisper_backend_init
# (see whisper.cpp's whisper_backend_init_gpu) already tries the GPU device first and falls back
# to CPU automatically when none is compiled in or none is found at runtime — no fallback logic
# needed on our side beyond compiling Vulkan support in.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_COREML OFF CACHE BOOL "" FORCE)
set(WHISPER_OPENVINO OFF CACHE BOOL "" FORCE)

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
# OFF, not the llama.cpp build's ON: that trades a libomp.so runtime dependency (an extra file
# this script would then have to place in jniLibs/) for a threading barrier optimization that
# matters far more on llama.cpp's large-matmul decode loop than on Whisper Tiny's much smaller
# encoder/decoder — not worth the extra shipped library for this engine.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(ANDROID_ARM_NEON ON CACHE BOOL "" FORCE)
    # Vulkan is 64-bit only (same reasoning as build-llama-android-vulkan.ps1's ABI split):
    # Vulkan's non-dispatchable handles are plain uint64_t on 32-bit targets rather than distinct
    # pointer types, so vulkan.hpp's C++ wrappers don't compile. armeabi-v7a is always CPU-only
    # here regardless of what the build script passes for GGML_VULKAN.
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory("${WHISPER_CPP_DIR}/ggml" "${CMAKE_BINARY_DIR}/ggml")

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
add_subdirectory("${WHISPER_CPP_DIR}/src" "${CMAKE_BINARY_DIR}/whisper-src")

# Normally set by whisper.cpp's own top-level CMakeLists.txt (after its add_subdirectory(src)) —
# whisper.cpp's whisper_version() reads this macro directly, so its absence isn't a cosmetic
# gap, it's a compile error (undeclared identifier).
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="${PROJECT_VERSION}")

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    # fp16 NEON intrinsics whisper.cpp's own reference CMake (examples/whisper.android) enables
    # per-ABI the same way — armv7 has no fp16 vector unit without this.
    target_compile_options(whisper PRIVATE -mfpu=neon-vfpv4)
endif()

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections)
    target_link_options(whisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
endif()
