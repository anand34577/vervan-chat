// JNI bridge between com.vervan.chat.voice.WhisperCppJni (Kotlin) and whisper.cpp's C API,
// linked against a prebuilt libwhisper.so (see CMakeLists.txt — this project does not build
// whisper.cpp itself; the developer drops libwhisper.so + its ggml deps into jniLibs/<abi>/).
//
// VERSION-SENSITIVE API NOTE: written against the long-stable whisper.h surface
// (whisper_init_from_file_with_params / whisper_full / whisper_full_get_segment_text). The
// struct field names in whisper_full_params have been stable for years; if a future whisper.cpp
// renames one, the compiler will point at the exact line. The transcribe path is intentionally
// minimal — greedy decoding, no callbacks, no timestamps — matching how WhisperSttEngine uses
// sherpa-onnx (one shot in, one transcript out).

#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <string>
#include <thread>

#include "whisper.h"

#define LOG_TAG "vervan_whisper_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Same per-thread failure-message pattern as llama_bridge.cpp: a bare jlong handle has nowhere
// to carry a failure reason, so nativeGetLastError() is the JNI-side equivalent of reading
// `.message` off a thrown Kotlin exception.
thread_local std::string g_last_error;

void set_last_error(const std::string &msg) {
    g_last_error = msg;
    LOGE("%s", msg.c_str());
}

