// JNI bridge between com.vervan.chat.voice.ShrutiJni (Kotlin) and Cactus's needle engine C API
// (needle.h), linked against the prebuilt static libneedle.a that Gradle's `fetchNeedleEngine`
// task downloads (pinned revision + sha256) from huggingface.co/Cactus-Compute/needle3.
//
// The needle runtime holds ONE process-global, non-thread-safe speech model. Every entry point
// below is serialized through g_mutex, and the Kotlin side (ShrutiSttEngine) serializes again with
// its own coroutine Mutex, so a release can never race a decode.
//
// needle_load() reads the .cact container in place (the format is designed to be mapped), so the
// bytes handed to it must stay valid for as long as that model is loaded. The file is mmap'ed
// read-only and the mapping is kept until a different model replaces it or nativeUnload() runs.

#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "needle.h"

#define LOG_TAG "vervan_needle_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;
std::string g_last_error;
std::string g_loaded_path;
void *g_map = nullptr;
size_t g_map_size = 0;

// The engine's JSON results are small (a transcript plus optional per-word timings); 30 s of
// speech with word timestamps stays well under this. A result that does not fit is reported as
// an error by needle (negative return), and the call is retried once with a larger buffer.
constexpr int kInitialOutBytes = 1 << 18;   // 256 KB
constexpr int kMaxOutBytes = 1 << 23;       // 8 MB

void set_error(const std::string &msg) {
    g_last_error = msg;
    LOGE("%s", msg.c_str());
}

void unmap_locked() {
    if (g_map != nullptr) {
        munmap(g_map, g_map_size);
        g_map = nullptr;
        g_map_size = 0;
    }
    g_loaded_path.clear();
}

std::string engine_error(const char *what, int rc) {
    const char *detail = needle_last_error();
    std::string msg = std::string(what) + " failed (" + std::to_string(rc) + ")";
    if (detail != nullptr && *detail != '\0') msg += ": " + std::string(detail);
    return msg;
}

// Copies a Java float[] (16 kHz mono, [-1, 1]) into a native vector.
bool read_pcm(JNIEnv *env, jfloatArray samples, jint n, std::vector<float> &out) {
    if (samples == nullptr || n <= 0) return false;
    jsize len = env->GetArrayLength(samples);
    if (n > len) n = len;
    out.resize(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, out.data());
    return !env->ExceptionCheck();
}

const char *opt_chars(JNIEnv *env, jstring s) {
    return s == nullptr ? nullptr : env->GetStringUTFChars(s, nullptr);
}

void release_chars(JNIEnv *env, jstring s, const char *c) {
    if (s != nullptr && c != nullptr) env->ReleaseStringUTFChars(s, c);
}

