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
// (uncommitted, same convention as `sdk.dir`) points at a llama.cpp checkout that's already been
// built for Android separately (this project does not build llama.cpp itself — see
// app/src/main/cpp/CMakeLists.txt). Absent property -> the whole native target is skipped, so a
// checkout without llama.cpp built still compiles/runs everything else normally.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val llamaCppDir: String? = localProperties.getProperty("llamacpp.dir")
val llamaCppLibsDir = llamaCppDir?.let { "$it/build-android/bin" }
val llamaCppRequiredFiles = listOf(
    "include/llama.h",
    "build-android/bin/libllama.so",
    "build-android/bin/libggml.so",
    "build-android/bin/libggml-base.so",
    "build-android/bin/libggml-cpu.so",
    "build-android/bin/libggml-vulkan.so"
)
val llamaCppAvailable = llamaCppDir?.let { dir ->
    llamaCppRequiredFiles.all { File(dir, it).isFile }
} == true
val llamaCppVisionAvailable = llamaCppAvailable && File(llamaCppDir!!, "build-android/bin/libmtmd.so").isFile
if (llamaCppDir != null && !llamaCppAvailable) {
    logger.warn("llamacpp.dir is set, but required headers/libraries are missing; GGUF support is disabled for debug builds.")
}

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

        if (llamaCppAvailable) {
            // Only arm64-v8a prebuilt libs exist (matches your build-android output) — without
            // this, AGP also tries armeabi-v7a/x86/x86_64 and fails linking against them.
            ndk { abiFilters += "arm64-v8a" }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DLLAMA_CPP_DIR=$llamaCppDir",
                        "-DLLAMA_CPP_LIBS_DIR=$llamaCppLibsDir",
                        "-DANDROID_STL=c++_shared"
                    )
                    abiFilters += "arm64-v8a"
                }
            }
        }
    }

    if (llamaCppAvailable) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
            "Release builds require a complete llama.cpp Android/Vulkan build. Set llamacpp.dir in local.properties."
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

// Copies the user's separately-built llama.cpp shared libraries into jniLibs so they actually
// land in the APK — Gradle's CMake integration only auto-packages libraries *built* by this
// project's own CMake invocation; these are IMPORTED (prebuilt elsewhere), so they need this
// explicit copy step, same loose-.so-under-jniLibs/ precedent this repo already uses for
// LiteRT-LM's supplementary accelerator libraries. No-op (and no error) when llamacpp.dir isn't
// set, or when the expected .so files aren't there yet (e.g. build still in progress).
tasks.register<Copy>("syncLlamaCppLibs") {
    onlyIf { llamaCppLibsDir != null && file(llamaCppLibsDir).exists() }
    from(llamaCppLibsDir ?: ".") {
        include("libllama.so", "libggml*.so", "libmtmd.so", "libc++_shared.so")
    }
    into("src/main/jniLibs/arm64-v8a")
}

tasks.named("preBuild") {
    dependsOn("syncLlamaCppLibs")
}
