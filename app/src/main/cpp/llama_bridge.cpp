// JNI bridge between com.vervan.chat.llm.LlamaCppJni (Kotlin) and llama.cpp's C API, linked
// against a prebuilt libllama.so/libggml*.so (see CMakeLists.txt — this project does not build
// llama.cpp itself, only links against the user's own separately-built output).
//
// VERSION-SENSITIVE API NOTE: this is written against a recent llama.cpp API surface and has
// not been compiled against real headers (the native build wasn't finished when this was
// written). The handful of call sites most likely to need adjusting for your exact checkout:
//   - llama_model_load_from_file()   (older llama.cpp: llama_load_model_from_file)
//   - llama_init_from_model()        (older llama.cpp: llama_new_context_with_model)
//   - llama_get_memory()/llama_memory_seq_rm()  (older llama.cpp: llama_kv_cache_seq_rm(ctx,...) directly)
//   - the mtmd_* function names under VERVAN_HAS_MTMD (mtmd's API has moved more than llama.cpp's)
// If the compiler errors on any of these, it'll point at the exact line — swap in whatever your
// installed llama.h/mtmd.h actually declares.

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

#ifdef VERVAN_HAS_MTMD
#include "mtmd.h"
#include "mtmd-helper.h"
#endif

#define LOG_TAG "vervan_llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Mirrors LlmEngine's per-call exception-message propagation — a bare jlong handle has nowhere
// else to carry a failure reason, so nativeGetLastError() is the JNI-side equivalent of reading
// `.message` off a thrown Kotlin exception. thread_local since load/generate calls for different
// sessions can run on different threads.
thread_local std::string g_last_error;

void set_last_error(const std::string &msg) {
    g_last_error = msg;
    LOGE("%s", msg.c_str());
}

// llama_token_to_piece() yields raw token bytes that don't necessarily align to UTF-8 codepoint
// boundaries (a multibyte char, e.g. an emoji, can be split across tokens) — env->NewStringUTF()
// requires *complete*, Modified-UTF-8-encoded input, so feeding it partial/raw-UTF-8 bytes
// crashes the JVM (JNI DETECTED ERROR / SIGABRT). Buffer bytes and only flush whole codepoints,
// decoded straight to UTF-16 (env->NewString) to sidestep Modified-UTF-8 entirely.
int utf8SeqLen(unsigned char c) {
    if ((c & 0x80) == 0) return 1;
    if ((c & 0xE0) == 0xC0) return 2;
    if ((c & 0xF0) == 0xE0) return 3;
    if ((c & 0xF8) == 0xF0) return 4;
    return 1; // invalid lead byte; consume as-is so the buffer can't stall forever
}

// Flushes every complete codepoint currently in `pending`, leaving a trailing partial sequence
// (if any) for the next call, and emits the decoded text via the Kotlin callback.
bool flushUtf8Tokens(JNIEnv *env, jobject callback, jmethodID onTokenMethod, std::string &pending) {
    std::vector<jchar> utf16;
    size_t consumed = 0;
    while (consumed < pending.size()) {
        auto lead = static_cast<unsigned char>(pending[consumed]);
        int len = utf8SeqLen(lead);
        if (consumed + len > pending.size()) break; // incomplete sequence, wait for more bytes
        uint32_t cp;
        switch (len) {
            case 1: cp = lead; break;
            case 2: cp = (lead & 0x1Fu) << 6 | (static_cast<unsigned char>(pending[consumed + 1]) & 0x3Fu); break;
            case 3: cp = (lead & 0x0Fu) << 12 | (static_cast<unsigned char>(pending[consumed + 1]) & 0x3Fu) << 6 |
                         (static_cast<unsigned char>(pending[consumed + 2]) & 0x3Fu); break;
            default: cp = (lead & 0x07u) << 18 | (static_cast<unsigned char>(pending[consumed + 1]) & 0x3Fu) << 12 |
                          (static_cast<unsigned char>(pending[consumed + 2]) & 0x3Fu) << 6 |
                          (static_cast<unsigned char>(pending[consumed + 3]) & 0x3Fu); break;
        }
        if (cp > 0xFFFF) {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        } else {
            utf16.push_back(static_cast<jchar>(cp));
        }
        consumed += len;
    }
    pending.erase(0, consumed);
    if (utf16.empty()) return true;
    jstring jToken = env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
    env->CallVoidMethod(callback, onTokenMethod, jToken);
    env->DeleteLocalRef(jToken);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        set_last_error("Token callback threw an exception");
        return false;
    }
    return true;
}

