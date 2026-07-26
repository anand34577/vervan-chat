<#
.SYNOPSIS
    Builds whisper.cpp for Android as one self-contained libwhisper.so per ABI, with the Vulkan
    GPU backend compiled in on arm64-v8a.

.DESCRIPTION
    This is the script Gradle's `buildWhisperCppNative` task invokes; it is also runnable by
    hand. Delegates to scripts/whisper-cmake/CMakeLists.txt, which builds ggml STATIC and only
    the outer `whisper` library SHARED (see that file's header comment for why: this app already
    ships llama.cpp's own libggml*.so at those exact filenames, from a different ggml revision —
    a second same-named .so from whisper.cpp's own vendored ggml copy would collide). The output
    is a single self-contained libwhisper.so with no companion ggml .so files to place alongside
    it, matching what app/src/main/cpp/CMakeLists.txt's whisper.cpp block expects.

    Vulkan (arm64-v8a only — same 64-bit-only constraint as build-llama-android-vulkan.ps1, see
    that script's comment) needs the same host toolchain that script already requires: a Vulkan
    SDK (headers, glslc, the SPIRV-Headers CMake package) and an x64 MSVC compiler, because ggml's
    Vulkan backend generates its SPIR-V shader headers with a host-side tool
    (vulkan-shaders-gen). If either is missing, this script does NOT fail the build — it logs a
    warning and falls back to a CPU-only libwhisper.so for that ABI, mirroring the exact runtime
    fallback whisper.cpp's own whisper_backend_init() already does (try the GPU device, fall back
    to CPU if none is compiled in or none is found at runtime). armeabi-v7a is always CPU-only.

    Output layout (per ABI):
        <WhisperCppDir>/build-android/<abi>/bin/libwhisper.so

.EXAMPLE
    ./scripts/build-whisper-android.ps1
    ./scripts/build-whisper-android.ps1 -Abi arm64-v8a -Clean
    ./scripts/build-whisper-android.ps1 -Vulkan:$false
#>
[CmdletBinding()]
param(
    # whisper.cpp checkout. Defaults to `whispercpp.dir` from local.properties.
    [string] $WhisperCppDir,

    [string[]] $Abi = @("arm64-v8a", "armeabi-v7a"),

    [string] $AndroidSdk,
    [string] $Ndk,
    [string] $VulkanSdk,
    [string] $MsvcDir,

    # Try to compile the Vulkan backend in on arm64-v8a. On by default; falls back to a CPU-only
    # build (with a warning, not a failure) if the Vulkan SDK or an x64 MSVC toolchain isn't
    # found. Pass -Vulkan:$false to skip the attempt entirely (e.g. for a faster CPU-only
    # rebuild loop).
    [bool] $Vulkan = $true,

    # Matches the app's minSdk (see app/build.gradle.kts) — whisper.cpp's CPU backend has no API
    # floor above that, unlike the Vulkan backend which needs Vulkan 1.1 entry points Android's
    # libvulkan.so only exports from API 28 (see build-llama-android-vulkan.ps1's -ApiLevel doc).
    # Applied per-ABI below: 28 when Vulkan actually ends up enabled for that ABI, 26 otherwise.
    [int] $ApiLevel = 26,
    [int] $VulkanApiLevel = 28,

    [switch] $Clean,

    [int] $Jobs = [Environment]::ProcessorCount
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = Split-Path -Parent $PSScriptRoot

function Write-Step { param([string] $Message) Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Ok   { param([string] $Message) Write-Host "    [OK] $Message" -ForegroundColor DarkGray }
function Write-Warn2 { param([string] $Message) Write-Host "    [WARN] $Message" -ForegroundColor Yellow }

function Get-LocalProperty {
    param([string] $Name)

    $file = Join-Path $RepoRoot "local.properties"
    if (-not (Test-Path $file)) { return $null }

    foreach ($line in (Get-Content $file)) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }

        $key = $trimmed.Substring(0, $trimmed.IndexOf("=")).Trim()
        if ($key -ne $Name) { continue }

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

Write-Step "Resolving toolchain"

$WhisperCppDir = Resolve-Setting $WhisperCppDir "whispercpp.dir" "WHISPER_CPP_DIR" $null
if (-not $WhisperCppDir) {
    throw "No whisper.cpp checkout. Set whispercpp.dir in local.properties or pass -WhisperCppDir."
}
$WhisperCppDir = (Resolve-Path $WhisperCppDir).Path.Replace("\", "/")
if (-not (Test-Path "$WhisperCppDir/include/whisper.h")) {
    throw "Not a whisper.cpp checkout (no include/whisper.h): $WhisperCppDir"
}
Write-Ok "whisper.cpp: $WhisperCppDir"

$AndroidSdk = Resolve-Setting $AndroidSdk "sdk.dir" "ANDROID_HOME" {
    Join-Path $env:LOCALAPPDATA "Android/Sdk"
}
if (-not (Test-Path $AndroidSdk)) { throw "Android SDK not found: $AndroidSdk" }
$AndroidSdk = (Resolve-Path $AndroidSdk).Path.Replace("\", "/")
Write-Ok "Android SDK: $AndroidSdk"

# Same NDK-resolution order as build-llama-android-vulkan.ps1: an explicit path wins, otherwise
# the version pinned in app/build.gradle.kts (so both native builds always use the same NDK),
# otherwise newest installed.
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

$WrapperCMakeLists = Join-Path $PSScriptRoot "whisper-cmake"
if (-not (Test-Path (Join-Path $WrapperCMakeLists "CMakeLists.txt"))) {
    throw "Missing scripts/whisper-cmake/CMakeLists.txt."
}

# ------------------------------------------------------------
# Vulkan SDK + MSVC host toolchain discovery (arm64-v8a only). Best-effort: any failure here
# just disables Vulkan for this run (falls back to a CPU-only libwhisper.so) rather than failing
# the whole build — unlike build-llama-android-vulkan.ps1, which treats a missing Vulkan SDK as
# fatal, whisper.cpp already has a working, tested CPU-only path (that's what this app shipped
# with before this Vulkan attempt), so there is a real fallback worth preferring over a hard stop.
# ------------------------------------------------------------

$VulkanReady = $false
$Glslc = $null
$VulkanInclude = $null
$SpirvHeadersDir = $null
$VulkanLibraryPath = $null

if ($Vulkan) {
    Write-Step "Resolving Vulkan toolchain (arm64-v8a)"

    $VulkanSdk = Resolve-Setting $VulkanSdk "vulkan.dir" "VULKAN_SDK" {
        $newest = Get-ChildItem "C:/VulkanSDK" -Directory -ErrorAction SilentlyContinue |
                  Sort-Object Name -Descending | Select-Object -First 1
        if ($newest) { return $newest.FullName }
        return $null
    }

    if (-not $VulkanSdk -or -not (Test-Path $VulkanSdk)) {
        Write-Warn2 "No Vulkan SDK found (install from https://vulkan.lunarg.com/ or pass -VulkanSdk) — building CPU-only."
    } else {
        $VulkanSdk = (Resolve-Path $VulkanSdk).Path.Replace("\", "/")
        $Glslc = "$VulkanSdk/Bin/glslc.exe"
        $VulkanInclude = "$VulkanSdk/Include"
        $SpirvHeadersDir = "$VulkanSdk/Lib/cmake/SPIRV-Headers"
        $VulkanLibraryPath = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/$VulkanApiLevel/libvulkan.so"

        $missing = @()
        foreach ($required in @(
            @{ Name = "Vulkan C header";    Path = "$VulkanInclude/vulkan/vulkan.h" },
            @{ Name = "Vulkan C++ header";  Path = "$VulkanInclude/vulkan/vulkan.hpp" },
            @{ Name = "SPIR-V header";      Path = "$VulkanInclude/spirv/unified1/spirv.hpp" },
            @{ Name = "SPIRV-Headers pkg";  Path = "$SpirvHeadersDir/SPIRV-HeadersConfig.cmake" },
            @{ Name = "glslc";              Path = $Glslc },
            @{ Name = "NDK libvulkan.so (API $VulkanApiLevel)"; Path = $VulkanLibraryPath }
        )) {
            if (-not (Test-Path $required.Path)) { $missing += $required.Name }
        }

        if ($missing.Count -gt 0) {
            Write-Warn2 "Vulkan SDK at $VulkanSdk is incomplete (missing: $($missing -join ', ')) — building CPU-only."
        } else {
            Write-Ok "Vulkan SDK: $VulkanSdk"

            # Importing the MSVC host environment (for vulkan-shaders-gen) is best-effort too: an
            # exception here just disables Vulkan for this run instead of throwing, so a machine
            # without "Desktop development with C++" installed still gets a working CPU build.
            try {
                $existing = Get-Command cl.exe -ErrorAction SilentlyContinue
                if ($existing -and $existing.Source -match "Hostx64[\\/]+x64[\\/]+cl\.exe$") {
                    Write-Ok "MSVC already on PATH: $($existing.Source)"
                } else {
                    $vcvars = $null
                    if ($MsvcDir) {
                        $candidate = $MsvcDir
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
                        throw "No x64 MSVC toolset found (install 'Desktop development with C++', or pass -MsvcDir)."
                    }

                    Write-Ok "Importing MSVC environment: $vcvars"
                    $output = & "$env:ComSpec" /s /c "`"$vcvars`" >nul 2>&1 && set"
                    if ($LASTEXITCODE -ne 0) { throw "vcvars64.bat failed (exit $LASTEXITCODE)." }
                    foreach ($line in $output) {
                        if ($line -match "^([^=]+)=(.*)$") {
                            Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] -ErrorAction SilentlyContinue
                        }
                    }
                    $resolved = Get-Command cl.exe -ErrorAction SilentlyContinue
                    if (-not $resolved -or $resolved.Source -notmatch "Hostx64[\\/]+x64[\\/]+cl\.exe$") {
                        throw "vcvars64.bat ran but the x64 host compiler (Hostx64\x64\cl.exe) is still not on PATH."
                    }
                    Write-Ok "Host compiler: $($resolved.Source)"
                }

                $env:VULKAN_SDK  = $VulkanSdk
                $env:ANDROID_NDK = $Ndk
                $env:PATH        = "$(Split-Path $CMake);$VulkanSdk/Bin;$env:PATH"
                $VulkanReady = $true
            } catch {
                Write-Warn2 "MSVC host toolchain unavailable ($($_.Exception.Message)) — building CPU-only."
            }
        }
    }
}

$Abi = $Abi | ForEach-Object { $_ -split "," } | ForEach-Object { $_.Trim() } | Where-Object { $_ }
$SupportedAbis = @("arm64-v8a", "armeabi-v7a")

foreach ($currentAbi in $Abi) {
    if ($currentAbi -notin $SupportedAbis) {
        throw "Unsupported ABI '$currentAbi'. Supported: $($SupportedAbis -join ', ')"
    }

    # Vulkan is 64-bit only (see scripts/whisper-cmake/CMakeLists.txt's ABI block).
    $useVulkan = $VulkanReady -and $currentAbi -eq "arm64-v8a"
    $abiApiLevel = if ($useVulkan) { $VulkanApiLevel } else { $ApiLevel }

    $buildDir  = "$WhisperCppDir/build-android/$currentAbi"
    $outputDir = "$buildDir/bin"

    $backendLabel = if ($useVulkan) { "Vulkan + CPU" } else { "CPU only" }
    Write-Step "Building whisper.cpp for $currentAbi (API $abiApiLevel, $backendLabel)"

    if ($Clean -and (Test-Path $buildDir)) {
        Write-Ok "Removing previous build: $buildDir"
        Remove-Item -Path $buildDir -Recurse -Force
    }

    $configureArguments = @(
        "-G", "Ninja"
        "-S", $WrapperCMakeLists.Replace("\", "/")
        "-B", $buildDir

        "-DCMAKE_MAKE_PROGRAM:FILEPATH=$Ninja"
        "-DCMAKE_TOOLCHAIN_FILE:FILEPATH=$AndroidToolchain"
        "-DCMAKE_BUILD_TYPE=Release"

        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY:PATH=$outputDir"
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY:PATH=$outputDir"

        "-DANDROID_ABI=$currentAbi"
        "-DANDROID_PLATFORM=android-$abiApiLevel"
        "-DANDROID_STL=c++_shared"

        "-DWHISPER_CPP_DIR:PATH=$WhisperCppDir"
    )

    if ($useVulkan) {
        $configureArguments += @(
            "-DGGML_VULKAN=ON"
            "-DVulkan_INCLUDE_DIR:PATH=$VulkanInclude"
            "-DVulkan_LIBRARY:FILEPATH=$VulkanLibraryPath"
            "-DVulkan_GLSLC_EXECUTABLE:FILEPATH=$Glslc"
            "-DSPIRV-Headers_DIR:PATH=$SpirvHeadersDir"
        )
    }

    & $CMake @configureArguments
    if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed for $currentAbi." }

    if ($useVulkan) {
        # Same sanity check build-llama-android-vulkan.ps1 does: if this trips, ggml picked the
        # NDK's clang for the host shader generator and would produce an ARM vulkan-shaders-gen
        # that cannot run on this machine.
        $hostToolchain = "$buildDir/host-toolchain.cmake"
        if (Test-Path $hostToolchain) {
            $contents = Get-Content $hostToolchain -Raw
            if ($contents -match "Android[\\/]+Sdk[\\/]+ndk") {
                throw "The Vulkan shader generator selected the Android NDK compiler instead of the host compiler."
            }
        }
    }

    Write-Ok "Compiling (whisper, $Jobs jobs)"
    & $CMake --build $buildDir --target whisper --parallel $Jobs
    if ($LASTEXITCODE -ne 0) { throw "Build failed for $currentAbi." }

    $produced = Get-ChildItem -Path $outputDir -Filter "*.so" -File -ErrorAction SilentlyContinue
    if (-not $produced -or -not ($produced.Name -contains "libwhisper.so")) {
        throw "Build reported success but libwhisper.so did not land in $outputDir."
    }

    Write-Ok "$currentAbi -> $outputDir"
    $produced | Sort-Object Name | ForEach-Object {
        Write-Host ("      {0,-28} {1,10:N0} KB" -f $_.Name, ($_.Length / 1KB)) -ForegroundColor DarkGray
    }
}

Write-Host "`nwhisper.cpp Android build complete." -ForegroundColor Green
