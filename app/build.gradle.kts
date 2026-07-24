import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// llama.cpp (GGUF) backend support — optional, machine-local. `llamacpp.dir` in local.properties
// (uncommitted, same convention as `sdk.dir`) points at a llama.cpp *source* checkout; Gradle
// compiles it for Android/Vulkan itself via the `buildLlamaCppNative` task below, so no manual
// build step is needed. Absent property -> the whole native target is skipped, and a checkout
// without llama.cpp still compiles/runs everything else normally.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val llamaCppDir: String? = localProperties.getProperty("llamacpp.dir")

// Availability is decided by the *source* checkout, not by build outputs: the libraries may not
// exist yet at configuration time precisely because buildLlamaCppNative hasn't run.
val llamaCppAvailable = llamaCppDir?.let { dir ->
    File(dir, "CMakeLists.txt").isFile && File(dir, "include/llama.h").isFile
} == true
// The build script always passes -DLLAMA_BUILD_MTMD=ON, so vision support tracks availability.
val llamaCppVisionAvailable = llamaCppAvailable
if (llamaCppDir != null && !llamaCppAvailable) {
    logger.warn("llamacpp.dir is set but does not look like a llama.cpp checkout (no CMakeLists.txt / include/llama.h); GGUF support is disabled.")
}

// ARM ABIs to build llama.cpp for. Both are on by default so GGUF works on every ARM Android
// device the app installs on — LiteRT-LM (MediaPipe) is 64-bit-only upstream, so on 32-bit
// devices llama.cpp is the *only* inference backend (guarded at runtime in LlmEngine). Building
// armeabi-v7a roughly doubles native build time; set llamacpp.abis=arm64-v8a to skip it.
val llamaCppAbis = (localProperties.getProperty("llamacpp.abis") ?: "arm64-v8a,armeabi-v7a")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
// -march for the arm64 CPU backend. The default targets ARMv8.2 (dotprod + i8mm): every
// mainstream arm64 phone since ~2019, but NOT early ARMv8.0 chips that can still run API 26.
// Set llamacpp.cpuArch=armv8-a for maximum device compatibility at a cost to CPU throughput.
// (Vulkan is the primary path either way; this only affects the CPU fallback backend.)
val llamaCppCpuArch = localProperties.getProperty("llamacpp.cpuArch") ?: "armv8.2-a+dotprod+i8mm"
// Escape hatch: set llamacpp.autobuild=false to manage the native build by hand.
val llamaCppAutoBuild = localProperties.getProperty("llamacpp.autobuild")?.toBoolean() != false

// whisper.cpp (GGML/GGML speech-to-text) backend — optional, PRE-BUILT. Unlike llama.cpp there is
// no source checkout / build script: the developer builds whisper.cpp for Android themselves and
// drops the resulting libwhisper.so (plus ggml .so deps if the ggml revision differs from
// llama.cpp's) into app/src/main/jniLibs/<abi>/. Availability is decided by libwhisper.so being
// present for at least one configured ABI. `whispercpp.dir`, if set, only supplies the whisper.h
// header for the JNI bridge compile — it is never compiled from here.
val whisperCppDir: String? = localProperties.getProperty("whispercpp.dir")
val whisperCppAbis = listOf("arm64-v8a", "armeabi-v7a").filter { abi ->
    file("src/main/jniLibs/$abi/libwhisper.so").isFile
}
val whisperCppAvailable = whisperCppAbis.isNotEmpty()

// Per-ABI output tree written by the build script. Falls back to the legacy single-ABI layout
// so an existing hand-built checkout keeps working without a rebuild.
fun llamaCppLibsDirFor(abi: String): File? {
    val dir = llamaCppDir ?: return null
    val perAbi = File(dir, "build-android/$abi/bin")
    if (File(perAbi, "libllama.so").isFile) return perAbi
    val legacy = File(dir, "build-android/bin")
    if (abi == "arm64-v8a" && File(legacy, "libllama.so").isFile) return legacy
    return perAbi
}
val llamaCpp32Available = llamaCppAvailable && llamaCppAbis.contains("armeabi-v7a")

