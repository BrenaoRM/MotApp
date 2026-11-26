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

    /** Uma inferência por texto, sem padding entre textos: a quantização int8 é dinâmica e o vetor não pode depender dos vizinhos. */
    override fun vetorizar(textos: List<String>): List<FloatArray> = textos.map(::vetorizarUm)

    private fun vetorizarUm(texto: String): FloatArray {
        val ids = listOf(tokenizador.codificar(texto, config.maxTokens))
        val entrada = Array(1) { LongArray(ids[0].size) { c -> ids[0][c].toLong() } }
        val mascara = Array(1) { LongArray(ids[0].size) { 1L } }
        OnnxTensor.createTensor(ambiente, entrada).use { tIds ->
            OnnxTensor.createTensor(ambiente, mascara).use { tMascara ->
                sessao.run(mapOf("input_ids" to tIds, "attention_mask" to tMascara)).use { saida ->
                    @Suppress("UNCHECKED_CAST")
                    val oculto = saida[0].value as Array<Array<FloatArray>>
                    return poolingENorma(oculto[0], ids[0].size)
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