// One loaded GGUF model + context + (optional) mtmd vision context. One instance per
// nativeLoadModel() call, freed by nativeCloseModel() — mirrors LlmEngine's single-Engine-
// instance-per-load lifecycle on the Kotlin side.
struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_adapter_lora *lora_adapter = nullptr;
#ifdef VERVAN_HAS_MTMD
    mtmd_context *mtmd_ctx = nullptr;
#endif
    std::atomic<bool> cancelled{false};
    uint32_t n_ctx = 0;
    uint32_t n_batch = 0;
    // One generation at a time per session — mirrors LlmEngine's single-active-Conversation
    // design; also lets nativeCloseModel() safely wait out an in-flight nativeGenerate() by
    // taking this same lock before freeing anything.
    std::mutex generate_mutex;
};

std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const jsize length = env->GetStringLength(s);
    const jchar *chars = env->GetStringChars(s, nullptr);
    if (chars == nullptr) return {};
    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length &&
            chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        if (cp <= 0x7F) {
            result.push_back(static_cast<char>(cp));
        } else if (cp <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(s, chars);
    return result;
}

jstring std_to_jstring(JNIEnv *env, const std::string &value) {
    std::vector<jchar> utf16;
    size_t i = 0;
    while (i < value.size()) {
        const unsigned char lead = static_cast<unsigned char>(value[i]);
        const int len = utf8SeqLen(lead);
        uint32_t cp = 0xFFFD;
        bool valid = i + len <= value.size();
        for (int j = 1; valid && j < len; ++j) {
            valid = (static_cast<unsigned char>(value[i + j]) & 0xC0) == 0x80;
        }
        if (valid) {
            if (len == 1) cp = lead;
            else if (len == 2) cp = (lead & 0x1F) << 6 | (static_cast<unsigned char>(value[i + 1]) & 0x3F);
            else if (len == 3) cp = (lead & 0x0F) << 12 | (static_cast<unsigned char>(value[i + 1]) & 0x3F) << 6 |
                                      (static_cast<unsigned char>(value[i + 2]) & 0x3F);
            else cp = (lead & 0x07) << 18 | (static_cast<unsigned char>(value[i + 1]) & 0x3F) << 12 |
                      (static_cast<unsigned char>(value[i + 2]) & 0x3F) << 6 |
                      (static_cast<unsigned char>(value[i + 3]) & 0x3F);
        }
        i += valid ? len : 1;
        if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) cp = 0xFFFD;
        if (cp > 0xFFFF) {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        } else {
            utf16.push_back(static_cast<jchar>(cp));
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

void destroy_session(LlamaSession *session) {
    if (session == nullptr) return;
#ifdef VERVAN_HAS_MTMD
    if (session->mtmd_ctx != nullptr) mtmd_free(session->mtmd_ctx);
#endif
    if (session->lora_adapter != nullptr) llama_adapter_lora_free(session->lora_adapter);
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}

// GGML_ASSERT/ggml_abort() write their failure message to stderr, which Android does not route
// to logcat by default — a native abort otherwise shows only a bare function+offset in the
// tombstone. Redirect stderr through a pipe read on a background thread so the actual assertion
// text (invaluable for anything past a "why did libllama abort" crash) reaches logcat.
void redirect_stderr_to_logcat() {
    int pipefd[2];
    if (pipe(pipefd) != 0) return;
    dup2(pipefd[1], STDERR_FILENO);
    close(pipefd[1]);
    std::thread([readfd = pipefd[0]] {
        char buf[1024];
        ssize_t n;
        while ((n = read(readfd, buf, sizeof(buf) - 1)) > 0) {
            buf[n] = 0;
            __android_log_write(ANDROID_LOG_ERROR, "llama.cpp", buf);
        }
    }).detach();
}

std::once_flag g_backend_init_flag;

void ensure_backend_initialized() {
    std::call_once(g_backend_init_flag, [] {
        redirect_stderr_to_logcat();
        llama_backend_init();
    });
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeLoadModel(
        JNIEnv *env, jobject /* thiz */,
        jstring jModelPath, jstring jMmprojPath,
        jint nCtx, jint nGpuLayers, jint nThreads, jboolean useMmap,
        jint nBatch, jint nUbatch, jboolean useMlock, jint flashAttnMode,
        jstring jKvCacheType, jint vulkanDeviceIndex, jfloat ropeFreqBase, jfloat ropeFreqScale,
        jstring jLoraPath, jfloat loraScale) {
    ensure_backend_initialized();

    const std::string modelPath = jstring_to_std(env, jModelPath);
    const std::string mmprojPath = jstring_to_std(env, jMmprojPath);
    const std::string kvCacheType = jstring_to_std(env, jKvCacheType);
    const std::string loraPath = jstring_to_std(env, jLoraPath);

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = nGpuLayers;
    modelParams.use_mmap = useMmap;
    modelParams.use_mlock = useMlock;
    modelParams.main_gpu = vulkanDeviceIndex;

    llama_model *model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) {
        set_last_error("Failed to load model file: " + modelPath);
        return 0;
    }

    const llama_vocab *vocab = llama_model_get_vocab(model);

    llama_context_params ctxParams = llama_context_default_params();
    const int effectiveCtx = nCtx > 0 ? nCtx : 4096;
    ctxParams.n_ctx = static_cast<uint32_t>(effectiveCtx);
    ctxParams.n_batch = static_cast<uint32_t>(std::min(nBatch > 0 ? nBatch : 2048, effectiveCtx));
    ctxParams.n_ubatch = static_cast<uint32_t>(nUbatch > 0 ? nUbatch : 512);
    ctxParams.n_threads = nThreads > 0 ? nThreads : 4;
    ctxParams.n_threads_batch = ctxParams.n_threads;
    // -1/0/1 map directly onto LLAMA_FLASH_ATTN_TYPE_{AUTO,DISABLED,ENABLED}'s own values —
    // AUTO (the default) lets llama.cpp fall back to non-flash attention on unsupported
    // ops/GPUs instead of hard-failing the whole context init the way ENABLED would.
    ctxParams.flash_attn_type = static_cast<llama_flash_attn_type>(flashAttnMode);
    if (kvCacheType == "q8_0") {
        ctxParams.type_k = GGML_TYPE_Q8_0;
        ctxParams.type_v = GGML_TYPE_Q8_0;
    } else if (kvCacheType == "q4_0") {
        ctxParams.type_k = GGML_TYPE_Q4_0;
        ctxParams.type_v = GGML_TYPE_Q4_0;
    } // else leave at llama_context_default_params()'s own f16 default
    ctxParams.rope_freq_base = ropeFreqBase; // 0 = from model, per llama.h
    ctxParams.rope_freq_scale = ropeFreqScale;

    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        set_last_error("Failed to create llama context for: " + modelPath);
        llama_model_free(model);
        return 0;
    }

    auto *session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    session->vocab = vocab;
    session->n_ctx = ctxParams.n_ctx;
    session->n_batch = ctxParams.n_batch;

    if (!loraPath.empty()) {
        session->lora_adapter = llama_adapter_lora_init(model, loraPath.c_str());
        if (session->lora_adapter == nullptr) {
            set_last_error("Failed to load LoRA adapter: " + loraPath);
            destroy_session(session);
            return 0;
        }
        float scale = loraScale > 0 ? loraScale : 1.0f;
        llama_set_adapters_lora(ctx, &session->lora_adapter, 1, &scale);
    }

#ifdef VERVAN_HAS_MTMD
    if (!mmprojPath.empty()) {
        mtmd_context_params mtmdParams = mtmd_context_params_default();
        // Deliberately independent of nGpuLayers (which only governs the text model): Mali's
        // ggml-vulkan CLIP/ViT path lacks the subgroup/coopmat kernels other GPUs have, so vision
        // encode on Vulkan0 here was clocked at ~175s for one image (vs. low single digits on
        // CPU) and saturated the GPU queue badly enough to starve the UI compositor (queueBuffer
        // fence timeouts, dozens of skipped frames) for the whole encode.
        mtmdParams.use_gpu = false;
        session->mtmd_ctx = mtmd_init_from_file(mmprojPath.c_str(), model, mtmdParams);
        if (session->mtmd_ctx == nullptr) {
            set_last_error("Failed to load mmproj file: " + mmprojPath);
            destroy_session(session);
            return 0;
        }
    }
#else
    if (!mmprojPath.empty()) {
        set_last_error("This llama.cpp build has no mtmd support; rebuild it with LLAMA_BUILD_MTMD=ON before using a vision projector");
        destroy_session(session);
        return 0;
    }
#endif

    LOGI("nativeLoadModel() success: %s (n_ctx=%d, n_gpu_layers=%d, mtmd=%s)",
         modelPath.c_str(), effectiveCtx, nGpuLayers, mmprojPath.empty() ? "no" : "yes");
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeGenerate(
        JNIEnv *env, jobject /* thiz */,
        jlong handle, jstring jPrompt, jstring jImagePath,
        jfloat temperature, jfloat topP, jint topK, jfloat minP,
        jfloat repeatPenalty, jint repeatLastN,
        jint seed, jint maxTokens, jstring jChatTemplateOverride,
        jstring jAssistantPrefill, jstring jSystemPrompt,
        jobject callback) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr) {
        const std::string error = "nativeGenerate() called with a null/closed handle";
        set_last_error(error);
        return std_to_jstring(env, error);
    }
    std::lock_guard<std::mutex> lock(session->generate_mutex);
    session->cancelled = false;
    g_last_error.clear();

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (callbackClass == nullptr || onTokenMethod == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        const std::string error = "Could not resolve the llama.cpp token callback";
        set_last_error(error);
        if (callbackClass != nullptr) env->DeleteLocalRef(callbackClass);
        return std_to_jstring(env, error);
    }

    std::string prompt = jstring_to_std(env, jPrompt);
    const std::string imagePath = jstring_to_std(env, jImagePath);
    const std::string chatTemplateOverride = jstring_to_std(env, jChatTemplateOverride);
    const std::string assistantPrefill = jstring_to_std(env, jAssistantPrefill);
    const std::string systemPrompt = jstring_to_std(env, jSystemPrompt);

    // Wrap the app's pre-assembled system/user text as real chat-template turns and apply a
    // template — either the user's own override (a llama_chat_builtin_templates() name, or raw
    // custom Jinja text) or, failing that, the GGUF's own embedded template (e.g. ChatML for
    // Qwen). Without this, the model never sees the turn-boundary special tokens it was trained
    // on and generation quality degrades badly. A real "system" turn matters beyond formatting:
    // most instruction-tuned models give system content different trust/priority than user
    // content, so folding persona/tool/memory instructions into the "user" turn (the old
    // single-message behavior here) is a real template violation, not just cosmetic — plausibly
    // why some models misbehave in this app but not in front-ends that send proper roles. Falls
    // back to the raw prompt untouched if neither an override nor a built-in template is available.
    const char *tmpl = !chatTemplateOverride.empty()
        ? chatTemplateOverride.c_str()
        : llama_model_chat_template(session->model, nullptr);
    if (tmpl != nullptr) {
        std::vector<llama_chat_message> messages;
        if (!systemPrompt.empty()) messages.push_back({"system", systemPrompt.c_str()});
        messages.push_back({"user", prompt.c_str()});
        std::vector<char> buf((prompt.size() + systemPrompt.size()) * 2 + 256);
        int32_t needed = llama_chat_apply_template(tmpl, messages.data(), messages.size(), /* add_ass */ true, buf.data(), static_cast<int32_t>(buf.size()));
        if (needed > static_cast<int32_t>(buf.size())) {
            buf.resize(needed);
            needed = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
        }
        if (needed > 0) prompt.assign(buf.data(), needed);
    }
    // Assistant-message prefill: the app's raw `llama_chat_apply_template()` path (not Jinja) has
    // no `enable_thinking` template variable, so a plain "/no_think" instruction embedded in the
    // user turn is only ever a request the model can ignore. Appending directly onto the
    // templated prompt, right after the "assistant\n" role header add_ass just added, forces the
    // literal token sequence into context before generation starts — e.g. an already-closed
    // "<think>\n\n</think>\n\n" makes the model continue as if it had already finished (not-)
    // thinking, the same reliable trick llama.cpp's own server/other GGUF front-ends use to force
    // reasoning on/off. Kotlin decides the exact text (or sends null/empty for no forcing).
    if (!assistantPrefill.empty()) prompt += assistantPrefill;

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(samplerParams);
    auto fail = [&](const std::string &message) -> jstring {
        set_last_error(message);
        if (sampler != nullptr) llama_sampler_free(sampler);
        env->DeleteLocalRef(callbackClass);
        return std_to_jstring(env, message);
    };
    if (sampler == nullptr) return fail("Could not create llama.cpp sampler");
    // Penalties before top-k/top-p per llama.cpp's own recommended ordering (see llama.h).
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
        repeatLastN != 0 ? repeatLastN : 64, repeatPenalty > 0 ? repeatPenalty : 1.1f,
        /* penalty_freq */ 0.0f, /* penalty_present */ 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK > 0 ? topK : 40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP > 0 ? topP : 0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(minP >= 0 ? minP : 0.05f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature >= 0 ? temperature : 0.8f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));

    // Clear the KV cache before every turn — this app rebuilds the whole prompt (persona +
    // history + retrieved sources) fresh each call rather than incrementally extending a
    // conversation, same reasoning LlmEngine.generate() documents for why it recreates its
    // Conversation every turn instead of reusing one long-lived session.
    // Must target seq_id 0 explicitly, not -1 ("all sequences"): for recurrent/hybrid memory
    // (Mamba/SSM-hybrid architectures, e.g. Qwen3.5's Gated Delta Net layers),
    // llama_memory_recurrent::seq_rm only resets its internal per-sequence `tail` pointer on the
    // seq_id>=0 path (see llama-memory-recurrent.cpp) — the seq_id<0 path clears cells' seq_id
    // sets but leaves `tail` stale, so the next decode's find_slot dereferences a cell that no
    // longer has the seq_id it expects and hits GGML_ASSERT(cell.has_seq_id(seq_id)). This app
    // only ever uses a single sequence (id 0), so clearing it specifically is equivalent for
    // plain KV-cache models and fixes the crash for hybrid/recurrent ones.
    llama_memory_seq_rm(llama_get_memory(session->ctx), 0, -1, -1);
    int32_t n_past = 0;
    bool ok = true;