android {
    namespace = "com.vervan.chat"
    compileSdk = 35
    ndkVersion = "28.1.13356709"

    defaultConfig {
        applicationId = "com.vervan.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("boolean", "LLAMA_CPP_AVAILABLE", llamaCppAvailable.toString())
        buildConfigField("boolean", "LLAMA_CPP_VISION_AVAILABLE", llamaCppVisionAvailable.toString())
        buildConfigField("boolean", "WHISPER_CPP_AVAILABLE", whisperCppAvailable.toString())

        // armeabi-v7a is always packaged so 32-bit devices can install; on them LiteRT-LM is
        // unavailable (MediaPipe ships no 32-bit libs) and GGUF works only if a 32-bit llama.cpp
        // build was supplied via llamacpp.dir32.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        if (llamaCppAvailable || whisperCppAvailable) {
            externalNativeBuild {
                cmake {
                    // LLAMA_CPP_LIBS_DIR is left to CMake to derive per-ABI from ANDROID_ABI —
                    // a single Gradle-side value can't cover both ABIs in one invocation.
                    arguments += listOf(
                        "-DLLAMA_CPP_DIR=${llamaCppDir ?: ""}",
                        "-DWHISPER_CPP_AVAILABLE=${whisperCppAvailable}",
                        "-DWHISPER_CPP_DIR=${whisperCppDir ?: ""}",
                        "-DANDROID_STL=c++_shared"
                    )
                    // The JNI bridges link against native libs for their ABI — only build each
                    // bridge for the ABIs its underlying native lib was actually built for.
                    abiFilters += (if (llamaCppAvailable) llamaCppAbis else emptyList()) + whisperCppAbis
                }
            }
        }
    }

    if (llamaCppAvailable || whisperCppAvailable) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        release {
            // Shrinking only — obfuscation is disabled in proguard-rules.pro because crash
            // reporting is on-device plain text (CrashLogManager) with no retrace step.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            // Plain JVM unit tests (testDebugUnitTest) run without the real Android framework —
            // any unmocked android.* call (e.g. Log.w/Log.e) throws instead of no-opping unless
            // this is set. Coordinator logic increasingly logs on its failure/warning paths
            // (§9/§11 diagnostics), so tests exercising those paths need this to run at all.
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // LiteRT-LM bundles LiteRT native libs for generation, and the standalone LiteRT
            // runtime bundles the newer copies needed by raw EmbeddingGemma. Package the first
            // resolved set; assemble verification checks libLiteRt.so stays the 2.1.6 library.
            pickFirsts += "**/libLiteRt*.so"
        }
    }

    // AAPT compresses assets by default, which corrupts the alignment sherpa-onnx's ONNX
    // runtime needs when it mmaps a model straight out of the APK (the bundled Silero VAD
    // model under assets/vad/, see VoiceActivityDetector) — must ship uncompressed.
    androidResources {
        noCompress += listOf("onnx")
    }
}

