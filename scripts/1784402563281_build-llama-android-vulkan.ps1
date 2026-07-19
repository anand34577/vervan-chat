$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# Configuration
# ------------------------------------------------------------

$Repo = "D:/LLama.cpp"
$Build = "$Repo/build-android"

$AndroidSdk = "C:/Users/anand/AppData/Local/Android/Sdk"
$Ndk = "$AndroidSdk/ndk/28.1.13356709"

$CMake = "$AndroidSdk/cmake/3.22.1/bin/cmake.exe"
$Ninja = "$AndroidSdk/cmake/3.22.1/bin/ninja.exe"

$VulkanSdk = "C:/VulkanSDK/1.4.350.0"

$AndroidToolchain = "$Ndk/build/cmake/android.toolchain.cmake"

$VulkanInclude = "$VulkanSdk/Include"
$VulkanLibrary = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/28/libvulkan.so"

$Glslc = "$VulkanSdk/Bin/glslc.exe"
$SpirvHeadersDir = "$VulkanSdk/Lib/cmake/SPIRV-Headers"

# The single change from the previous script: without this, ggml's CPU backend compiles with
# no ARM -march flag at all when cross-compiling (GGML_NATIVE only auto-detects on native builds),
# silently falling back to baseline armv8-a scalar/basic-NEON kernels - no SDOT/UDOT (dotprod) or
# I8MM matmul instructions. That is the CPU-path performance gap vs PocketPal's build. Drop
# "+i8mm" if you need to support pre-2019 devices without ARMv8.2 I8MM support.
$CpuArmArch = "armv8.2-a+dotprod+i8mm"

# Make the SDK variables available to child CMake processes.
$env:ANDROID_NDK = $Ndk
$env:VULKAN_SDK = $VulkanSdk

# Ensure the selected CMake, Ninja, and Vulkan tools are available.
$env:PATH = "$AndroidSdk/cmake/3.22.1/bin;$VulkanSdk/Bin;$env:PATH"

# ------------------------------------------------------------
# Verify the x64 Windows host compiler
# ------------------------------------------------------------

$HostCompiler = (Get-Command cl.exe -ErrorAction Stop).Source

Write-Host "Windows host compiler:"
Write-Host $HostCompiler

if ($HostCompiler -notmatch "Hostx64[\\/]+x64[\\/]+cl\.exe$") {
    throw @"
The current shell is not using the x64 MSVC compiler.

Expected:
  ...\Hostx64\x64\cl.exe

Actual:
  $HostCompiler

Close this shell and initialize Developer PowerShell with:
 -Arch amd64 -HostArch amd64
"@
}

# ------------------------------------------------------------
# Verify every required file
# ------------------------------------------------------------

$RequiredFiles = [ordered]@{
    "llama.cpp source"          = "$Repo/CMakeLists.txt"
    "Android toolchain"         = $AndroidToolchain
    "CMake"                     = $CMake
    "Ninja"                     = $Ninja
    "Vulkan C++ header"         = "$VulkanInclude/vulkan/vulkan.hpp"
    "Vulkan C header"           = "$VulkanInclude/vulkan/vulkan.h"
    "Android Vulkan library"    = $VulkanLibrary
    "glslc"                     = $Glslc
    "SPIR-V CMake package"      = "$SpirvHeadersDir/SPIRV-HeadersConfig.cmake"
    "SPIR-V header"             = "$VulkanInclude/spirv/unified1/spirv.hpp"
}

foreach ($Entry in $RequiredFiles.GetEnumerator()) {
    if (-not (Test-Path $Entry.Value)) {
        throw "Missing $($Entry.Key): $($Entry.Value)"
    }

    Write-Host "[OK] $($Entry.Key)"
}

Write-Host ""
Write-Host "glslc version:"
& $Glslc --version

if ($LASTEXITCODE -ne 0) {
    throw "glslc verification failed."
}

# ------------------------------------------------------------
# Fix Git safe-directory warning
# ------------------------------------------------------------

git config --global --add safe.directory "D:/LLama.cpp"

git -C $Repo status --short

if ($LASTEXITCODE -ne 0) {
    throw "The llama.cpp Git repository could not be accessed."
}

Write-Host ""
Write-Host "llama.cpp revision:"

git -C $Repo log -1 --oneline

# ------------------------------------------------------------
# Delete the complete previous Android build
# ------------------------------------------------------------

