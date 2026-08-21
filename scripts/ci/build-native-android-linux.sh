#!/usr/bin/env bash
# CPU-only Android cross-compile of llama.cpp and whisper.cpp, for the GitLab CI release job
# (Linux runner, no Vulkan SDK/MSVC — see scripts/build-llama-android-vulkan.ps1 and
# scripts/build-whisper-android.ps1 for the full local dev build with the Vulkan GPU backend,
# which stays Windows-only and untouched by this script). Output layout matches those scripts
# exactly: <dir>/build-android/<abi>/bin/*.so — so app/build.gradle.kts's sync tasks need no
# changes; set llamacpp.autobuild=false / whispercpp.autobuild=false so Gradle just packages what
# this script already built instead of re-invoking powershell.exe.
#
# Release artifacts are installed on a wide range of ARM64 devices. Build llama.cpp's upstream
# GGML_CPU_ALL_VARIANTS backend plugins so the runtime dispatches to the features the device
# actually has instead of baking an armv8.2/i8mm instruction assumption into every APK.
set -euo pipefail

: "${LLAMA_CPP_DIR:?set LLAMA_CPP_DIR}"
: "${WHISPER_CPP_DIR:?set WHISPER_CPP_DIR}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}"
ABIS=${ABIS:-"arm64-v8a armeabi-v7a"}
API_LEVEL=${API_LEVEL:-26}
JOBS=${JOBS:-$(nproc)}
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

TOOLCHAIN="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
[ -f "$TOOLCHAIN" ] || { echo "NDK toolchain not found: $TOOLCHAIN" >&2; exit 1; }

# Same idempotent patch-apply logic as the .ps1 scripts (Invoke-LlamaCppPatches): a plain
# `git apply --check` reads fine on Linux/pwsh without the cmd.exe stderr workaround Windows
# PowerShell 5.1 needs.
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
            echo "Cannot apply $(basename "$patch") to $dir (llama.cpp revision moved on; delete or rebase it)." >&2
            exit 1
        fi
    done
}

echo "==> Applying local patches to $LLAMA_CPP_DIR"
apply_patches "$LLAMA_CPP_DIR"

for abi in $ABIS; do
    case "$abi" in
        arm64-v8a)   triple=aarch64-linux-android; abi_flags=(-DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON) ;;
        armeabi-v7a) triple=arm-linux-androideabi; abi_flags=(-DANDROID_ARM_NEON=ON -DGGML_LLAMAFILE=OFF) ;;
        *) echo "Unsupported ABI: $abi" >&2; exit 1 ;;
    esac

    echo "==> Building llama.cpp for $abi (CPU only, API $API_LEVEL)"
    build_dir="$LLAMA_CPP_DIR/build-android/$abi"
    out_dir="$build_dir/bin"
    cmake -G Ninja -S "$LLAMA_CPP_DIR" -B "$build_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="$out_dir" -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$out_dir" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$API_LEVEL" -DANDROID_STL=c++_shared \
        -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_CCACHE=OFF -DGGML_VULKAN=OFF \
        -DGGML_BUILD_TESTS=OFF -DGGML_BUILD_EXAMPLES=OFF -DBUILD_SHARED_LIBS=ON \
        -DLLAMA_BUILD_COMMON=ON -DLLAMA_BUILD_MTMD=ON -DMTMD_VIDEO=OFF \
        -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_APP=OFF \
        -DLLAMA_BUILD_UI=OFF -DLLAMA_USE_PREBUILT_UI=OFF -DLLAMA_BUILD_TESTS=OFF \
        "${abi_flags[@]}"
    cmake --build "$build_dir" --target llama mtmd --parallel "$JOBS"

    # libc++_shared.so is a runtime dependency of every library above but isn't produced by the
    # build itself — same reasoning as the .ps1 scripts' copy step, just the Linux sysroot path.
    stl="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/libc++_shared.so"
    [ -f "$stl" ] && cp "$stl" "$out_dir/"

    for lib in libllama.so libggml.so libggml-base.so; do
        [ -f "$out_dir/$lib" ] || { echo "Missing expected library $lib in $out_dir" >&2; exit 1; }
    done
    if [ "$abi" = "arm64-v8a" ]; then
        compgen -G "$out_dir/libggml-cpu-*.so" > /dev/null || {
            echo "No runtime-dispatched libggml-cpu-*.so backend for $abi in $out_dir" >&2
            exit 1
        }
    else
        [ -f "$out_dir/libggml-cpu.so" ] || { echo "Missing libggml-cpu.so for $abi" >&2; exit 1; }
    fi
    echo "  $abi -> $out_dir"
done

echo "==> Building whisper.cpp for $ABIS (CPU only, API $API_LEVEL)"
for abi in $ABIS; do
    build_dir="$WHISPER_CPP_DIR/build-android/$abi"
    out_dir="$build_dir/bin"
    extra_flags=()
    [ "$abi" = "armeabi-v7a" ] && extra_flags+=(-DANDROID_ARM_NEON=ON)

    cmake -G Ninja -S "$REPO_ROOT/scripts/whisper-cmake" -B "$build_dir" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="$out_dir" -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$out_dir" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$API_LEVEL" -DANDROID_STL=c++_shared \
        -DWHISPER_CPP_DIR="$WHISPER_CPP_DIR" "${extra_flags[@]}"
    cmake --build "$build_dir" --target whisper --parallel "$JOBS"

    [ -f "$out_dir/libwhisper.so" ] || { echo "libwhisper.so did not land in $out_dir" >&2; exit 1; }
    echo "  $abi -> $out_dir"
done

echo "Native CI build complete."
