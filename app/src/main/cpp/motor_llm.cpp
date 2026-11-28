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

// Estado da seq 0 logo depois de um prefixo fixo do prompt. É um snapshot completo (llama_state_seq_get_data), não um
// pedaço do KV: serve também para memória recorrente/híbrida, que não aceita cortar a sequência no meio.
struct Prefixo {
    std::string texto;
    std::vector<uint8_t> estado;
    int32_t tokens = 0;
    uint64_t uso = 0;  // para trocar o usado há mais tempo
};

struct Motor {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    // Cancela só a geração com este id exato: cancelar uma geração na fila ou um cancelamento atrasado nunca
    // derruba outra. `cancelar_tudo` é o do liberar.
    std::atomic<int64_t> cancelar_id{0};
    std::atomic<bool> cancelar_tudo{false};
    std::atomic<int64_t> geracao{0};
    Prefixo prefixos[2];  // plano e resposta, um não expulsa o outro
    uint64_t usos = 0;
};

bool cancelado(const Motor *m) { return m->cancelar_tudo.load() || m->cancelar_id.load() == m->geracao.load(); }

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

std::vector<llama_token> tokenizar(const llama_vocab *vocab, const std::string &s, bool especiais) {
    const int32_t n = -llama_tokenize(vocab, s.data(), static_cast<int32_t>(s.size()), nullptr, 0, especiais, true);
    std::vector<llama_token> t(static_cast<size_t>(std::max(n, 0)));
    if (n > 0) llama_tokenize(vocab, s.data(), static_cast<int32_t>(s.size()), t.data(), n, especiais, true);
    return t;
}

/** Processa `tokens` na seq 0 a partir da posição `inicio`, em lotes de n_batch. true se deu erro (ou foi cancelado). */
bool processar(llama_context *ctx, llama_batch_ext *lote, const std::vector<llama_token> &tokens, llama_pos inicio, bool logitsNoFim) {
    const auto n = static_cast<int32_t>(tokens.size());
    const auto nBatch = static_cast<int32_t>(llama_n_batch(ctx));
    for (int32_t i = 0; i < n; i += nBatch) {
        const int32_t fim = std::min(n, i + nBatch);
        llama_batch_ext_clear(lote);
        for (int32_t j = i; j < fim; j++) {
            const int32_t idx = llama_batch_ext_add_token(lote, 0, tokens[j]);
            const llama_pos pos = inicio + j;
            llama_batch_ext_set_pos(lote, idx, &pos);
        }
        llama_batch_ext_set_output_logits(lote, fim - i - 1, logitsNoFim && fim == n);
        if (llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, lote) != 0) return true;
    }
    return false;
}

int64_t micros_desde(std::chrono::steady_clock::time_point t) {
    return std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - t).count();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_financacelular_ia_llm_MotorLlm_nativeCarregar(JNIEnv *env, jclass, jstring caminho, jint threads, jint contexto, jboolean kvQuantizado) {
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
    if (c == nullptr) return 0;  // OutOfMemoryError pendente sobe para o Kotlin
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
    if (kvQuantizado) {  // metade da RAM do KV; V quantizado exige flash attention
        cp.type_k = GGML_TYPE_Q8_0;
        cp.type_v = GGML_TYPE_Q8_0;
        cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    }
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
    if (geracao == INT64_MAX) m->cancelar_tudo.store(true);
    else m->cancelar_id.store(geracao);
}

