package com.example.financacelular.ia

import org.json.JSONObject
import java.text.Normalizer

/**
 * SentencePiece Unigram do XLM-R podado: Viterbi por palavra sobre as peças de `tokenizador.json`.
 * Espelho de `ia/exportar_modelo.py::viterbi_de_json`; o índice na lista de peças é o id.
 */
class Tokenizador(
    pecas: List<Pair<String, Float>>,
    private val unk: Int,
    private val bos: Int,
    private val eos: Int,
    val pad: Int,
    val prefixo: String,
) {
    private val indiceDaPeca = HashMap<String, Int>(pecas.size * 2)
    private val pontuacao = DoubleArray(pecas.size)
    private val maiorPeca: Int

    init {
        val especiais = setOf(unk, bos, eos, pad)
        var maior = 1
        pecas.forEachIndexed { id, (texto, score) ->
            if (id in especiais) return@forEachIndexed
            indiceDaPeca[texto] = id
            pontuacao[id] = score.toDouble()
            maior = maxOf(maior, texto.codePointCount(0, texto.length))
        }
        maiorPeca = maior
    }

    fun codificar(texto: String, maxTokens: Int): IntArray {
        val limite = maxOf(maxTokens - 2, 0)
        val ids = ArrayList<Int>()
        for (palavra in palavrasDe(Normalizer.normalize(prefixo + texto, Normalizer.Form.NFKC))) {
            if (ids.size >= limite) break
            ids += segmentar(MARCADOR + palavra)
        }
        val miolo = if (ids.size > limite) ids.subList(0, limite) else ids
        return IntArray(miolo.size + 2).also {
            it[0] = bos
            miolo.forEachIndexed { i, id -> it[i + 1] = id }
            it[it.size - 1] = eos
        }
    }

    private fun segmentar(palavra: String): List<Int> {
        val cps = palavra.codePoints().toArray()
        val n = cps.size
        val melhor = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        melhor[0] = 0.0
        val voltaInicio = IntArray(n + 1)
        val voltaId = IntArray(n + 1) { -1 }
        for (i in 0 until n) {
            if (melhor[i] == Double.NEGATIVE_INFINITY) continue
            var achouUm = false
            for (j in i + 1..minOf(n, i + maiorPeca)) {
                val id = indiceDaPeca[String(cps, i, j - i)] ?: continue
                if (j == i + 1) achouUm = true
                val total = melhor[i] + pontuacao[id]
                if (total > melhor[j]) {
                    melhor[j] = total
                    voltaInicio[j] = i
                    voltaId[j] = id
                }
            }
            if (!achouUm && melhor[i] > melhor[i + 1]) { // caractere sem peça: <unk> (score 0)
                melhor[i + 1] = melhor[i]
                voltaInicio[i + 1] = i
                voltaId[i + 1] = unk
            }
        }
        val ids = ArrayList<Int>()
        var j = n
        while (j > 0) {
            ids += voltaId[j]
            j = voltaInicio[j]
        }
        ids.reverse()
        // <unk> seguidos viram um só
        return ids.filterIndexed { k, id -> !(id == unk && k > 0 && ids[k - 1] == unk) }
    }

    companion object {
        private const val MARCADOR = "▁"

        /** Como `str.split()` do Python: qualquer espaço Unicode, sem itens vazios. */
        private fun palavrasDe(texto: String): List<String> {
            val saida = ArrayList<String>()
            val atual = StringBuilder()
            var i = 0
            while (i < texto.length) {
                val cp = texto.codePointAt(i)
                i += Character.charCount(cp)
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85) {
                    if (atual.isNotEmpty()) {
                        saida += atual.toString()
                        atual.setLength(0)
                    }
                } else {
                    atual.appendCodePoint(cp)
                }
            }
            if (atual.isNotEmpty()) saida += atual.toString()
            return saida
        }

        fun deJson(json: String): Tokenizador {
            val o = JSONObject(json)
            val arr = o.getJSONArray("pecas")
            val pecas = List(arr.length()) { k ->
                val par = arr.getJSONArray(k)
                par.getString(0) to par.getDouble(1).toFloat()
            }
            return Tokenizador(
                pecas, o.getInt("unk"), o.getInt("bos"), o.getInt("eos"), o.getInt("pad"), o.getString("prefixo"),
            )
        }
    }
}
