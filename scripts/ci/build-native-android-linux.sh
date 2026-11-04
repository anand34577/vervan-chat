#!/usr/bin/env bash
# Android cross-compile of llama.cpp and whisper.cpp for the GitHub Actions release job.
# Output layout matches scripts/build-llama-android-vulkan.ps1 and scripts/build-whisper-android.ps1
# exactly (<dir>/build-android/<abi>/bin/*.so), so app/build.gradle.kts's sync tasks need no
# changes; set llamacpp.autobuild=false / whispercpp.autobuild=false so Gradle only packages what
# this script built.
#
# arm64-v8a: GGML_CPU_ALL_VARIANTS + GGML_BACKEND_DL. One CPU plugin per ARM feature tier
# (armv8.0 up to dotprod/i8mm/SVE) is built and the bridge picks the best one on the phone at
# runtime. A single -march=armv8.2-a+dotprod+i8mm build crashes with SIGILL on the many phones
# without i8mm (anything older than ~2022), so per-device dispatch is what makes GGUF work on
# most devices. The Vulkan GPU backend is built too (API 28+). Point VULKAN_SDK at a LunarG
# Linux SDK's x86_64 directory; without it, glslc on PATH is tried. LLAMA_VULKAN_REQUIRED=1
# fails the build instead of falling back to CPU-only when Vulkan can't be configured.
# armeabi-v7a: CPU-only, API 26 (the app's minSdk), NEON.
set -euo pipefail

: "${LLAMA_CPP_DIR:?set LLAMA_CPP_DIR}"
: "${WHISPER_CPP_DIR:?set WHISPER_CPP_DIR}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}"
ABIS=${ABIS:-"arm64-v8a armeabi-v7a"}
LLAMA_VULKAN=${LLAMA_VULKAN:-1}
JOBS=${JOBS:-$(nproc)}
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

TOOLCHAIN="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
[ -f "$TOOLCHAIN" ] || { echo "NDK toolchain not found: $TOOLCHAIN" >&2; exit 1; }
NDK_PREBUILT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

