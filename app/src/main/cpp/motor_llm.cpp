// Ponte JNI entre MotorLlm.kt e o llama.cpp. Uma geração por vez (o Kotlin serializa as chamadas numa thread só).
#include <android/log.h>
#include <asm/hwcap.h>
#include <jni.h>
#include <sys/auxv.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr const char *TAG = "MotorLlm";

struct Motor {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    // Gera até este id está cancelado: um cancelamento atrasado nunca derruba a geração seguinte.
    std::atomic<int64_t> cancelar_ate{0};
    int64_t geracao = 0;
};

bool cancelado(const Motor *m) { return m->cancelar_ate.load() >= m->geracao; }

void log_llama(ggml_log_level nivel, const char *texto, void *) {
    if (nivel == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", texto);
    else if (nivel == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", texto);
}

/** Tamanho do maior prefixo de `s` que não termina no meio de um caractere UTF-8. */
size_t prefixo_utf8_completo(const std::string &s) {
    const size_t n = s.size();
    for (size_t volta = 1; volta <= 4 && volta <= n; volta++) {
        const auto b = static_cast<unsigned char>(s[n - volta]);
        if ((b & 0xC0) == 0x80) continue;  // byte de continuação
        const size_t precisa = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : b >= 0xC0 ? 2 : 1;
        return volta < precisa ? n - volta : n;
    }
    return n;
}

jbyteArray bytes(JNIEnv *env, const std::string &s) {
    jbyteArray a = env->NewByteArray(static_cast<jsize>(s.size()));
    if (a != nullptr) env->SetByteArrayRegion(a, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return a;
}

// UTF-8 de verdade (GetStringUTFChars daria UTF-8 modificado, que estraga emoji no prompt e na gramática)
std::string texto_de(JNIEnv *env, jbyteArray a) {
    std::string s(static_cast<size_t>(env->GetArrayLength(a)), '\0');
    env->GetByteArrayRegion(a, 0, static_cast<jsize>(s.size()), reinterpret_cast<jbyte *>(s.data()));
    return s;
}

int64_t micros_desde(std::chrono::steady_clock::time_point t) {
    return std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - t).count();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_financacelular_ia_llm_MotorLlm_nativeCarregar(JNIEnv *env, jclass, jstring caminho, jint threads, jint contexto) {
    // ggml foi compilado para armv8.2+dotprod+fp16: em CPU sem isso a primeira conta daria SIGILL
    const unsigned long hw = getauxval(AT_HWCAP);
    if ((hw & HWCAP_ASIMDDP) == 0 || (hw & HWCAP_ASIMDHP) == 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "CPU sem dotprod/fp16: LLM desligado");
        return 0;
    }
    static const bool iniciado = [] {
        llama_log_set(log_llama, nullptr);
        llama_backend_init();
        return true;
    }();
    (void) iniciado;

    const char *c = env->GetStringUTFChars(caminho, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(c, mp);
    env->ReleaseStringUTFChars(caminho, c);
    if (model == nullptr) return 0;

    auto *m = new Motor();
    m->model = model;
    m->vocab = llama_model_get_vocab(model);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(contexto);
    cp.n_batch = static_cast<uint32_t>(contexto);
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    cp.no_perf = true;
    // cancela também no meio do processamento do prompt
    cp.abort_callback = [](void *dados) { return cancelado(static_cast<Motor *>(dados)); };
    cp.abort_callback_data = m;
    m->ctx = llama_init_from_model(model, cp);
    if (m->ctx == nullptr) {
        llama_model_free(model);
        delete m;
        return 0;
    }
    return reinterpret_cast<jlong>(m);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_financacelular_ia_llm_MotorLlm_nativeCancelar(JNIEnv *, jclass, jlong handle, jlong geracao) {
    auto *m = reinterpret_cast<Motor *>(handle);
    int64_t atual = m->cancelar_ate.load();
    while (atual < geracao && !m->cancelar_ate.compare_exchange_weak(atual, geracao)) {}
}

/**
 * Gera com decodificação gulosa (e gramática GBNF, se houver). Devolve os bytes UTF-8 do texto, ou null em erro.
 * `receptor.pedaco(byte[])` recebe só UTF-8 completo. `estatisticas` = [tokens do prompt, µs do prompt, tokens gerados, µs da geração].
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_financacelular_ia_llm_MotorLlm_nativeGerar(
        JNIEnv *env, jclass, jlong handle, jlong geracao, jbyteArray promptUtf8, jbyteArray gramaticaUtf8, jint maxTokens,
        jobject receptor, jlongArray estatisticas) {
    auto *m = reinterpret_cast<Motor *>(handle);
    m->geracao = geracao;
    llama_memory_clear(llama_get_memory(m->ctx), true);

    const std::string prompt = texto_de(env, promptUtf8);

    const int32_t nCtx = static_cast<int32_t>(llama_n_ctx(m->ctx));
    const int32_t n = -llama_tokenize(m->vocab, prompt.data(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (n <= 0 || n >= nCtx) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "prompt com %d tokens não cabe no contexto de %d", n, nCtx);
        return nullptr;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(n));
    llama_tokenize(m->vocab, prompt.data(), static_cast<int32_t>(prompt.size()), tokens.data(), n, true, true);

    llama_sampler *amostrador = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (gramaticaUtf8 != nullptr) {
        const std::string g = texto_de(env, gramaticaUtf8);
        llama_sampler *sg = llama_sampler_init_grammar(m->vocab, g.c_str(), "root");
        if (sg == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "gramática inválida");
            llama_sampler_free(amostrador);
            return nullptr;
        }
        llama_sampler_chain_add(amostrador, sg);
    }
    llama_sampler_chain_add(amostrador, llama_sampler_init_greedy());

    jmethodID pedaco = env->GetMethodID(env->GetObjectClass(receptor), "pedaco", "([B)V");
    llama_batch_ext *lote = llama_batch_ext_init(m->ctx);
    std::string texto;      // tudo o que foi gerado
    size_t entregue = 0;    // bytes de `texto` já passados ao receptor
    bool erro = false;
    jlong est[4] = {n, 0, 0, 0};

    // prompt, em lotes de n_batch
    auto t = std::chrono::steady_clock::now();
    const int32_t nBatch = static_cast<int32_t>(llama_n_batch(m->ctx));
    for (int32_t i = 0; i < n && !erro; i += nBatch) {
        const int32_t fim = std::min(n, i + nBatch);
        llama_batch_ext_clear(lote);
        for (int32_t j = i; j < fim; j++) {
            const int32_t idx = llama_batch_ext_add_token(lote, 0, tokens[j]);
            const llama_pos pos = j;
            llama_batch_ext_set_pos(lote, idx, &pos);
        }
        llama_batch_ext_set_output_logits(lote, fim - i - 1, fim == n);
        erro = llama_process(m->ctx, LLAMA_PROCESS_TYPE_DECODE, lote) != 0;
    }
    est[1] = micros_desde(t);

    t = std::chrono::steady_clock::now();
    llama_pos pos = n;
    while (!erro && !cancelado(m) && pos < nCtx) {
        const llama_token tok = llama_sampler_sample(amostrador, m->ctx, -1);
        if (llama_vocab_is_eog(m->vocab, tok)) break;
        est[2]++;

        char buf[256];
        const int32_t k = llama_token_to_piece(m->vocab, tok, buf, sizeof(buf), 0, false);
        if (k > 0) texto.append(buf, static_cast<size_t>(k));
        const size_t completo = prefixo_utf8_completo(texto);
        if (completo > entregue) {
            jbyteArray a = bytes(env, texto.substr(entregue, completo - entregue));
            env->CallVoidMethod(receptor, pedaco, a);
            env->DeleteLocalRef(a);
            entregue = completo;
            if (env->ExceptionCheck()) break;  // a exceção do aoPedaco sobe para o Kotlin
        }
        if (est[2] >= maxTokens) break;

        llama_batch_ext_clear(lote);
        const int32_t idx = llama_batch_ext_add_token(lote, 0, tok);
        llama_batch_ext_set_pos(lote, idx, &pos);
        llama_batch_ext_set_output_logits(lote, idx, true);
        erro = llama_process(m->ctx, LLAMA_PROCESS_TYPE_DECODE, lote) != 0;
        pos++;
    }
    est[3] = micros_desde(t);

    llama_batch_ext_free(lote);
    llama_sampler_free(amostrador);
    env->SetLongArrayRegion(estatisticas, 0, 4, est);
    if (erro && !cancelado(m)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "llama_process falhou");
        return nullptr;
    }
    // final cortado no meio de um caractere fica de fora, como no receptor
    return bytes(env, texto.substr(0, entregue));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_financacelular_ia_llm_MotorLlm_nativeLiberar(JNIEnv *, jclass, jlong handle) {
    auto *m = reinterpret_cast<Motor *>(handle);
    llama_free(m->ctx);
    llama_model_free(m->model);
    delete m;
}
