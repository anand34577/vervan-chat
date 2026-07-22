cmake_minimum_required(VERSION 3.22.1)
project(vervan_native)

# This CMake project builds whichever JNI bridges are requested via the flags passed in from
# app/build.gradle.kts: llama.cpp / GGUF generation (derived here into VERVAN_BUILD_LLAMA from
# LLAMA_CPP_DIR) and/or whisper.cpp speech-to-text (WHISPER_CPP_AVAILABLE, passed in directly).
# Either or both can be enabled — a build with only whisper.cpp (no llama.cpp checkout) configures
# just fine, and vice versa.

# --- llama.cpp (GGUF generation + embedding) --------------------------------------------------
# LLAMA_CPP_DIR is passed in from app/build.gradle.kts, read from the `llamacpp.dir` entry in
# local.properties. The llama.cpp libraries themselves are built by
# scripts/build-llama-android-vulkan.ps1, which Gradle's `buildLlamaCppNative` task runs
# automatically before this project is compiled. They are IMPORTED here rather than built as a
# sub-project: llama.cpp's Vulkan backend needs a host-side shader generator (and therefore an
# MSVC/host toolchain that Gradle's CMake invocation has no way to set up), and building it once
# per ABI outside Gradle also keeps incremental app builds from re-running a multi-minute build.
set(VERVAN_BUILD_LLAMA FALSE)
if(DEFINED LLAMA_CPP_DIR AND EXISTS "${LLAMA_CPP_DIR}/include/llama.h")
    set(VERVAN_BUILD_LLAMA TRUE)
elseif(DEFINED LLAMA_CPP_DIR)
    message(WARNING "LLAMA_CPP_DIR is set (${LLAMA_CPP_DIR}) but does not look like a llama.cpp checkout (no include/llama.h); the llama.cpp JNI bridge is skipped.")
endif()

if(VERVAN_BUILD_LLAMA)
# Per-ABI output tree written by the build script. The legacy single-ABI layout
# (build-android/bin, from the older hand-run script) is accepted as a fallback so an existing
# checkout keeps working without a rebuild.
if(NOT DEFINED LLAMA_CPP_LIBS_DIR OR LLAMA_CPP_LIBS_DIR STREQUAL "")
    set(LLAMA_CPP_LIBS_DIR "${LLAMA_CPP_DIR}/build-android/${ANDROID_ABI}/bin")
endif()
if(NOT EXISTS "${LLAMA_CPP_LIBS_DIR}/libllama.so"
   AND EXISTS "${LLAMA_CPP_DIR}/build-android/bin/libllama.so"
   AND ANDROID_ABI STREQUAL "arm64-v8a")
    set(LLAMA_CPP_LIBS_DIR "${LLAMA_CPP_DIR}/build-android/bin")
endif()

if(NOT EXISTS "${LLAMA_CPP_LIBS_DIR}/libllama.so")
    message(FATAL_ERROR
        "No llama.cpp libraries for ${ANDROID_ABI} in ${LLAMA_CPP_LIBS_DIR}.\n"
        "Run: powershell -File scripts/build-llama-android-vulkan.ps1 -Abi ${ANDROID_ABI}"
    )
endif()

message(STATUS "llama.cpp libraries for ${ANDROID_ABI}: ${LLAMA_CPP_LIBS_DIR}")

# --- Prebuilt llama.cpp shared libraries -----------------------------------------------------
# Filenames match `-DBUILD_SHARED_LIBS=ON`'s ggml-split-into-multiple-.so layout.
function(vervan_import_prebuilt target_name file_name)
    add_library(${target_name} SHARED IMPORTED)
    set_target_properties(${target_name} PROPERTIES
        IMPORTED_LOCATION "${LLAMA_CPP_LIBS_DIR}/${file_name}"
    )
endfunction()

vervan_import_prebuilt(llama libllama.so)
vervan_import_prebuilt(ggml libggml.so)
vervan_import_prebuilt(ggml-base libggml-base.so)
vervan_import_prebuilt(ggml-cpu libggml-cpu.so)

# The Vulkan backend is 64-bit only — on 32-bit targets Vulkan's non-dispatchable handles are
# plain uint64_t instead of distinct types, and vulkan.hpp's C++ wrappers don't compile. The
# build script therefore produces a CPU-only llama.cpp for armeabi-v7a. ggml selects backends at
# runtime, so linking without it just means no GPU offload on those devices.
if(EXISTS "${LLAMA_CPP_LIBS_DIR}/libggml-vulkan.so")
    vervan_import_prebuilt(ggml-vulkan libggml-vulkan.so)
    set(VERVAN_HAS_VULKAN TRUE)
else()
    message(STATUS "No libggml-vulkan.so for ${ANDROID_ABI} — CPU-only llama.cpp for this ABI.")
endif()

# mtmd (vision) is linked only if present. The build script passes -DLLAMA_BUILD_MTMD=ON so it
# normally is; if it's missing, VERVAN_HAS_MTMD stays undefined and llama_bridge.cpp's
# #ifdef-guarded vision code paths compile out cleanly (text generation still works; loading a
# model with a non-null mmprojPath fails cleanly with a clear log line instead).
if(EXISTS "${LLAMA_CPP_LIBS_DIR}/libmtmd.so")
    vervan_import_prebuilt(mtmd libmtmd.so)
    set(VERVAN_HAS_MTMD TRUE)
endif()

add_library(vervan_llama_jni SHARED llama_bridge.cpp)

