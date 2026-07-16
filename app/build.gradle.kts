import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.vervan.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vervan.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // ponytail: the "not compatible with 16 KB devices" warning traces to exactly one
    // bundled .so — libmediapipe_tasks_text_jni.so from tasks-text:0.10.21 (embeddings),
    // whose LOAD segments are 4 KB-aligned; verified with the NDK's llvm-readelf that every
    // other native lib here (tasks-genai, MLKit OCR, AndroidX) is already 16 KB-aligned.
    // 0.10.21 is the only tasks-text build in the offline cache, so there's no newer,
    // aligned build to pull in yet. Forcing legacy (compressed, extract-on-install)
    // packaging sidesteps the check entirely — Google's own 16 KB guidance notes
    // extractNativeLibs="true" APKs aren't subject to it, since libs land on disk at
    // install time instead of being mmap'd straight out of the APK. Costs a bit of app
    // size and first-install extraction time; revert once tasks-text ships 16 KB-aligned.
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    implementation("com.google.mediapipe:tasks-text:0.10.21")
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