/**
 * Gera com decodificação gulosa (e gramática GBNF, se houver). Devolve os bytes UTF-8 do texto, ou null em erro.
 * `receptor.pedaco(byte[])` recebe só UTF-8 completo. `estatisticas` = [tokens do prompt, µs do prompt, tokens gerados,
 * µs da geração, 1 se parou por cancelamento, tokens do prefixo que vieram do cache]; "tokens do prompt" não conta os do cache.
 * Os primeiros `tamanhoPrefixo` bytes do prompt são um prefixo fixo (0 = nenhum): o estado logo depois dele fica guardado
 * e a próxima geração com o mesmo prefixo só processa o resto. Se `pedaco` lançar exceção, solta tudo e volta na hora
 * (a exceção sobe). Erro se prompt + maxTokens não couber no contexto.
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_financacelular_ia_llm_MotorLlm_nativeGerar(
        JNIEnv *env, jclass, jlong handle, jlong geracao, jbyteArray promptUtf8, jint tamanhoPrefixo, jbyteArray gramaticaUtf8,
        jint maxTokens, jobject receptor, jlongArray estatisticas) {
    auto *m = reinterpret_cast<Motor *>(handle);
    m->geracao = geracao;
    llama_memory_t memoria = llama_get_memory(m->ctx);
    llama_memory_clear(memoria, true);

    const std::string prompt = texto_de(env, promptUtf8);
    const size_t corte = std::min(static_cast<size_t>(std::max(tamanhoPrefixo, 0)), prompt.size());
    const std::string prefixo = prompt.substr(0, corte);

    // prefixo já guardado: restaura o estado e processa só o resto
    Prefixo *guardado = nullptr;
    int32_t doCache = 0;
    if (!prefixo.empty()) {
        for (auto &p : m->prefixos) {
            if (p.texto == prefixo) guardado = &p;
        }
        if (guardado != nullptr && llama_state_seq_set_data(m->ctx, guardado->estado.data(), guardado->estado.size(), 0) != 0) {
            doCache = guardado->tokens;
            guardado->uso = ++m->usos;
        } else if (guardado != nullptr) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "estado do prefixo não restaurou: processa de novo");
            llama_memory_clear(memoria, true);
            guardado->texto.clear();
            guardado->uso = 0;  // é o próximo a ser trocado
        }
    }
    // Prefixo e resto tokenizados separados (o resto sem tokens especiais de início), também quando o prefixo é novo:
    // assim a geração que guarda o prefixo e a que o reaproveita veem os mesmos tokens.
    std::vector<llama_token> tokensPrefixo;
    if (!prefixo.empty() && doCache == 0) tokensPrefixo = tokenizar(m->vocab, prefixo, true);
    const std::vector<llama_token> tokens = tokenizar(m->vocab, prompt.substr(corte), prefixo.empty());

    const int32_t nCtx = static_cast<int32_t>(llama_n_ctx(m->ctx));
    const auto nPrefixo = static_cast<int32_t>(tokensPrefixo.size()) + doCache;
    const auto n = static_cast<int32_t>(tokens.size());
    if (n <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "prompt vazio");
        return nullptr;
    }
    if (maxTokens <= 0 || static_cast<int64_t>(nPrefixo) + n + maxTokens > nCtx) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "prompt de %d tokens + %d gerados não cabe no contexto de %d", nPrefixo + n, maxTokens, nCtx);
        return nullptr;
    }

    jmethodID pedaco = env->GetMethodID(env->GetObjectClass(receptor), "pedaco", "([B)V");
    if (pedaco == nullptr) return nullptr;  // NoSuchMethodError pendente sobe para o Kotlin

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

    llama_batch_ext *lote = llama_batch_ext_init(m->ctx);
    std::string texto;      // tudo o que foi gerado
    size_t entregue = 0;    // bytes de `texto` já passados ao receptor
    bool erro = false;
    jlong est[6] = {static_cast<jlong>(tokensPrefixo.size()) + n, 0, 0, 0, 0, doCache};

    auto t = std::chrono::steady_clock::now();
    if (!tokensPrefixo.empty()) {
        erro = processar(m->ctx, lote, tokensPrefixo, 0, false);
        if (!erro && !cancelado(m)) {
            // guarda no lugar vazio ou no usado há mais tempo
            Prefixo *p = &m->prefixos[0];
            for (auto &q : m->prefixos) {
                if (q.uso < p->uso) p = &q;
            }
            p->estado.resize(llama_state_seq_get_size(m->ctx, 0));
            p->estado.resize(llama_state_seq_get_data(m->ctx, p->estado.data(), p->estado.size(), 0));
            p->texto = p->estado.empty() ? std::string() : prefixo;
            p->tokens = nPrefixo;
            p->uso = ++m->usos;
            __android_log_print(ANDROID_LOG_INFO, TAG, "prefixo de %d tokens guardado (%zu bytes)", nPrefixo, p->estado.size());
        }
    }
    if (!erro) erro = processar(m->ctx, lote, tokens, nPrefixo, true);
    est[1] = micros_desde(t);

    t = std::chrono::steady_clock::now();
    llama_pos pos = nPrefixo + n;
    while (!erro && !cancelado(m) && est[2] < maxTokens) {
        const llama_token tok = llama_sampler_sample(amostrador, m->ctx, -1);
        if (llama_vocab_is_eog(m->vocab, tok)) break;
        est[2]++;

        std::string peca(64, '\0');
        int32_t k = llama_token_to_piece(m->vocab, tok, peca.data(), static_cast<int32_t>(peca.size()), 0, false);
        if (k < 0) {  // peça maior que o buffer: -k é o tamanho certo
            peca.resize(static_cast<size_t>(-k));
            k = llama_token_to_piece(m->vocab, tok, peca.data(), static_cast<int32_t>(peca.size()), 0, false);
        }
        if (k > 0) texto.append(peca.data(), static_cast<size_t>(k));
        const size_t completo = prefixo_utf8_completo(texto);
        if (completo > entregue) {
            jbyteArray a = bytes(env, texto.substr(entregue, completo - entregue));
            if (a == nullptr) break;  // OutOfMemoryError pendente: tratado logo abaixo
            env->CallVoidMethod(receptor, pedaco, a);
            env->DeleteLocalRef(a);
            entregue = completo;
        }
        if (env->ExceptionCheck()) break;  // exceção do aoPedaco: nenhuma outra chamada JNI antes de voltar
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
    if (env->ExceptionCheck()) return nullptr;  // a exceção pendente sobe para o Kotlin

    est[4] = cancelado(m) ? 1 : 0;
    env->SetLongArrayRegion(estatisticas, 0, 6, est);
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