if (Test-Path $Build) {
    Write-Host ""
    Write-Host "Deleting previous build: $Build"

    Remove-Item `
       -Path $Build `
       -Recurse `
       -Force
}

if (Test-Path $Build) {
    throw "The old build directory could not be deleted: $Build"
}

Write-Host "Old build directory removed."

# ------------------------------------------------------------
# Configure Android arm64-v8a with Vulkan
# ------------------------------------------------------------

$ConfigureArguments = @(
    "-G", "Ninja"

    "-S", $Repo
    "-B", $Build

    "-DCMAKE_MAKE_PROGRAM:FILEPATH=$Ninja"
    "-DCMAKE_TOOLCHAIN_FILE:FILEPATH=$AndroidToolchain"
    "-DCMAKE_BUILD_TYPE=Release"

    "-DANDROID_ABI=arm64-v8a"
    "-DANDROID_PLATFORM=android-28"

    "-DGGML_VULKAN=ON"
    "-DGGML_NATIVE=OFF"
    "-DGGML_CPU_ARM_ARCH=$CpuArmArch"
    "-DGGML_OPENMP=OFF"
    "-DGGML_CCACHE=OFF"

    "-DGGML_BUILD_TESTS=OFF"
    "-DGGML_BUILD_EXAMPLES=OFF"

    "-DBUILD_SHARED_LIBS=ON"

    "-DLLAMA_BUILD_COMMON=OFF"
    "-DLLAMA_BUILD_TOOLS=OFF"
    "-DLLAMA_BUILD_SERVER=OFF"
    "-DLLAMA_BUILD_APP=OFF"
    "-DLLAMA_BUILD_UI=OFF"
    "-DLLAMA_USE_PREBUILT_UI=OFF"
    "-DLLAMA_BUILD_EXAMPLES=OFF"
    "-DLLAMA_BUILD_TESTS=OFF"
    "-DLLAMA_BUILD_MTMD=ON"

    "-DVulkan_INCLUDE_DIR:PATH=$VulkanInclude"
    "-DVulkan_LIBRARY:FILEPATH=$VulkanLibrary"
    "-DVulkan_GLSLC_EXECUTABLE:FILEPATH=$Glslc"

    "-DSPIRV-Headers_DIR:PATH=$SpirvHeadersDir"
)

Write-Host ""
Write-Host "Configuring llama.cpp for Android Vulkan..."

& $CMake @ConfigureArguments

if ($LASTEXITCODE -ne 0) {
    throw "CMake configuration failed."
}

# ------------------------------------------------------------
# Verify important cached paths
# ------------------------------------------------------------

$Cache = "$Build/CMakeCache.txt"

Write-Host ""
Write-Host "Important CMake cache entries:"

Select-String `
   -Path $Cache `
   -Pattern @(
        "^ANDROID_ABI"
        "^ANDROID_PLATFORM"
        "^GGML_VULKAN"
        "^GGML_CPU_ARM_ARCH"
        "^Vulkan_INCLUDE_DIR"
        "^Vulkan_LIBRARY"
        "^Vulkan_GLSLC_EXECUTABLE"
        "^SPIRV-Headers_DIR"
    )

# ------------------------------------------------------------
# Verify the actual compile command picked up the -march flag
# ------------------------------------------------------------

$CompileCommandsPath = "$Build/compile_commands.json"

if (Test-Path $CompileCommandsPath) {
    $GgmlCpuLine = Select-String -Path $CompileCommandsPath -Pattern "march=$([regex]::Escape($CpuArmArch))" -SimpleMatch:$false
    if (-not $GgmlCpuLine) {
        Write-Warning "Could not find '-march=$CpuArmArch' in compile_commands.json - verify GGML_CPU_ARM_ARCH was actually applied to the ggml-cpu compile units."
    } else {
        Write-Host ""
        Write-Host "[OK] Confirmed -march=$CpuArmArch is present in the generated compile commands."
    }
}

# ------------------------------------------------------------
# Verify the generated Windows host toolchain
# ------------------------------------------------------------

$HostToolchain = "$Build/host-toolchain.cmake"

if (-not (Test-Path $HostToolchain)) {
    throw "The Vulkan host toolchain was not generated: $HostToolchain"
}

Write-Host ""
Write-Host "Generated Vulkan host toolchain:"
Get-Content $HostToolchain

$HostToolchainContents = Get-Content $HostToolchain -Raw

if ($HostToolchainContents -match "Android[\\/]Sdk[\\/]ndk") {
    throw "The host shader generator incorrectly selected the Android NDK compiler."
}

if ($HostToolchainContents -notmatch "Hostx64[\\/]+x64") {
    Write-Warning "The generated host toolchain does not appear to contain Hostx64\x64."
}

# ------------------------------------------------------------
# Build llama, mtmd, and their dependencies
# ------------------------------------------------------------
# (Not "--target llama" alone: this repo's own jniLibs need libmtmd.so too - see
# app/src/main/cpp/CMakeLists.txt's optional mtmd import - and mtmd is a sibling target of
# llama, not a dependency of it, so it must be requested explicitly or it silently won't build.)

Write-Host ""
Write-Host "Building the Android llama + mtmd targets..."

& $CMake `
   --build $Build `
   --target llama mtmd `
   --parallel 8

if ($LASTEXITCODE -ne 0) {
    throw "Android Vulkan build failed."
}

# ------------------------------------------------------------
# Display produced Android shared libraries
# ------------------------------------------------------------

$Libraries = Get-ChildItem `
   -Path $Build `
   -Recurse `
   -File `
   -Filter "*.so" |
    Sort-Object Name, FullName

if (-not $Libraries) {
    throw "Build completed but no Android .so libraries were found."
}

Write-Host ""
Write-Host "Android libraries produced:"

$Libraries |
    Select-Object Name, FullName |
    Format-Table -AutoSize

# ------------------------------------------------------------
# Sanity-check the CPU .so actually contains dotprod/i8mm code
# ------------------------------------------------------------

$ObjDump = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-objdump.exe"
$GgmlCpuLib = $Libraries | Where-Object { $_.Name -eq "libggml-cpu.so" } | Select-Object -First 1

if ($GgmlCpuLib -and (Test-Path $ObjDump)) {
    Write-Host ""
    Write-Host "Disassembling libggml-cpu.so to confirm SDOT/UDOT (dotprod) instructions are present..."
    $DotprodCount = (& $ObjDump -d $GgmlCpuLib.FullName | Select-String -Pattern "\bsdot\b|\budot\b").Count
    if ($DotprodCount -gt 0) {
        Write-Host "[OK] Found $DotprodCount dotprod (sdot/udot) instructions in libggml-cpu.so."
    } else {
        Write-Warning "No sdot/udot instructions found in libggml-cpu.so - GGML_CPU_ARM_ARCH may not have taken effect."
    }
}

Write-Host ""
Write-Host "Android Vulkan llama.cpp build completed successfully."
Write-Host "Point local.properties' llamacpp.dir at: $Repo"