#ifdef VERVAN_HAS_MTMD
    if (!imagePath.empty()) {
        if (session->mtmd_ctx == nullptr) return fail("An image was supplied, but this model has no loaded vision projector");
        mtmd_helper_bitmap_wrapper wrapper =
                mtmd_helper_bitmap_init_from_file(session->mtmd_ctx, imagePath.c_str(), /* placeholder */ false);
        if (wrapper.bitmap == nullptr) {
            set_last_error("Failed to load image: " + imagePath);
            ok = false;
        } else {
            // mtmd_tokenize requires exactly one media marker per bitmap in the prompt text — the
            // Kotlin side sends the raw user prompt with no marker, so splice it in here rather
            // than pushing this API detail up to callers.
            const std::string markedPrompt = std::string(mtmd_default_marker()) + "\n" + prompt;
            mtmd_input_chunks *chunks = mtmd_input_chunks_init();
            // mtmd_input_text gained a text_len field between text and add_special — a 3-arg
            // positional brace-init silently shifts add_special into text_len (truncating text
            // to 1 byte) and zero-inits parse_special to false. Field names required here.
            mtmd_input_text inputText{
                    /* text */ markedPrompt.c_str(),
                    /* text_len */ markedPrompt.size(),
                    /* add_special */ true,
                    /* parse_special */ true};
            const mtmd_bitmap *bitmaps[] = {wrapper.bitmap};
            if (mtmd_tokenize(session->mtmd_ctx, chunks, &inputText, bitmaps, 1) != 0) {
                set_last_error("mtmd_tokenize failed for: " + imagePath);
                ok = false;
            } else {
                llama_pos newNPast = 0;
                if (mtmd_helper_eval_chunks(session->mtmd_ctx, session->ctx, chunks, 0, 0,
                                             session->n_batch, /* logits_last */ true, &newNPast) != 0) {
                    set_last_error("mtmd_helper_eval_chunks failed");
                    ok = false;
                } else {
                    n_past = newNPast;
                }
            }
            mtmd_input_chunks_free(chunks);
            mtmd_bitmap_free(wrapper.bitmap);
            if (wrapper.video_ctx != nullptr) mtmd_helper_video_free(wrapper.video_ctx);
        }
    }