target_include_directories(vervan_llama_jni PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
    # mtmd headers — path has moved between llama.cpp revisions (examples/llava,
    # tools/mtmd, common/mtmd); adjust if the compiler can't find mtmd.h.
    "${LLAMA_CPP_DIR}/tools/mtmd"
)

if(VERVAN_HAS_MTMD)
    target_compile_definitions(vervan_llama_jni PRIVATE VERVAN_HAS_MTMD=1)
endif()

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(vervan_llama_jni
    llama
    ggml
    ggml-base
    ggml-cpu
    $<$<BOOL:${VERVAN_HAS_VULKAN}>:ggml-vulkan>
    $<$<BOOL:${VERVAN_HAS_MTMD}>:mtmd>
    ${log-lib}
    ${android-lib}
)
endif() # VERVAN_BUILD_LLAMA

# --- whisper.cpp (GGUF/GGML speech-to-text) ---------------------------------------------------
# Built only when WHISPER_CPP_AVAILABLE is passed (see app/build.gradle.kts, which sets it from
# the presence of libwhisper.so in jniLibs/<abi>/). Unlike llama.cpp there is NO source-checkout
# build path: the developer builds whisper.cpp for Android themselves and drops the resulting
# libwhisper.so (+ any ggml .so deps not already shipped) into jniLibs/. WHISPER_CPP_DIR, if set,
# points only at the source checkout whose whisper.h header this bridge #includes — it is never
# compiled from here.
#
# whisper.cpp shares ggml with llama.cpp: if both are built against the same ggml revision,
# llama.cpp's already-shipped libggml*.so satisfies libwhisper.so's runtime dependency too, so a
# whisper-only .so drop (just libwhisper.so) is enough. If ggml revisions differ, also place
# whisper.cpp's own libggml*.so copies in jniLibs/<abi>/.
set(VERVAN_BUILD_WHISPER FALSE)
if(WHISPER_CPP_AVAILABLE)
    set(WHISPER_LIBS_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
    # WHISPER_CPP_AVAILABLE is app-wide (it is true when libwhisper.so exists for AT LEAST ONE
    # ABI), but this CMake project is configured once PER ABI, and abiFilters is the union of the
    # llama.cpp and whisper.cpp ABI lists. So a whisper.cpp drop for arm64-v8a only — the common
    # case, since that is what most people build first — configures this block for armeabi-v7a
    # too, with no libwhisper.so present. Skipping that ABI with a warning is correct; a
    # FATAL_ERROR here would make a partial drop fail the entire build.
    if(EXISTS "${WHISPER_LIBS_DIR}/libwhisper.so")
        set(VERVAN_BUILD_WHISPER TRUE)
    else()
        message(WARNING
            "libwhisper.so is missing for ${ANDROID_ABI} at ${WHISPER_LIBS_DIR}/libwhisper.so — "
            "the whisper.cpp STT bridge is skipped for this ABI (offline STT falls back to "
            "sherpa-onnx / the device recognizer there). To enable it, build whisper.cpp for "
            "${ANDROID_ABI} and copy libwhisper.so (and libggml*.so if the ggml revision differs "
            "from llama.cpp's) into app/src/main/jniLibs/${ANDROID_ABI}/."
        )
    endif()
endif()

if(VERVAN_BUILD_WHISPER)
    message(STATUS "whisper.cpp library for ${ANDROID_ABI}: ${WHISPER_LIBS_DIR}")

    add_library(whisper SHARED IMPORTED)
    set_target_properties(whisper PROPERTIES IMPORTED_LOCATION "${WHISPER_LIBS_DIR}/libwhisper.so")

    add_library(vervan_whisper_jni SHARED whisper_bridge.cpp)

    # whisper.h: prefer the source checkout's headers when WHISPER_CPP_DIR is set; otherwise fall
    # back to an include/ dir shipped alongside the .so in jniLibs. The checkout root is included
    # for pre-2024 whisper.cpp layouts, which kept whisper.h at the top level rather than include/;
    # ggml/include covers the revisions whose whisper.h still pulls in ggml.h.
    target_include_directories(vervan_whisper_jni PRIVATE
        "${WHISPER_CPP_DIR}/include"
        "${WHISPER_CPP_DIR}/include/whisper"
        "${WHISPER_CPP_DIR}/ggml/include"
        "${WHISPER_CPP_DIR}"
        "${WHISPER_LIBS_DIR}/include"
    )

    # Fail loudly and specifically: libwhisper.so is present for this ABI, so whisper.cpp is
    # clearly meant to be on, but without whisper.h the bridge compile dies on a bare
    # "'whisper.h' file not found" that says nothing about how to fix it. This IS fatal (unlike the
    # missing-.so case above, which legitimately means "not enabled for this ABI").
    find_file(VERVAN_WHISPER_HEADER whisper.h
        PATHS "${WHISPER_CPP_DIR}/include" "${WHISPER_CPP_DIR}" "${WHISPER_LIBS_DIR}/include"
        NO_DEFAULT_PATH
    )
    if(NOT VERVAN_WHISPER_HEADER)
        message(FATAL_ERROR
            "libwhisper.so is present for ${ANDROID_ABI} but whisper.h was not found.\n"
            "Set `whispercpp.dir` in local.properties to your whisper.cpp source checkout, or copy "
            "whisper.h into app/src/main/jniLibs/${ANDROID_ABI}/include/."
        )
    endif()

    find_library(log-lib-whisper log)
    target_link_libraries(vervan_whisper_jni whisper ${log-lib-whisper})
endif() # VERVAN_BUILD_WHISPER