val verifyLlamaCppRelease by tasks.registering {
    doLast {
        check(llamaCppAvailable) {
            "Release builds require llama.cpp. Set llamacpp.dir in local.properties to a llama.cpp checkout; " +
                "Gradle builds it for Android/Vulkan automatically."
        }
        // Guards against a stale or partial native build silently shipping in a release APK.
        // Vulkan is only expected on 64-bit — see the ABI table in the build script.
        llamaCppAbis.forEach { abi ->
            val libs = llamaCppLibsDirFor(abi)
            val required = buildList {
                add("libllama.so")
                add("libggml-cpu.so")
                if (abi == "arm64-v8a") add("libggml-vulkan.so")
            }
            required.forEach { lib ->
                check(libs != null && File(libs, lib).isFile) {
                    "llama.cpp library $lib for $abi is missing (expected in $libs). Run :app:buildLlamaCppNative."
                }
            }
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyLlamaCppRelease)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    // Loads the vendored Mermaid 10.9.6 browser bundle from APK assets over a safe local
    // HTTPS origin. Runtime network loads are disabled in OfflineMermaidView.
    implementation("androidx.webkit:webkit:1.16.0")

    // CommonMark, tables, tasks, links, and LaTeX rendered entirely on-device.
    val markwonVersion = "4.6.2"
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:inline-parser:$markwonVersion")
    implementation("io.noties.markwon:ext-latex:$markwonVersion")
    implementation("io.noties.markwon:ext-tables:$markwonVersion")
    implementation("io.noties.markwon:ext-tasklist:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")
    implementation("io.noties.markwon:linkify:$markwonVersion")

    // Local persistence — chats, messages, personas, model registry
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // On-device Gemma inference runtime (LiteRT-LM based). Bumped 0.10.0 -> 0.13.1 to match
    // a verified-working reference build; the old 0.10.0 + a stray, unused
    // com.google.mediapipe:tasks-genai:0.10.27 (LlmEngine only ever calls into
    // com.google.ai.edge.litertlm.*, never tasks.genai.*) shipped two different native
    // inference runtimes' .so files in the same APK — a version-skew condition that surfaces
    // as a native crash/UnsatisfiedLinkError at Engine.initialize(), invisible to
    // compileDebugKotlin since it's a runtime linking failure, not a compile error.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    // EmbeddingGemma / local text embeddings for semantic retrieval — MediaPipe TextEmbedder
    // only loads Task Bundle-packaged models. A raw exported .tflite graph (no bundled
    // tokenizer/metadata) needs the plain TFLite Interpreter instead, which EmbeddingEngine
    // falls back to (see EmbeddingEngine.kt / RawTfliteEmbedder.kt).
    implementation("com.google.mediapipe:tasks-text:0.10.35")
    // Raw EmbeddingGemma exports use newer TFLite ops (for example EMBEDDING_LOOKUP v4).
    // The legacy org.tensorflow:tensorflow-lite line stops at 2.16.1, which cannot load them,
    // so use the current LiteRT runtime. It preserves the org.tensorflow.lite Java API.
    implementation("com.google.ai.edge.litert:litert:2.1.6")

    // Realtime voice pipeline (see com.vervan.chat.voice) — tiered on-device TTS, all with
    // confirmed Hindi support (Android's own system TTS varies by device/installed voice
    // data, hence the tiering). Piper/Kokoro run through sherpa-onnx (Apache-2.0) rather than
    // Piper's own GPL-3.0 codebase, so no copyleft exposure; Piper's hi_IN/en_IN voice .onnx
    // files are MIT-licensed per the piper-voices repo. sherpa-onnx also supplies the bundled
    // Silero-VAD (MIT) used for STT endpointing and barge-in — one less dependency than adding
    // a separate VAD library.
    //
    // sherpa-onnx has no published Maven/JitPack coordinate (confirmed against a live Gradle
    // sync — its own Android docs describe building native .so files from source, not a
    // resolvable dependency). Vendored locally instead: drop the built/downloaded AAR into
    // app/libs/ (see the placeholder note there) and this fileTree dependency picks it up.
    // Empty libs/ resolves to zero files with no build error, so this line is safe to leave
    // in place before the AAR exists — PiperTtsEngine/KokoroTtsEngine/VoiceActivityDetector
    // just won't compile until it's there.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // Piper/Kokoro voices ship from sherpa-onnx's GitHub releases as .tar.bz2 archives
    // (model.onnx + tokens.txt + shared espeak-ng-data/ per voice) — Android has no built-in
    // bzip2 support, and this is exactly commons-compress's job; avoids hand-rolling a bzip2
    // decoder for what TtsModelDownloadManager.downloadArchiveVoice needs.
    implementation("org.apache.commons:commons-compress:1.26.1")
    // Supertonic's Android SDK (ai.supertone:supertonic-android, per the original spec) does
    // not appear to be publicly documented or Maven-resolvable at all — its own GitHub repo
    // lists iOS/Flutter/Java/web support but nothing for Android. Disabled pending a real
    // integration path: see SupertonicTtsEngine.kt.disabled.

    // PDF text extraction for document import
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // On-device OCR for scanned PDFs (spec §13.3/40.27) — the (non play-services) "text-recognition"
    // artifact bundles its model in the APK, so it works with no network at all, unlike
    // com.google.android.gms:play-services-mlkit-text-recognition which fetches the model on first use.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // HTML extraction with heading/list structure preserved — pure Java, no AWT/StAX, safe on
    // Android (unlike Apache POI's OOXML modules, see below).
    implementation("org.jsoup:jsoup:1.17.2")
    // Legacy binary Office formats (.doc/.xls, pre-2007 OLE2 Compound File format) ONLY —
    // deliberately NOT poi-ooxml (.docx/.xlsx/.pptx): poi-ooxml needs javax.xml.stream (StAX),
    // which Android's core library doesn't ship, and commonly needs java.awt classes Android
    // also lacks — a well-known POI-on-Android breakage. `poi` (HSSF, old .xls) and
    // `poi-scratchpad` (HWPF, old .doc) only touch POIFS, a self-contained OLE2 reader with no
    // XML/AWT dependency, so they're safe. .docx/.xlsx/.pptx stay on this app's existing
    // hand-rolled zip/XML extraction (TextExtractor.kt) instead of poi-ooxml.
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")
    // EXIF orientation correction for attached photos (Phase 7, spec §13) — many cameras
    // store images "sideways" with an orientation tag rather than actually rotating pixels.
    implementation("androidx.exifinterface:exifinterface:1.3.2")

    testImplementation("junit:junit:4.13.2")
    // Android's android.jar ships org.json as a throwing stub (real parsing is stripped out),
    // so plain JVM unit tests that exercise JSONObject parsing (ToolCallParserTest) need the
    // real reference implementation on the test classpath instead.
    testImplementation("org.json:json:20240303")

    // Settings storage
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // App lock (Phase A: privacy hardening) — BiometricPrompt for fingerprint/face, and
    // EncryptedSharedPreferences (Android Keystore-backed) for the PIN hash+salt. Standard
    // AndroidX libraries for this exact job, not custom crypto.
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // ProcessLifecycleOwner — detects the whole app going to background/foreground, for
    // auto-lock-on-background instead of a per-Activity onPause (which also fires during
    // in-app navigation, not just when the user actually leaves the app).
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Local OpenAI-compatible API server (Phase J) — the standard lightweight embedded HTTP
    // server for Android: one small artifact, no transitive networking stack, long track
    // record in exactly this embedded-server role. Not Ktor's server engine, which pulls in a
    // much larger dependency graph for what's a handful of endpoints here.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// --- llama.cpp native build ------------------------------------------------------------------
// Compiles llama.cpp for Android with the Vulkan backend, one output tree per ABI, so that
// pointing llamacpp.dir at a plain source checkout is the only setup a developer does.
//
// It runs as a separate CMake invocation rather than an add_subdirectory() inside the app's own
// externalNativeBuild because ggml's Vulkan backend builds `vulkan-shaders-gen` for the *host*:
// that needs an MSVC (or host clang/gcc) toolchain and a Vulkan SDK on PATH, which Gradle's
// CMake invocation has no way to set up. The script handles that — including importing the
// vcvars64 environment — so no Developer PowerShell is needed either.
val llamaCppBuildScript = rootProject.file("scripts/build-llama-android-vulkan.ps1")

val buildLlamaCppNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds llama.cpp (Vulkan + CPU) for Android into <llamacpp.dir>/build-android/<abi>/bin"
    onlyIf { llamaCppAvailable && llamaCppAutoBuild && llamaCppBuildScript.isFile }

    // Re-run when the sources, the toolchain choice, or the script itself changes. HEAD covers
    // `git pull` in the llama.cpp checkout without fingerprinting tens of thousands of files.
    inputs.file(llamaCppBuildScript)
    // The patches are compiled into the libraries, so editing one has to invalidate the build
    // exactly like editing the script does.
    inputs.dir(rootProject.file("scripts/patches")).optional()
    inputs.property("abis", llamaCppAbis)
    inputs.property("cpuArch", llamaCppCpuArch)
    if (llamaCppDir != null) {
        val head = File(llamaCppDir, ".git/HEAD")
        if (head.isFile) inputs.file(head).optional()
    }
    // Declaring the produced libraries as outputs is what makes this task skippable: once they
    // exist and nothing above changed, Gradle's up-to-date check keeps app builds fast.
    llamaCppAbis.forEach { abi ->
        llamaCppLibsDirFor(abi)?.let { outputs.dir(it) }
    }

    executable = "powershell.exe"
    args(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", llamaCppBuildScript.absolutePath,
        "-Abi", llamaCppAbis.joinToString(","),
        "-CpuArmArch", llamaCppCpuArch
        // -ApiLevel is intentionally left at the script's default (28, not the app's minSdk of
        // 26) because ggml's Vulkan backend needs Vulkan 1.1 symbols that Android's libvulkan.so
        // only exports from API 28. See the parameter's comment in the script.
    )
}

// Copies the built llama.cpp shared libraries into jniLibs so they actually land in the APK —
// Gradle's CMake integration only auto-packages libraries built by *this* project's CMake
// invocation; these are IMPORTED, so they need an explicit copy step. Same loose-.so-under-
// jniLibs/ precedent this repo already uses for LiteRT-LM's accelerator libraries.
val syncLlamaCppLibs = llamaCppAbis.map { abi ->
    tasks.register<Copy>("syncLlamaCppLibs${abi.replace("-", "")}") {
        dependsOn(buildLlamaCppNative)
        val libsDir = llamaCppLibsDirFor(abi)
        onlyIf { libsDir != null && libsDir.isDirectory }
        from(libsDir ?: file(".")) {
            include("libllama.so", "libggml*.so", "libmtmd.so", "libc++_shared.so")
        }
        into("src/main/jniLibs/$abi")
    }
}

tasks.named("preBuild") {
    dependsOn(syncLlamaCppLibs)
}

// The JNI bridge links directly against libllama.so, so llama.cpp must be built before any
// native compile task runs — preBuild ordering alone doesn't guarantee that for CMake tasks.
tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }
    .configureEach { dependsOn(buildLlamaCppNative) }

// verifyLlamaCppRelease inspects the *result* of the native build, so that has to happen first.
verifyLlamaCppRelease.configure { dependsOn(buildLlamaCppNative) }
