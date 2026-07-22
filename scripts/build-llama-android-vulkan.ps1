<#
.SYNOPSIS
    Builds llama.cpp (Vulkan + CPU) for Android, one output tree per ABI.

.DESCRIPTION
    This is the script Gradle's `buildLlamaCppNative` task invokes; it is also runnable by hand.
    Unlike the previous revision of this file it takes no machine-specific edits and does NOT
    need a Developer PowerShell: every toolchain path is either passed in, discovered, or
    derived, and the MSVC x64 environment is imported from vcvars64.bat when cl.exe isn't
    already on PATH.

    Why MSVC is needed at all for an *Android* build: ggml's Vulkan backend generates its SPIR-V
    shader headers with `vulkan-shaders-gen`, a tool that must run on the *host*. ggml builds it
    via ExternalProject with a generated host toolchain file, and on Windows its
    detect_host_compiler() looks for `cl`/`gcc`/`clang` on PATH. With no MSVC on PATH it either
    fails outright or (worse) picks up the NDK's clang and produces an ARM binary the host can't
    execute.

    Output layout (per ABI, so one checkout serves every ABI):
        <LlamaCppDir>/build-android/<abi>/bin/*.so

.EXAMPLE
    ./scripts/build-llama-android-vulkan.ps1
    ./scripts/build-llama-android-vulkan.ps1 -Abi arm64-v8a,armeabi-v7a -Clean
#>
[CmdletBinding()]
param(
    # llama.cpp checkout. Defaults to `llamacpp.dir` from local.properties.
    [string] $LlamaCppDir,

    # ARM ABIs to build. Both are on by default so the APK covers every ARM Android device:
    # arm64-v8a for modern 64-bit phones, armeabi-v7a for 32-bit ARM. This roughly doubles build
    # time; pass -Abi arm64-v8a if you only care about 64-bit.
    [string[]] $Abi = @("arm64-v8a", "armeabi-v7a"),

    [string] $AndroidSdk,
    [string] $Ndk,
    [string] $VulkanSdk,
    [string] $MsvcDir,

    # Native API floor. This is deliberately ABOVE the app's minSdk of 26: ggml's Vulkan backend
    # calls Vulkan 1.1 entry points (vkGetPhysicalDeviceFeatures2 and friends), and Android's
    # libvulkan.so only exports those from API 28 on — at 26 the link fails outright with
    # "undefined symbol: vkGetPhysicalDeviceFeatures2".
    #
    # The consequence is contained: on API 26/27 devices System.loadLibrary("vervan_llama_jni")
    # throws UnsatisfiedLinkError, and LlamaCppJni is only ever touched when a GGUF model is
    # actually being loaded (never at startup), so those devices lose GGUF support and keep
    # everything else. Lowering this to 26 would trade Vulkan acceleration on every device for
    # CPU-only GGUF on the handful still running Android 8.
    [int] $ApiLevel = 28,

    # -march for the arm64 ggml-cpu backend. See the compatibility note in the README: the default
    # targets ARMv8.2 (dotprod + i8mm), which is every mainstream arm64 phone from ~2019 on but
    # NOT early ARMv8.0 chips (e.g. Cortex-A53-era devices that can still run API 26). Pass
    # -CpuArmArch armv8-a for a build that runs on literally any arm64 Android device, at a real
    # cost to CPU-path token throughput. Ignored for armeabi-v7a.
    [string] $CpuArmArch = "armv8.2-a+dotprod+i8mm",

    # Wipe each ABI's build directory first. Off by default so repeat runs are incremental.
    [switch] $Clean,

    # Build the llama.cpp checkout as-is, without applying scripts/patches/*.patch.
    [switch] $SkipPatches,

    [int] $Jobs = [Environment]::ProcessorCount
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = Split-Path -Parent $PSScriptRoot

function Write-Step { param([string] $Message) Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Ok   { param([string] $Message) Write-Host "    [OK] $Message" -ForegroundColor DarkGray }

# ------------------------------------------------------------
# local.properties
# ------------------------------------------------------------
# Same file Gradle reads, so a path configured once is honoured whether the build starts from
# Gradle or from a shell.

function Get-LocalProperty {
    param([string] $Name)

    $file = Join-Path $RepoRoot "local.properties"
    if (-not (Test-Path $file)) { return $null }

    foreach ($line in (Get-Content $file)) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }

        $key = $trimmed.Substring(0, $trimmed.IndexOf("=")).Trim()
        if ($key -ne $Name) { continue }

        # Java .properties escapes drive-letter colons and backslashes ("D\:\\LLama.cpp").
        $value = $trimmed.Substring($trimmed.IndexOf("=") + 1).Trim()
        return $value.Replace("\:", ":").Replace("\\", "/")
    }
    return $null
}

function Resolve-Setting {
    param([string] $Provided, [string] $PropertyName, [string] $EnvName, [scriptblock] $Fallback)

    if ($Provided)      { return $Provided }
    $fromProps = Get-LocalProperty $PropertyName
    if ($fromProps)     { return $fromProps }
    if ($EnvName) {
        $fromEnv = [Environment]::GetEnvironmentVariable($EnvName)
        if ($fromEnv)   { return $fromEnv }
    }
    if ($Fallback)      { return (& $Fallback) }
    return $null
}

# ------------------------------------------------------------
# Toolchain discovery
# ------------------------------------------------------------

Write-Step "Resolving toolchain"

$LlamaCppDir = Resolve-Setting $LlamaCppDir "llamacpp.dir" "LLAMA_CPP_DIR" $null
if (-not $LlamaCppDir) {
    throw "No llama.cpp checkout. Set llamacpp.dir in local.properties or pass -LlamaCppDir."
}
$LlamaCppDir = (Resolve-Path $LlamaCppDir).Path.Replace("\", "/")
if (-not (Test-Path "$LlamaCppDir/CMakeLists.txt")) {
    throw "Not a llama.cpp checkout (no CMakeLists.txt): $LlamaCppDir"
}
Write-Ok "llama.cpp: $LlamaCppDir"

$AndroidSdk = Resolve-Setting $AndroidSdk "sdk.dir" "ANDROID_HOME" {
    Join-Path $env:LOCALAPPDATA "Android/Sdk"
}
if (-not (Test-Path $AndroidSdk)) { throw "Android SDK not found: $AndroidSdk" }
$AndroidSdk = (Resolve-Path $AndroidSdk).Path.Replace("\", "/")
Write-Ok "Android SDK: $AndroidSdk"

# NDK: an explicit path wins, otherwise the version pinned in app/build.gradle.kts (so the
# llama.cpp libraries and the JNI bridge are always built by the same NDK), otherwise newest.
$Ndk = Resolve-Setting $Ndk "ndk.dir" "ANDROID_NDK_HOME" {
    $gradleFile = Join-Path $RepoRoot "app/build.gradle.kts"
    $pinned = $null
    if (Test-Path $gradleFile) {
        $match = Select-String -Path $gradleFile -Pattern 'ndkVersion\s*=\s*"([^"]+)"' | Select-Object -First 1
        if ($match) { $pinned = $match.Matches[0].Groups[1].Value }
    }
    if ($pinned -and (Test-Path "$AndroidSdk/ndk/$pinned")) { return "$AndroidSdk/ndk/$pinned" }

    $newest = Get-ChildItem "$AndroidSdk/ndk" -Directory -ErrorAction SilentlyContinue |
              Sort-Object Name -Descending | Select-Object -First 1
    if ($newest) { return $newest.FullName }
    return $null
}
if (-not $Ndk -or -not (Test-Path $Ndk)) { throw "Android NDK not found. Pass -Ndk or install one." }
$Ndk = (Resolve-Path $Ndk).Path.Replace("\", "/")

$AndroidToolchain = "$Ndk/build/cmake/android.toolchain.cmake"
if (-not (Test-Path $AndroidToolchain)) { throw "NDK CMake toolchain missing: $AndroidToolchain" }
Write-Ok "NDK: $Ndk"

# CMake + Ninja from the SDK, so the build doesn't depend on whatever is on PATH.
$CMake = $null
$Ninja = $null
$cmakeDirs = Get-ChildItem "$AndroidSdk/cmake" -Directory -ErrorAction SilentlyContinue |
             Sort-Object { [version]($_.Name) } -Descending
foreach ($dir in $cmakeDirs) {
    if ((Test-Path "$($dir.FullName)/bin/cmake.exe") -and (Test-Path "$($dir.FullName)/bin/ninja.exe")) {
        $CMake = "$($dir.FullName)/bin/cmake.exe".Replace("\", "/")
        $Ninja = "$($dir.FullName)/bin/ninja.exe".Replace("\", "/")
        break
    }
}
if (-not $CMake) {
    $onPath = Get-Command cmake.exe -ErrorAction SilentlyContinue
    if (-not $onPath) { throw "No CMake found under $AndroidSdk/cmake or on PATH." }
    $CMake = $onPath.Source
    $ninjaOnPath = Get-Command ninja.exe -ErrorAction SilentlyContinue
    if (-not $ninjaOnPath) { throw "CMake found but Ninja is missing; install the SDK CMake package." }
    $Ninja = $ninjaOnPath.Source
}
Write-Ok "CMake: $CMake"

# Vulkan SDK: supplies the headers, glslc, and the SPIRV-Headers CMake package. The *library* to
# link comes from the NDK sysroot instead (Android's own libvulkan.so), never from the SDK.
$VulkanSdk = Resolve-Setting $VulkanSdk "vulkan.dir" "VULKAN_SDK" {
    $newest = Get-ChildItem "C:/VulkanSDK" -Directory -ErrorAction SilentlyContinue |
              Sort-Object Name -Descending | Select-Object -First 1
    if ($newest) { return $newest.FullName }
    return $null
}
if (-not $VulkanSdk -or -not (Test-Path $VulkanSdk)) {
    throw "Vulkan SDK not found. Install it from https://vulkan.lunarg.com/ or pass -VulkanSdk."
}
$VulkanSdk = (Resolve-Path $VulkanSdk).Path.Replace("\", "/")

$Glslc           = "$VulkanSdk/Bin/glslc.exe"
$VulkanInclude   = "$VulkanSdk/Include"
$SpirvHeadersDir = "$VulkanSdk/Lib/cmake/SPIRV-Headers"

foreach ($required in @(
    @{ Name = "Vulkan C header";    Path = "$VulkanInclude/vulkan/vulkan.h" },
    @{ Name = "Vulkan C++ header";  Path = "$VulkanInclude/vulkan/vulkan.hpp" },
    @{ Name = "SPIR-V header";      Path = "$VulkanInclude/spirv/unified1/spirv.hpp" },
    @{ Name = "SPIRV-Headers pkg";  Path = "$SpirvHeadersDir/SPIRV-HeadersConfig.cmake" },
    @{ Name = "glslc";              Path = $Glslc }
)) {
    if (-not (Test-Path $required.Path)) {
        throw "Vulkan SDK is incomplete - missing $($required.Name): $($required.Path)"
    }
}
Write-Ok "Vulkan SDK: $VulkanSdk"

# ------------------------------------------------------------
# MSVC host environment (for vulkan-shaders-gen)
# ------------------------------------------------------------

function Import-MsvcEnvironment {
    param([string] $ExplicitDir)

    # Already in a Developer shell with the right compiler? Leave it alone.
    $existing = Get-Command cl.exe -ErrorAction SilentlyContinue
    if ($existing -and $existing.Source -match "Hostx64[\\/]+x64[\\/]+cl\.exe$") {
        Write-Ok "MSVC already on PATH: $($existing.Source)"
        return
    }

    $vcvars = $null

    # -MsvcDir may point at the VC install root, or at the .../bin folder the user has handy.
    if ($ExplicitDir) {
        $candidate = $ExplicitDir
        while ($candidate -and -not (Test-Path (Join-Path $candidate "Auxiliary/Build/vcvars64.bat"))) {
            $parent = Split-Path -Parent $candidate
            if ($parent -eq $candidate) { $candidate = $null; break }
            $candidate = $parent
        }
        if ($candidate) { $vcvars = Join-Path $candidate "Auxiliary/Build/vcvars64.bat" }
    }

    if (-not $vcvars) {
        $vswhere = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
        if (Test-Path $vswhere) {
            $installPath = & $vswhere -latest -products * `
                -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
                -property installationPath 2>$null | Select-Object -First 1
            if ($installPath) {
                $candidate = Join-Path $installPath "VC/Auxiliary/Build/vcvars64.bat"
                if (Test-Path $candidate) { $vcvars = $candidate }
            }
        }
    }

    if (-not $vcvars -or -not (Test-Path $vcvars)) {
        throw @"
No x64 MSVC toolset found.

ggml builds its Vulkan shader generator for the host, and on Windows that needs MSVC. Install
"Desktop development with C++" in the Visual Studio Installer, or pass -MsvcDir pointing at your
VC directory (the one containing Auxiliary\Build\vcvars64.bat).
"@
    }

    # vcvars64.bat only mutates its own cmd.exe; run it, dump the resulting environment, and
    # replay it into this process so child CMake/Ninja invocations inherit a usable MSVC.
    Write-Ok "Importing MSVC environment: $vcvars"
    $output = & "$env:ComSpec" /s /c "`"$vcvars`" >nul 2>&1 && set"
    if ($LASTEXITCODE -ne 0) { throw "vcvars64.bat failed (exit $LASTEXITCODE)." }

    foreach ($line in $output) {
        if ($line -match "^([^=]+)=(.*)$") {
            Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] -ErrorAction SilentlyContinue
        }
    }

    $resolved = Get-Command cl.exe -ErrorAction SilentlyContinue
    if (-not $resolved) { throw "vcvars64.bat ran but cl.exe is still not on PATH." }
    if ($resolved.Source -notmatch "Hostx64[\\/]+x64[\\/]+cl\.exe$") {
        throw "Expected the x64 host compiler (Hostx64\x64\cl.exe) but found: $($resolved.Source)"
    }
    Write-Ok "Host compiler: $($resolved.Source)"
}

Write-Step "Preparing the Windows host compiler"
Import-MsvcEnvironment -ExplicitDir $MsvcDir

# ------------------------------------------------------------
# Local patches against the llama.cpp checkout
# ------------------------------------------------------------
# Fixes we need that aren't upstream yet. Each is a plain git patch under scripts/patches/ with
# its rationale in the header. Application is idempotent (already-applied patches are detected
# and skipped) so repeat builds are safe, and `git -C <checkout> checkout ggml/ src/` reverts
# everything if you'd rather manage the checkout yourself. Set -SkipPatches to opt out entirely.

# `git apply --check` reports rejections on stderr. Under Windows PowerShell 5.1 a native
# command's stderr is wrapped into ErrorRecords, which $ErrorActionPreference = "Stop" then turns
# into a terminating error even for the probe calls whose failure is a normal, expected answer.
# Run those through cmd.exe with stderr discarded and judge purely by exit code.
function Test-GitApply {
    param([string] $Repo, [string] $PatchPath, [switch] $Reverse)

    $reverseFlag = if ($Reverse) { "--reverse " } else { "" }
    & "$env:ComSpec" /c "git -C `"$Repo`" apply $reverseFlag--check `"$PatchPath`" >nul 2>&1"
    return $LASTEXITCODE -eq 0
}

function Invoke-LlamaCppPatches {
    param([string] $Repo, [string] $PatchDir)

    if (-not (Test-Path $PatchDir)) { return }
    $patches = Get-ChildItem $PatchDir -Filter "*.patch" -File | Sort-Object Name
    if (-not $patches) { return }

    Write-Step "Applying local patches to $Repo"

    foreach ($patch in $patches) {
        # --reverse --check succeeds only when the patch is already in the tree.
        if (Test-GitApply -Repo $Repo -PatchPath $patch.FullName -Reverse) {
            Write-Ok "$($patch.Name) (already applied)"
            continue
        }

        if (-not (Test-GitApply -Repo $Repo -PatchPath $patch.FullName)) {
            throw @"
Cannot apply $($patch.Name) to $Repo.

The llama.cpp sources it targets have changed (likely a newer upstream revision). Check whether
the fix has landed upstream -- if so, delete the patch; if not, rebase it. Re-run with
-SkipPatches to build without it, but expect the crash the patch header describes.
"@
        }

        & git -C $Repo apply $patch.FullName
        if ($LASTEXITCODE -ne 0) { throw "Failed to apply $($patch.Name)." }
        Write-Ok "$($patch.Name) (applied)"
    }
}

if (-not $SkipPatches) {
    Invoke-LlamaCppPatches -Repo $LlamaCppDir -PatchDir (Join-Path $PSScriptRoot "patches")
} else {
    Write-Host "    Skipping local patches (-SkipPatches)." -ForegroundColor Yellow
}

# ggml reads VULKAN_SDK to locate the SPIRV-Headers package; the NDK variable keeps the
# ExternalProject sub-build consistent with the outer one.
$env:VULKAN_SDK  = $VulkanSdk
$env:ANDROID_NDK = $Ndk
$env:PATH        = "$(Split-Path $CMake);$VulkanSdk/Bin;$env:PATH"

# ------------------------------------------------------------
# Per-ABI build
# ------------------------------------------------------------

# PowerShell's -File invocation mode passes each argument through as a single string, so
# "-Abi arm64-v8a,armeabi-v7a" arrives as one element rather than a bound array. Normalize so
# both the comma-joined form (what Gradle passes) and a real array work.
$Abi = $Abi | ForEach-Object { $_ -split "," } | ForEach-Object { $_.Trim() } | Where-Object { $_ }

# Vulkan is 64-bit only. On 32-bit targets Vulkan's non-dispatchable handles (VkBuffer and
# friends) are plain uint64_t rather than distinct pointer types, so vulkan.hpp's C++ wrappers
# collapse to ambiguous overloads and ggml-vulkan.cpp does not compile ("invalid operands to
# binary expression ... and 'vk::Buffer'"). armeabi-v7a therefore gets a CPU-only llama.cpp,
# which is the right trade anyway: 32-bit ARM devices are low-end and their GPUs rarely expose a
# usable Vulkan compute path.
$AbiSettings = @{
    "arm64-v8a"   = @{ Triple = "aarch64-linux-android"; UsesArmArch = $true;  Vulkan = $true  }
    "armeabi-v7a" = @{ Triple = "arm-linux-androideabi"; UsesArmArch = $false; Vulkan = $false }
}

foreach ($currentAbi in $Abi) {
    if (-not $AbiSettings.ContainsKey($currentAbi)) {
        throw "Unsupported ABI '$currentAbi'. Supported: $($AbiSettings.Keys -join ', ')"
    }

    $settings  = $AbiSettings[$currentAbi]
    $buildDir  = "$LlamaCppDir/build-android/$currentAbi"
    $outputDir = "$buildDir/bin"

    $backends = if ($settings.Vulkan) { "Vulkan + CPU" } else { "CPU only" }
    Write-Step "Building llama.cpp for $currentAbi (API $ApiLevel, $backends)"

    # Android ships libvulkan.so in the NDK sysroot; the loader is a stub that resolves the real
    # driver at runtime, so devices without a Vulkan driver simply report zero devices rather
    # than failing to load the library.
    $vulkanLibrary = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/$($settings.Triple)/$ApiLevel/libvulkan.so"
    if ($settings.Vulkan -and -not (Test-Path $vulkanLibrary)) {
        throw "No libvulkan.so for $currentAbi at API $ApiLevel in the NDK sysroot: $vulkanLibrary"
    }

    if ($Clean -and (Test-Path $buildDir)) {
        Write-Ok "Removing previous build: $buildDir"
        Remove-Item -Path $buildDir -Recurse -Force
    }

    $configureArguments = @(
        "-G", "Ninja"
        "-S", $LlamaCppDir
        "-B", $buildDir

        "-DCMAKE_MAKE_PROGRAM:FILEPATH=$Ninja"
        "-DCMAKE_TOOLCHAIN_FILE:FILEPATH=$AndroidToolchain"
        "-DCMAKE_BUILD_TYPE=Release"

        # Everything lands in one flat directory per ABI, which is what the Gradle sync task and
        # app/src/main/cpp/CMakeLists.txt both expect.
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY:PATH=$outputDir"
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY:PATH=$outputDir"

        "-DANDROID_ABI=$currentAbi"
        "-DANDROID_PLATFORM=android-$ApiLevel"
        # 32-bit ARM: NEON is the NDK default for armeabi-v7a but pin it, since ggml's ARM
        # kernels assume it. Deliberately NOT set via GGML_CPU_ARM_ARCH, which ggml turns into a
        # bare -march= and would drop the toolchain's -mfpu=neon-vfpv4/-mfloat-abi settings.
        "-DANDROID_ARM_NEON=ON"
        # c++_shared, matching ANDROID_STL in the app's own CMake invocation: with c++_static
        # each .so would carry a private copy of the C++ runtime, and exceptions/RTTI crossing
        # the llama <-> JNI bridge boundary would break.
        "-DANDROID_STL=c++_shared"

        "-DGGML_NATIVE=OFF"
        "-DGGML_OPENMP=OFF"
        "-DGGML_CCACHE=OFF"
        "-DGGML_BUILD_TESTS=OFF"
        "-DGGML_BUILD_EXAMPLES=OFF"

        "-DBUILD_SHARED_LIBS=ON"

        "-DLLAMA_BUILD_COMMON=ON"
        "-DLLAMA_BUILD_MTMD=ON"
        # mtmd's video support shells out to an ffmpeg *binary* on PATH, which never exists on
        # Android, and it drags in vendor/sheredom/subprocess.h -> posix_spawn, which Android's
        # libc only declares from API 28 up. Leaving it on would force the whole build to API 28
        # and drop API 26/27 devices for no functional gain. The video entry points remain, and
        # return a clean "not supported in this build" error.
        "-DMTMD_VIDEO=OFF"
        "-DLLAMA_BUILD_TOOLS=OFF"
        "-DLLAMA_BUILD_SERVER=OFF"
        "-DLLAMA_BUILD_APP=OFF"
        "-DLLAMA_BUILD_UI=OFF"
        "-DLLAMA_USE_PREBUILT_UI=OFF"
        "-DLLAMA_BUILD_EXAMPLES=OFF"
        "-DLLAMA_BUILD_TESTS=OFF"

    )

    if ($settings.Vulkan) {
        $configureArguments += @(
            "-DGGML_VULKAN=ON"
            "-DVulkan_INCLUDE_DIR:PATH=$VulkanInclude"
            "-DVulkan_LIBRARY:FILEPATH=$vulkanLibrary"
            "-DVulkan_GLSLC_EXECUTABLE:FILEPATH=$Glslc"
            "-DSPIRV-Headers_DIR:PATH=$SpirvHeadersDir"
        )
    } else {
        $configureArguments += "-DGGML_VULKAN=OFF"
    }

    if ($settings.UsesArmArch) {
        # Without an explicit -march, cross-compiled ggml-cpu falls back to baseline armv8-a
        # scalar kernels (GGML_NATIVE only auto-detects on native builds) - no SDOT/UDOT or I8MM.
        if ($CpuArmArch) { $configureArguments += "-DGGML_CPU_ARM_ARCH=$CpuArmArch" }
    } else {
        # 32-bit ARM: llamafile's sgemm.cpp takes its NEON path on any __ARM_NEON target but uses
        # fp16 vector intrinsics (vld1q_f16/vld1_f16) that only exist on AArch64 / ARMv8.2-FP16,
        # so it does not compile for armv7. It is a matmul fast path, not a requirement - ggml
        # falls back to its own kernels, which is the right trade for a compatibility ABI.
        $configureArguments += "-DGGML_LLAMAFILE=OFF"
    }

    & $CMake @configureArguments
    if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed for $currentAbi." }

    # If this trips, ggml picked the NDK's clang for the host shader generator and the build
    # would produce an ARM vulkan-shaders-gen that cannot run on this machine.
    $hostToolchain = "$buildDir/host-toolchain.cmake"
    if (Test-Path $hostToolchain) {
        $contents = Get-Content $hostToolchain -Raw
        if ($contents -match "Android[\\/]+Sdk[\\/]+ndk") {
            throw "The Vulkan shader generator selected the Android NDK compiler instead of the host compiler."
        }
    }

    Write-Ok "Compiling (llama + mtmd, $Jobs jobs)"
    # mtmd is a sibling target of llama, not a dependency, so it must be named explicitly or the
    # vision projector support silently won't be built.
    & $CMake --build $buildDir --target llama mtmd --parallel $Jobs
    if ($LASTEXITCODE -ne 0) { throw "Build failed for $currentAbi." }

    # The NDK's libc++_shared.so is a runtime dependency of every library above but is not
    # produced by the build, so copy it next to them.
    $stl = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/$($settings.Triple)/libc++_shared.so"
    if (Test-Path $stl) { Copy-Item $stl -Destination $outputDir -Force }

    $produced = Get-ChildItem -Path $outputDir -Filter "*.so" -File -ErrorAction SilentlyContinue
    if (-not $produced) { throw "Build reported success but no .so files landed in $outputDir." }

    $expectedLibraries = @("libllama.so", "libggml.so", "libggml-base.so", "libggml-cpu.so")
    if ($settings.Vulkan) { $expectedLibraries += "libggml-vulkan.so" }
    foreach ($expected in $expectedLibraries) {
        if (-not ($produced.Name -contains $expected)) {
            throw "Missing expected library $expected in $outputDir."
        }
    }

    Write-Ok "$currentAbi -> $outputDir"
    $produced | Sort-Object Name | ForEach-Object {
        Write-Host ("      {0,-28} {1,10:N0} KB" -f $_.Name, ($_.Length / 1KB)) -ForegroundColor DarkGray
    }
}

Write-Host "`nllama.cpp Android build complete." -ForegroundColor Green