# Same idempotent patch-apply logic as the .ps1 scripts (Invoke-LlamaCppPatches).
apply_patches() {
    local dir="$1" patch_dir="$REPO_ROOT/scripts/patches"
    [ -d "$patch_dir" ] || return 0
    for patch in "$patch_dir"/*.patch; do
        [ -e "$patch" ] || continue
        if git -C "$dir" apply --reverse --check "$patch" 2>/dev/null; then
            echo "  [OK] $(basename "$patch") (already applied)"
        elif git -C "$dir" apply --check "$patch" 2>/dev/null; then
            git -C "$dir" apply "$patch"
            echo "  [OK] $(basename "$patch") (applied)"
        else
            # Upstream fixed or moved the code a patch targets; the build doesn't depend on it.
            echo "  [SKIP] $(basename "$patch") does not apply to this llama.cpp revision"
        fi
    done
}

echo "==> Applying local patches to $LLAMA_CPP_DIR"
apply_patches "$LLAMA_CPP_DIR"

common_llama_flags=(
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release -DANDROID_STL=c++_shared
    -DGGML_NATIVE=OFF -DGGML_CCACHE=OFF
    -DGGML_BUILD_TESTS=OFF -DGGML_BUILD_EXAMPLES=OFF -DBUILD_SHARED_LIBS=ON
    -DLLAMA_BUILD_COMMON=ON -DLLAMA_BUILD_MTMD=ON -DMTMD_VIDEO=OFF
    -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_APP=OFF
    -DLLAMA_BUILD_UI=OFF -DLLAMA_USE_PREBUILT_UI=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_CURL=OFF
)

configure_llama() {
    local abi="$1" api="$2" build_dir="$3" out_dir="$4"; shift 4
    rm -rf "$build_dir"
    cmake -G Ninja -S "$LLAMA_CPP_DIR" -B "$build_dir" \
        -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="$out_dir" -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$out_dir" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$api" \
        "${common_llama_flags[@]}" "$@"
}

for abi in $ABIS; do
    build_dir="$LLAMA_CPP_DIR/build-android/$abi"
    out_dir="$build_dir/bin"
    case "$abi" in
        arm64-v8a)
            triple=aarch64-linux-android; api=28; omp_arch=aarch64
            cpu_flags=(-DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_OPENMP=ON -DGGML_CPU_KLEIDIAI=OFF)
            vulkan_flags=()
            if [ "$LLAMA_VULKAN" = "1" ] && [ -n "${VULKAN_SDK:-}" ]; then
                # LunarG SDK (the x86_64 dir): headers, glslc and the SPIRV-Headers CMake package,
                # the same pieces scripts/build-llama-android-vulkan.ps1 takes from the Windows SDK.
                # ggml-vulkan's find_package(SPIRV-Headers) has no fallback, so it must be passed.
                spirv_cfg=$(find "$VULKAN_SDK" -name SPIRV-HeadersConfig.cmake | head -1)
                [ -n "$spirv_cfg" ] || { echo "SPIRV-HeadersConfig.cmake not found in $VULKAN_SDK" >&2; exit 1; }
                vulkan_flags=(-DGGML_VULKAN=ON
                    -DVulkan_GLSLC_EXECUTABLE="$VULKAN_SDK/bin/glslc"
                    -DVulkan_INCLUDE_DIR="$VULKAN_SDK/include"
                    -DSPIRV-Headers_DIR="$(dirname "$spirv_cfg")"
                    -DVulkan_LIBRARY="$NDK_PREBUILT/sysroot/usr/lib/$triple/$api/libvulkan.so")
            elif [ "$LLAMA_VULKAN" = "1" ] && command -v glslc >/dev/null; then
                vulkan_flags=(-DGGML_VULKAN=ON
                    -DVulkan_GLSLC_EXECUTABLE="$(command -v glslc)"
                    -DVulkan_LIBRARY="$NDK_PREBUILT/sysroot/usr/lib/$triple/$api/libvulkan.so")
                [ -d /usr/include/vulkan ] && vulkan_flags+=(-DVulkan_INCLUDE_DIR=/usr/include)
            fi
            if [ "${LLAMA_VULKAN_REQUIRED:-0}" = "1" ] && [ ${#vulkan_flags[@]} -eq 0 ]; then
                echo "Vulkan is required but no Vulkan SDK or glslc was found" >&2; exit 1
            fi
            echo "==> Configuring llama.cpp for $abi (API $api, CPU variants${vulkan_flags:+ + Vulkan})"
            if ! configure_llama "$abi" "$api" "$build_dir" "$out_dir" "${cpu_flags[@]}" "${vulkan_flags[@]}" \
                || ! cmake --build "$build_dir" --parallel "$JOBS"; then
                if [ "${LLAMA_VULKAN_REQUIRED:-0}" = "1" ]; then
                    # The app's verifyLlamaCpp* task needs libggml-vulkan.so, so a CPU-only
                    # fallback would only fail later with a less useful message.
                    echo "Vulkan build failed for $abi and LLAMA_VULKAN_REQUIRED=1" >&2; exit 1
                elif [ ${#vulkan_flags[@]} -gt 0 ]; then
                    echo "::warning::Vulkan build failed for $abi; falling back to CPU-only"
                    configure_llama "$abi" "$api" "$build_dir" "$out_dir" "${cpu_flags[@]}" -DGGML_VULKAN=OFF
                    cmake --build "$build_dir" --parallel "$JOBS"
                else
                    exit 1
                fi
            fi
            # GGML_OPENMP=ON links libggml-base.so against the NDK's libomp.so, which must ship too.
            omp=$(find "$NDK_PREBUILT/lib/clang" -path "*/lib/linux/$omp_arch/libomp.so" | head -1)
            [ -n "$omp" ] || { echo "libomp.so not found in the NDK" >&2; exit 1; }
            cp "$omp" "$out_dir/"
            ls "$out_dir"/libggml-cpu*.so >/dev/null || { echo "No CPU variant plugins in $out_dir" >&2; exit 1; }
            ;;
        armeabi-v7a)
            triple=arm-linux-androideabi; api=26
            echo "==> Configuring llama.cpp for $abi (API $api, CPU only)"
            configure_llama "$abi" "$api" "$build_dir" "$out_dir" \
                -DGGML_OPENMP=OFF -DGGML_VULKAN=OFF -DANDROID_ARM_NEON=ON -DGGML_LLAMAFILE=OFF
            cmake --build "$build_dir" --target llama mtmd --parallel "$JOBS"
            [ -f "$out_dir/libggml-cpu.so" ] || { echo "Missing libggml-cpu.so in $out_dir" >&2; exit 1; }
            ;;
        *) echo "Unsupported ABI: $abi" >&2; exit 1 ;;
    esac

    # libc++_shared.so is a runtime dependency of every library above but isn't produced by the build.
    stl="$NDK_PREBUILT/sysroot/usr/lib/$triple/libc++_shared.so"
    [ -f "$stl" ] && cp "$stl" "$out_dir/"
    for lib in libllama.so libggml.so libggml-base.so; do
        [ -f "$out_dir/$lib" ] || { echo "Missing expected library $lib in $out_dir" >&2; exit 1; }
    done
    echo "  $abi -> $out_dir"
    ls -1 "$out_dir"/*.so
done

echo "==> Building whisper.cpp for $ABIS (CPU only)"
for abi in $ABIS; do
    build_dir="$WHISPER_CPP_DIR/build-android/$abi"
    out_dir="$build_dir/bin"
    extra_flags=()
    [ "$abi" = "armeabi-v7a" ] && extra_flags+=(-DANDROID_ARM_NEON=ON)

    cmake -G Ninja -S "$REPO_ROOT/scripts/whisper-cmake" -B "$build_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="$out_dir" -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$out_dir" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-26" -DANDROID_STL=c++_shared \
        -DWHISPER_CPP_DIR="$WHISPER_CPP_DIR" "${extra_flags[@]}"
    cmake --build "$build_dir" --target whisper --parallel "$JOBS"

    [ -f "$out_dir/libwhisper.so" ] || { echo "libwhisper.so did not land in $out_dir" >&2; exit 1; }
    echo "  $abi -> $out_dir"
done

echo "Native CI build complete."
