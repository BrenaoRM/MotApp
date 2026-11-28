package com.example.financacelular.ia.llm

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface MotorDeTexto {
    /**
     * Bloqueante; chamar fora da Main. aoPedaco recebe só UTF-8 completo. Para quando a coroutine for cancelada.
     * `prefixo`: início fixo do prompt cujo processamento fica guardado entre gerações ("" = sem cache).
     */
    suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, prefixo: String = "", aoPedaco: (String) -> Unit): String
    fun liberar()
}

/** Tokens e tempos da última geração (prompt processado, sem o prefixo que veio do cache, e tokens gerados). */
data class EstatisticasGeracao(
    val tokensPrompt: Long,
    val microsPrompt: Long,
    val tokensGerados: Long,
    val microsGeracao: Long,
    val tokensDoCache: Long,
)

/**
 * llama.cpp via JNI (libmotor_llm.so), decodificação gulosa, KV cache em q8_0. Guarda o estado depois dos dois últimos
 * prefixos usados (plano e resposta), então só o resto do prompt é processado. As gerações rodam uma de cada vez numa
 * thread própria; [liberar] entra na mesma fila, então nunca solta o modelo no meio de uma geração.
 *
 * `aoPedaco` roda nessa thread interna, não no dispatcher de quem chamou: para mexer na UI, trocar para a Main.
 * Sem [liberar], a thread e o modelo (1–3 GB) ficam presos até o processo morrer; quem carrega é dono do ciclo de vida.
 */
class MotorLlm private constructor(handle: Long) : MotorDeTexto {
    private val trava = Any()
    private var handle = handle // 0 depois de liberar; lido e zerado só com `trava`
    private val geracoes = AtomicLong(0)
    private val emAndamento = AtomicLong(0) // id da geração que está no llama.cpp agora (0 = nenhuma)
    private val fila = Executors.newSingleThreadExecutor { r -> Thread(null, r, "MotorLlm", 8L shl 20) }

    @Volatile
    private var liberado = false

    @Volatile
    var ultimaGeracao: EstatisticasGeracao? = null
        private set

    override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, prefixo: String, aoPedaco: (String) -> Unit): String {
        // resto nunca vazio: a geração precisa dos logits do último token processado
        require(prefixo.isEmpty() || (prompt.length > prefixo.length && prompt.startsWith(prefixo))) { "prefixo não é início do prompt" }
        val tamanhoPrefixo = prefixo.toByteArray().size
        return suspendCancellableCoroutine { cont ->
            val tarefa = fun(id: Long) = Runnable {
                val h = synchronized(trava) { handle }
                if (h == 0L) {
                    cont.resumeWithException(IllegalStateException("MotorLlm já liberado"))
                    return@Runnable
                }
                // marca antes de olhar isActive: ou o cancelamento vê este id, ou esta tarefa vê o cancelamento
                emAndamento.set(id)
                try {
                    if (!cont.isActive) return@Runnable
                    val est = LongArray(6)
                    val resultado = runCatching {
                        nativeGerar(h, id, prompt.toByteArray(), tamanhoPrefixo, gramatica?.toByteArray(), maxTokens, Receptor(aoPedaco), est)
                    }
                    ultimaGeracao = EstatisticasGeracao(est[0], est[1], est[2], est[3], est[5])
                    resultado.fold(
                        { bytes ->
                            when {
                                liberado -> cont.resumeWithException(IllegalStateException("MotorLlm liberado durante a geração"))
                                // parou por cancelamento: nunca devolve o texto cortado como se tivesse terminado
                                est[4] != 0L -> cont.resumeWithException(CancellationException("geração cancelada"))
                                bytes == null -> cont.resumeWithException(IllegalStateException("falha na geração do llama.cpp"))
                                else -> cont.resume(String(bytes, Charsets.UTF_8))
                            }
                        },
                        { cont.resumeWithException(it) },
                    )
                } finally {
                    emAndamento.set(0)
                }
            }
            // id e lugar na fila juntos: a ordem dos ids é a ordem de execução
            val id = synchronized(trava) {
                val id = geracoes.incrementAndGet()
                try {
                    fila.execute(tarefa(id))
                } catch (_: RejectedExecutionException) {
                    cont.resumeWithException(IllegalStateException("MotorLlm já liberado"))
                    return@suspendCancellableCoroutine
                }
                id
            }
            cont.invokeOnCancellation { cancelar(id) }
        }
    }

    /** Cancela só a geração `id`, e só se ela já estiver no llama.cpp (na fila, a própria tarefa vê o cancelamento e não roda). */
    private fun cancelar(id: Long) = synchronized(trava) {
        if (handle != 0L && emAndamento.get() == id) nativeCancelar(handle, id)
    }

    /** Cancela o que estiver gerando e solta o modelo assim que a geração atual parar. Pode chamar mais de uma vez. */
    override fun liberar() {
        synchronized(trava) {
            if (liberado) return
            liberado = true
            if (handle != 0L) nativeCancelar(handle, Long.MAX_VALUE)
            fila.execute {
                val h = synchronized(trava) { handle.also { handle = 0L } }
                if (h != 0L) nativeLiberar(h)
            }
            fila.shutdown()
        }
    }

    /** Chamado pelo JNI a cada pedaço de UTF-8 completo. */
    private class Receptor(private val aoPedaco: (String) -> Unit) {
        @Suppress("unused")
        fun pedaco(bytes: ByteArray) = aoPedaco(String(bytes, Charsets.UTF_8))
    }

    companion object {
        /**
         * Abre o GGUF (leva segundos: chamar fora da Main). null se o arquivo não abrir ou a CPU não tiver dotprod/fp16.
         * `kvQuantizado` = false deixa o KV cache em f16 (só para comparar).
         */
        fun carregar(caminho: String, threads: Int = 4, contexto: Int = 2048, kvQuantizado: Boolean = true): MotorLlm? {
            System.loadLibrary("motor_llm")
            val h = nativeCarregar(caminho, threads, contexto, kvQuantizado)
            return if (h == 0L) null else MotorLlm(h)
        }

        @JvmStatic private external fun nativeCarregar(caminho: String, threads: Int, contexto: Int, kvQuantizado: Boolean): Long

        @JvmStatic private external fun nativeCancelar(handle: Long, geracao: Long)

        @JvmStatic private external fun nativeGerar(
            handle: Long,
            geracao: Long,
            prompt: ByteArray,
            tamanhoPrefixo: Int,
            gramatica: ByteArray?,
            maxTokens: Int,
            receptor: Any,
            estatisticas: LongArray,
        ): ByteArray?

        @JvmStatic private external fun nativeLiberar(handle: Long)
    }
}
