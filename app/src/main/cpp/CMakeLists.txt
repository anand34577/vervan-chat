cmake_minimum_required(VERSION 3.22.1)
project(vervan_llama_jni)

# LLAMA_CPP_DIR / LLAMA_CPP_LIBS_DIR are passed in from app/build.gradle.kts, read from the
# optional `llamacpp.dir` entry in local.properties — this whole CMake project is only ever
# configured when that property is present (see the guard around externalNativeBuild in
# build.gradle.kts), so a checkout without llama.cpp built never reaches this file.
if(NOT DEFINED LLAMA_CPP_DIR)
    message(FATAL_ERROR
        "LLAMA_CPP_DIR not set. This CMakeLists.txt is meant to be configured via "
        "app/build.gradle.kts's llamacpp.dir property (local.properties), not invoked directly."
    )
endif()
if(NOT DEFINED LLAMA_CPP_LIBS_DIR)
    set(LLAMA_CPP_LIBS_DIR "${LLAMA_CPP_DIR}/build-android/bin")
endif()
# 32-bit builds link against the separate armeabi-v7a llama.cpp output (llamacpp.dir32 in
# local.properties). Gradle only enables this ABI when those libs exist, so the dir is valid here.
if(ANDROID_ABI STREQUAL "armeabi-v7a" AND DEFINED LLAMA_CPP_LIBS32_DIR AND NOT LLAMA_CPP_LIBS32_DIR STREQUAL "")
    set(LLAMA_CPP_LIBS_DIR "${LLAMA_CPP_LIBS32_DIR}")
endif()

# --- Prebuilt llama.cpp shared libraries -----------------------------------------------------
# Built separately by the user's own CMake+NDK+Vulkan invocation (see the plan/PR description) —
# this project does NOT build llama.cpp, only links against its already-built output. Filenames
# match `-DBUILD_SHARED_LIBS=ON`'s ggml-split-into-multiple-.so layout; if a different llama.cpp
# revision names/splits these differently, update this list to match what actually lands in
# LLAMA_CPP_LIBS_DIR.
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
vervan_import_prebuilt(ggml-vulkan libggml-vulkan.so)

# mtmd (vision) is linked only if present. Your current cmake invocation
# (-DLLAMA_BUILD_EXAMPLES=OFF, no explicit tools/mtmd flag) may or may not produce this — check
# LLAMA_CPP_LIBS_DIR after your build finishes. If libmtmd.so is missing, VERVAN_HAS_MTMD stays
# undefined and llama_bridge.cpp's #ifdef-guarded vision code paths compile out cleanly (text
# generation still works; loading a model with a non-null mmprojPath will just fail cleanly with
# a clear log line instead).
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
    ggml-vulkan
    $<$<BOOL:${VERVAN_HAS_MTMD}>:mtmd>
    ${log-lib}
    ${android-lib}
)