#else
    if (!imagePath.empty()) return fail("This app build does not include llama.cpp vision support");
#endif
    if (imagePath.empty()) {
        std::vector<llama_token> tokens(prompt.size() + 8);
        int n_tokens = llama_tokenize(session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                       tokens.data(), static_cast<int32_t>(tokens.size()),
                                       /* add_special */ true, /* parse_special */ true);
        if (n_tokens < 0) {
            tokens.resize(-n_tokens);
            n_tokens = llama_tokenize(session->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                       tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        }
        if (n_tokens <= 0) return fail("Could not tokenize the prompt");
        tokens.resize(static_cast<size_t>(n_tokens));
        if (static_cast<uint32_t>(n_tokens) >= session->n_ctx) {
            return fail("Prompt needs " + std::to_string(n_tokens) + " tokens but the model context is only " +
                        std::to_string(session->n_ctx) + ". Shorten the chat or increase context size.");
        }

        int offset = 0;
        while (offset < n_tokens) {
            const int chunk = std::min<int>(static_cast<int>(session->n_batch), n_tokens - offset);
            llama_batch batch = llama_batch_init(chunk, 0, 1);
            for (int i = 0; i < chunk; i++) {
                batch.token[i] = tokens[offset + i];
                batch.pos[i] = offset + i;
                batch.n_seq_id[i] = 1;
                batch.seq_id[i][0] = 0;
                batch.logits[i] = (offset + i == n_tokens - 1);
            }
            batch.n_tokens = chunk;
            const int decodeResult = llama_decode(session->ctx, batch);
            llama_batch_free(batch);
            if (decodeResult != 0) return fail("llama_decode failed while evaluating the prompt");
            offset += chunk;
        }
        n_past = n_tokens;
    }

    if (!ok) return fail(g_last_error.empty() ? "Failed to evaluate multimodal prompt" : g_last_error);
    const int availableTokens = static_cast<int>(session->n_ctx) - n_past;
    if (availableTokens <= 0) return fail("The prompt filled the entire model context; no room remains for a response");
    const int generationLimit = std::min(maxTokens > 0 ? static_cast<int>(maxTokens) : 1024, availableTokens);

    std::string pendingUtf8;
    int generated = 0;
    while (!session->cancelled.load() && generated < generationLimit) {
        llama_token newToken = llama_sampler_sample(sampler, session->ctx, -1);
        llama_sampler_accept(sampler, newToken);

        if (llama_vocab_is_eog(session->vocab, newToken)) break;

        std::vector<char> piece(256);
        int n = llama_token_to_piece(session->vocab, newToken, piece.data(), piece.size(), 0, true);
        if (n < 0) {
            piece.resize(static_cast<size_t>(-n));
            n = llama_token_to_piece(session->vocab, newToken, piece.data(), piece.size(), 0, true);
        }
        if (n < 0) return fail("Could not decode a generated token");

        llama_batch batch = llama_batch_init(1, 0, 1);
        batch.token[0] = newToken;
        batch.pos[0] = n_past;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        batch.n_tokens = 1;
        const int decodeResult = llama_decode(session->ctx, batch);
        llama_batch_free(batch);
        if (decodeResult != 0) return fail("llama_decode failed mid-generation");

        if (n > 0) {
            pendingUtf8.append(piece.data(), static_cast<size_t>(n));
            if (!flushUtf8Tokens(env, callback, onTokenMethod, pendingUtf8)) {
                return fail(g_last_error.empty() ? "Token callback failed" : g_last_error);
            }
        }
        n_past++;
        generated++;
    }

    llama_sampler_free(sampler);
    env->DeleteLocalRef(callbackClass);
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeCancelGeneration(JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session != nullptr) session->cancelled = true;
}