// Runs one needle call that writes JSON into a caller buffer, growing the buffer once if needed.
// Returns the JSON as raw UTF-8 bytes (decoded on the Kotlin side): NewStringUTF expects
// *modified* UTF-8 and would mangle supplementary characters in a transcript.
template <typename Fn>
jbyteArray call_json(JNIEnv *env, const char *what, Fn fn) {
    for (int cap = kInitialOutBytes; cap <= kMaxOutBytes; cap *= 8) {
        std::vector<char> out(static_cast<size_t>(cap), '\0');
        int rc = fn(out.data(), cap);
        if (rc >= 0) {
            out[static_cast<size_t>(cap) - 1] = '\0';
            jsize len = static_cast<jsize>(strlen(out.data()));
            jbyteArray bytes = env->NewByteArray(len);
            if (bytes != nullptr) env->SetByteArrayRegion(bytes, 0, len, reinterpret_cast<const jbyte *>(out.data()));
            return bytes;
        }
        const char *detail = needle_last_error();
        bool too_small = detail != nullptr && std::string(detail).find("capacity") != std::string::npos;
        if (!too_small) {
            set_error(engine_error(what, rc));
            return nullptr;
        }
    }
    set_error(std::string(what) + " failed: result larger than the output buffer");
    return nullptr;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeLoad(JNIEnv *env, jobject, jstring jpath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *cpath = env->GetStringUTFChars(jpath, nullptr);
    std::string path = cpath != nullptr ? cpath : "";
    if (cpath != nullptr) env->ReleaseStringUTFChars(jpath, cpath);

    if (!g_loaded_path.empty() && g_loaded_path == path && g_map != nullptr) return JNI_TRUE;

    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        set_error("cannot open " + path);
        return JNI_FALSE;
    }
    struct stat st {};
    if (fstat(fd, &st) != 0 || st.st_size <= 0) {
        close(fd);
        set_error("empty or unreadable model file " + path);
        return JNI_FALSE;
    }
    size_t size = static_cast<size_t>(st.st_size);
    void *map = mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (map == MAP_FAILED) {
        set_error("mmap failed for " + path);
        return JNI_FALSE;
    }

    int rc = needle_load(static_cast<const unsigned char *>(map), static_cast<unsigned long long>(size));
    if (rc < 0) {
        set_error(engine_error("needle_load", rc));
        munmap(map, size);
        return JNI_FALSE;
    }
    if ((needle_models() & NEEDLE_SPEECH) == 0) {
        set_error("the file is not a needle speech (Whistle/Shruti) model: " + path);
        munmap(map, size);
        return JNI_FALSE;
    }
    // The new model replaced the previous speech model inside the runtime, so the old mapping is
    // no longer referenced and can go.
    unmap_locked();
    g_map = map;
    g_map_size = size;
    g_loaded_path = path;
    LOGI("needle speech model loaded: %s (%zu bytes)", path.c_str(), size);
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeTranscribe(JNIEnv *env, jobject, jfloatArray samples, jint n,
                                                       jstring jlanguage, jstring jkeywords, jboolean wordTimestamps) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_map == nullptr) {
        set_error("no speech model loaded");
        return nullptr;
    }
    std::vector<float> pcm;
    if (!read_pcm(env, samples, n, pcm)) {
        set_error("no audio");
        return nullptr;
    }
    const char *language = opt_chars(env, jlanguage);
    const char *keywords = opt_chars(env, jkeywords);
    jbyteArray result = call_json(env, "needle_transcribe", [&](char *out, int cap) {
        return needle_transcribe(pcm.data(), static_cast<int>(pcm.size()), language, keywords,
                                 wordTimestamps ? 1 : 0, out, cap);
    });
    release_chars(env, jlanguage, language);
    release_chars(env, jkeywords, keywords);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeStreamProcess(JNIEnv *env, jobject, jfloatArray samples, jint n,
                                                          jstring jlanguage, jstring jkeywords) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_map == nullptr) {
        set_error("no speech model loaded");
        return nullptr;
    }
    std::vector<float> pcm;
    if (!read_pcm(env, samples, n, pcm)) {
        set_error("no audio");
        return nullptr;
    }
    const char *language = opt_chars(env, jlanguage);
    const char *keywords = opt_chars(env, jkeywords);
    jbyteArray result = call_json(env, "needle_stream_transcribe_process", [&](char *out, int cap) {
        return needle_stream_transcribe_process(pcm.data(), static_cast<int>(pcm.size()), language, keywords, out, cap);
    });
    release_chars(env, jlanguage, language);
    release_chars(env, jkeywords, keywords);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeStreamStop(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_map == nullptr) {
        set_error("no speech model loaded");
        return nullptr;
    }
    return call_json(env, "needle_stream_transcribe_stop", [&](char *out, int cap) {
        return needle_stream_transcribe_stop(out, cap);
    });
}

JNIEXPORT void JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeReset(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_map != nullptr) needle_reset();
}

// The runtime has no "free model" call: the mapping is kept (the model is ~30 MB and read in
// place) and only forgotten so the next nativeLoad() of a different file swaps it cleanly. A
// load of the SAME path after this re-maps and re-loads, which is what the Kotlin side expects
// after a release.
JNIEXPORT void JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeForget(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_loaded_path.clear();
}

JNIEXPORT jstring JNICALL
Java_com_vervan_chat_voice_ShrutiJni_nativeGetLastError(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_last_error.empty()) return nullptr;
    return env->NewStringUTF(g_last_error.c_str());
}

} // extern "C"
