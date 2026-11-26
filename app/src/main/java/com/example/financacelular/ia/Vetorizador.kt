package com.example.financacelular.ia

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlin.math.sqrt

/** Transforma textos (já normalizados por [NormalizacaoTexto]) em vetores L2-normalizados. */
interface Vetorizador {
    val versao: Int
    fun vetorizar(textos: List<String>): List<FloatArray>
}

class VetorizadorOnnx private constructor(
    private val ambiente: OrtEnvironment,
    private val sessao: OrtSession,
    private val tokenizador: Tokenizador,
    private val config: ConfigIa,
) : Vetorizador, AutoCloseable {

    override val versao: Int get() = config.versaoModelo

    override fun vetorizar(textos: List<String>): List<FloatArray> =
        textos.chunked(LOTE).flatMap(::vetorizarLote)

    private fun vetorizarLote(textos: List<String>): List<FloatArray> {
        val ids = textos.map { tokenizador.codificar(it, config.maxTokens) }
        val maior = ids.maxOf { it.size }
        val entrada = Array(ids.size) { r ->
            LongArray(maior) { c -> (if (c < ids[r].size) ids[r][c] else tokenizador.pad).toLong() }
        }
        val mascara = Array(ids.size) { r -> LongArray(maior) { c -> if (c < ids[r].size) 1L else 0L } }
        OnnxTensor.createTensor(ambiente, entrada).use { tIds ->
            OnnxTensor.createTensor(ambiente, mascara).use { tMascara ->
                sessao.run(mapOf("input_ids" to tIds, "attention_mask" to tMascara)).use { saida ->
                    @Suppress("UNCHECKED_CAST")
                    val oculto = saida[0].value as Array<Array<FloatArray>>
                    return List(ids.size) { r -> poolingENorma(oculto[r], ids[r].size) }
                }
            }
        }
    }

    /** Média dos `n` primeiros tokens (o resto é padding) e depois L2. */
    private fun poolingENorma(tokens: Array<FloatArray>, n: Int): FloatArray {
        val v = FloatArray(config.dimensao)
        for (t in 0 until n) for (d in v.indices) v[d] += tokens[t][d]
        var soma = 0.0
        for (d in v.indices) {
            v[d] /= n
            soma += v[d] * v[d]
        }
        val norma = sqrt(soma).toFloat().coerceAtLeast(1e-12f)
        for (d in v.indices) v[d] /= norma
        return v
    }

    override fun close() {
        sessao.close()
    }

    companion object {
        private const val LOTE = 64

        /** Devolve `null` se qualquer coisa falhar (asset ausente ou corrompido, sem `.so` da ABI, falta de memória). */
        fun abrir(
            context: Context,
            config: ConfigIa,
            caminhoModelo: String = "ia/modelo.onnx",
            caminhoTokenizador: String = "ia/tokenizador.json",
        ): VetorizadorOnnx? = try {
            val bytes = context.assets.open(caminhoModelo).use { it.readBytes() }
            val json = context.assets.open(caminhoTokenizador).use { it.readBytes().toString(Charsets.UTF_8) }
            criar(bytes, Tokenizador.deJson(json), config)
        } catch (_: Throwable) {
            null
        }

        internal fun criar(modelo: ByteArray, tokenizador: Tokenizador, config: ConfigIa): VetorizadorOnnx {
            val ambiente = OrtEnvironment.getEnvironment()
            val opcoes = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            return VetorizadorOnnx(ambiente, ambiente.createSession(modelo, opcoes), tokenizador, config)
        }
    }
}