JNIEXPORT void JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeCloseModel(JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr) return;
    session->cancelled = true;
    std::unique_lock<std::mutex> lock(session->generate_mutex); // wait out any in-flight generate first
#ifdef VERVAN_HAS_MTMD
    if (session->mtmd_ctx != nullptr) mtmd_free(session->mtmd_ctx);
#endif
    if (session->lora_adapter != nullptr) llama_adapter_lora_free(session->lora_adapter);
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    lock.unlock(); // never destroy a mutex while a lock object still owns it
    delete session;
}

JNIEXPORT jstring JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeGetLastError(JNIEnv *env, jobject /* thiz */) {
    if (g_last_error.empty()) return nullptr;
    return std_to_jstring(env, g_last_error);
}

JNIEXPORT jstring JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeGetModelInfo(JNIEnv *env, jobject /* thiz */, jlong handle) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr || session->model == nullptr) return nullptr;
    char descBuf[256] = {0};
    llama_model_desc(session->model, descBuf, sizeof(descBuf));
    const int32_t nCtxTrain = llama_model_n_ctx_train(session->model);
    const int32_t nLayer = llama_model_n_layer(session->model);
    const std::string result = std::string(descBuf) + "|" + std::to_string(nCtxTrain) + "|" + std::to_string(nLayer);
    return std_to_jstring(env, result);
}

JNIEXPORT jobjectArray JNICALL
Java_com_vervan_chat_llm_LlamaCppJni_nativeListChatTemplates(JNIEnv *env, jobject /* thiz */) {
    const int32_t count = llama_chat_builtin_templates(nullptr, 0);
    std::vector<const char *> names(count > 0 ? count : 0);
    if (count > 0) llama_chat_builtin_templates(names.data(), names.size());
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
    for (size_t i = 0; i < names.size(); i++) {
        jstring name = env->NewStringUTF(names[i]); // built-in template names are pure ASCII
        env->SetObjectArrayElement(result, static_cast<jsize>(i), name);
        env->DeleteLocalRef(name);
    }
    return result;
}

} // extern "C"
