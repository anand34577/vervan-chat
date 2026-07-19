# R8 configuration — shrinking ON, obfuscation OFF.
#
# Why no obfuscation: this app's crash reporting is entirely on-device (CrashLogManager →
# Diagnostics → user shares plain text). Obfuscated frames would need a per-release mapping.txt
# retrace step nobody in that flow can run, making every field crash report useless. Dead-code
# and resource shrinking deliver the size win; obfuscation would only deliver unreadable logs.
-dontobfuscate

# Readable crash-log frames (goes with -dontobfuscate).
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*

# ---------------------------------------------------------------------------
# App JNI surface. llama_bridge.cpp resolves LlamaCppJni's callback by name at
# runtime (GetMethodID "onToken"), and the `external fun` declarations must keep
# their exact names/signatures for registration to match.
-keep class com.vervan.chat.llm.LlamaCppJni { *; }
-keep class com.vervan.chat.llm.LlamaCppJni$* { *; }
-keepclasseswithmembers class com.vervan.chat.** { native <methods>; }

# ---------------------------------------------------------------------------
# Inference runtimes — all JNI-backed; Java-side classes are looked up from native
# code, invisible to R8's reachability analysis.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# sherpa-onnx (vendored AAR: TTS voices, Whisper STT, Silero VAD) — pure JNI bridge.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ---------------------------------------------------------------------------
# Apache POI (legacy .doc/.xls only) — reflection-heavy record factories. Its optional
# desktop/XML dependencies don't exist on Android by design (see build.gradle.kts:
# deliberately NOT poi-ooxml), hence the dontwarn block.
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.commons.math3.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.xml.security.**
-dontwarn org.osgi.**
-dontwarn org.w3c.dom.svg.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn net.sf.saxon.**

# PDFBox-Android — loads fonts/codecs reflectively.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# commons-compress — only the bzip2 path is used (TTS voice .tar.bz2 archives); its many
# optional codec backends are absent on purpose.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
-dontwarn org.tukaani.**
-dontwarn org.objectweb.asm.**

# NanoHTTPD — instantiated directly, no reflection, but it references optional
# javax.net server bits guarded at runtime.
-dontwarn fi.iki.elonen.**

# Markwon's LaTeX extension bundles jlatexmath, which resolves resources by class name.
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn ru.noties.jlatexmath.**