// One loaded whisper.cpp model + its context. One instance per nativeInit() call, freed by
// nativeFree() — mirrors WhisperCppSttEngine's single-engine-instance-per-load lifecycle.
// n_threads is captured at init and applied to every decode: whisper_full_params carries it
// per-call, but the value is a property of the loaded engine, not of an individual utterance.
struct WhisperSession {
    whisper_context *ctx = nullptr;
    int n_threads = 0;
};

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_vervan_chat_voice_WhisperCppJni_nativeInit(
    JNIEnv *env, jobject /*thiz*/, jstring modelPath, jint nThreads
) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        set_last_error("GetStringUTFChars failed for modelPath");
        return 0;
    }
    std::string modelPathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);

    // whisper_context_default_params() gained a use_gpu field later than the original struct;
    // leaving it at defaults matches whisper.cpp's own cli default (CPU on Android — no metal/
    // cuda here). The field's presence is the only thing most revisions disagree on; reading
    // defaults from the library instead of hardcoding keeps this forward-compatible.
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // CPU-only: whisper.cpp's Android build has no GPU backend wired here.

    whisper_context *ctx = whisper_init_from_file_with_params(modelPathStr.c_str(), cparams);
    if (ctx == nullptr) {
        set_last_error("whisper_init_from_file_with_params returned null for: " + modelPathStr);
        return 0;
    }
    // nThreads <= 0 means "let the bridge pick" (see WhisperCppJni.nativeInit's contract):
    // mirror whisper.cpp's own default of min(4, hardware_concurrency). Clamped to at least 1 so
    // a device reporting 0 concurrency can't produce an n_threads of 0, which whisper rejects.
    int threads = nThreads;
    if (threads <= 0) {
        const unsigned hw = std::thread::hardware_concurrency();
        threads = std::min(4, hw > 0 ? static_cast<int>(hw) : 1);
    }
    threads = std::max(1, threads);

    auto *session = new WhisperSession();
    session->ctx = ctx;
    session->n_threads = threads;
    LOGI("whisper.cpp model loaded (n_threads=%d): %s", threads, modelPathStr.c_str());
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_vervan_chat_voice_WhisperCppJni_nativeTranscribe(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jfloatArray samples, jint nSamples,
    jstring language, jboolean translate
) {
    auto *session = reinterpret_cast<WhisperSession *>(handle);
    if (session == nullptr || session->ctx == nullptr) {
        set_last_error("nativeTranscribe called with null session");
        return nullptr;
    }

    // nSamples is caller-supplied and must never be trusted to address past the array — clamp it
    // to the real length rather than handing whisper.cpp an out-of-bounds pointer range.
    const jsize arrayLen = env->GetArrayLength(samples);
    const int sampleCount = std::min(static_cast<int>(arrayLen), std::max(0, static_cast<int>(nSamples)));
    if (sampleCount <= 0) {
        set_last_error("nativeTranscribe called with no samples");
        return nullptr;
    }

    // Pin the float array once and hand whisper.cpp a raw pointer — copying 30s of 16kHz audio
    // (~480k floats = ~2MB) per call would be pointless churn on the decode path.
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    if (pcm == nullptr) {
        set_last_error("GetFloatArrayElements returned null");
        return nullptr;
    }

    // whisper.cpp resolves params.language through whisper_lang_id(), which returns -1 for an
    // unrecognized code — and whisper then builds its prompt with a bogus language token instead
    // of failing, producing silently wrong output. Validate here and fall back to auto-detect, so
    // no caller can poison a decode by passing something that isn't an ISO-639-1 code.
    const char *langC = env->GetStringUTFChars(language, nullptr);
    std::string langStr(langC != nullptr ? langC : "auto");
    if (langC != nullptr) env->ReleaseStringUTFChars(language, langC);
    if (langStr.empty() || (langStr != "auto" && whisper_lang_id(langStr.c_str()) == -1)) {
        LOGE("unknown whisper language '%s' — falling back to auto-detect", langStr.c_str());
        langStr = "auto";
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.no_timestamps = true;
    params.single_segment = false;
    params.no_context = true;
    params.translate = translate == JNI_TRUE;
    params.n_threads = session->n_threads;
    // language pointer must outlive whisper_full(); langStr owns the storage, c_str() is stable
    // for the duration of the std::string's life, which covers the whole call.
    params.language = langStr.c_str();

    int result = whisper_full(session->ctx, params, pcm, sampleCount);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT); // JNI_ABORT: we never modify pcm.

    if (result != 0) {
        set_last_error("whisper_full failed with code " + std::to_string(result));
        return nullptr;
    }

    // Concatenate every decoded segment into one transcript — the realtime voice pipeline feeds
    // one VAD-endpointed utterance and wants one string back, not a list of segments with
    // timestamps (matching WhisperSttEngine's single-text contract on the sherpa-onnx side).
    const int nSegments = whisper_full_n_segments(session->ctx);
    std::string transcript;
    for (int i = 0; i < nSegments; i++) {
        const char *seg = whisper_full_get_segment_text(session->ctx, i);
        if (seg != nullptr) transcript.append(seg);
    }

    // Trim leading/trailing whitespace — sherpa's getResult().text.trim() equivalent. A blank
    // result means whisper decoded nothing useful, and the caller falls through to the next STT
    // tier (see RealtimeVoiceController's 3-tier policy), so returning an empty string here would
    // be indistinguishable from a real blank decode; instead null signals "decode produced
    // nothing" and null is what transcribe() returns to the Kotlin side either way.
    auto notSpace = [](unsigned char c) { return !isspace(c); };
    transcript.erase(transcript.begin(), std::find_if(transcript.begin(), transcript.end(), notSpace));
    transcript.erase(std::find_if(transcript.rbegin(), transcript.rend(), notSpace).base(), transcript.end());

    if (transcript.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(transcript.c_str());
}

JNIEXPORT void JNICALL
Java_com_vervan_chat_voice_WhisperCppJni_nativeFree(
    JNIEnv *env, jobject /*thiz*/, jlong handle
) {
    auto *session = reinterpret_cast<WhisperSession *>(handle);
    if (session == nullptr) return;
    if (session->ctx != nullptr) {
        whisper_free(session->ctx);
        session->ctx = nullptr;
    }
    delete session;
    LOGI("whisper.cpp session freed");
}

JNIEXPORT jstring JNICALL
Java_com_vervan_chat_voice_WhisperCppJni_nativeGetLastError(
    JNIEnv *env, jobject /*thiz*/
) {
    if (g_last_error.empty()) return nullptr;
    return env->NewStringUTF(g_last_error.c_str());
}

} // extern "C"
