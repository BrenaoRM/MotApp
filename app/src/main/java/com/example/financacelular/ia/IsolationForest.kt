package com.example.financacelular.ia

import java.util.Random
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Isolation Forest (Liu, Ting & Zhou, 2008), como o `IsolationForest` do sklearn: cada árvore isola uma subamostra
 * sem reposição de `min(amostra, n)` pontos com cortes aleatórios, até a altura `ceil(log2(amostra efetiva))`.
 * Ponto atípico = isolado com poucos cortes. Determinístico para a mesma [semente]. Sem Android: roda na JVM.
 */
class IsolationForest(private val arvores: Int, private val amostra: Int, private val semente: Long) {

    private sealed class No {
        class Folha(val tamanho: Int) : No()
        class Corte(val atributo: Int, val valor: Double, val esquerda: No, val direita: No) : No()
    }

    private var raizes: List<No> = emptyList()
    private var amostraEfetiva = 0

    fun treinar(x: List<DoubleArray>) {
        require(x.size >= 2) { "Isolation Forest precisa de ao menos 2 pontos" }
        val r = Random(semente)
        amostraEfetiva = min(amostra, x.size)
        val alturaMaxima = ceil(log2(max(amostraEfetiva, 2).toDouble())).toInt()
        val indices = IntArray(x.size) { it }
        raizes = List(arvores) {
            // Fisher-Yates parcial: as primeiras [amostraEfetiva] posições viram a subamostra
            for (i in 0 until amostraEfetiva) {
                val j = i + r.nextInt(x.size - i)
                val t = indices[i]; indices[i] = indices[j]; indices[j] = t
            }
            construir(List(amostraEfetiva) { x[indices[it]] }, 0, alturaMaxima, r)
        }
    }

    /** s(x) ∈ (0, 1): maior = mais atípico (≈ 0,5 ou menos = normal). Igual a `-score_samples` do sklearn. */
    fun pontuacao(x: DoubleArray): Double {
        val media = raizes.sumOf { caminho(it, x, 0) } / raizes.size
        return 2.0.pow(-media / c(amostraEfetiva))
    }

    private fun construir(pontos: List<DoubleArray>, altura: Int, alturaMaxima: Int, r: Random): No {
        if (altura >= alturaMaxima || pontos.size <= 1) return No.Folha(pontos.size)
        val d = pontos[0].size
        val minimos = DoubleArray(d) { j -> pontos.minOf { it[j] } }
        val maximos = DoubleArray(d) { j -> pontos.maxOf { it[j] } }
        // Atributo constante no nó não separa nada: sorteia entre os que variam (folha se nenhum varia)
        val variam = (0 until d).filter { minimos[it] < maximos[it] }
        if (variam.isEmpty()) return No.Folha(pontos.size)
        val j = variam[r.nextInt(variam.size)]
        val valor = minimos[j] + r.nextDouble() * (maximos[j] - minimos[j])
        val (esquerda, direita) = pontos.partition { it[j] <= valor }
        return No.Corte(
            j, valor,
            construir(esquerda, altura + 1, alturaMaxima, r),
            construir(direita, altura + 1, alturaMaxima, r),
        )
    }

    private tailrec fun caminho(no: No, x: DoubleArray, altura: Int): Double = when (no) {
        is No.Folha -> altura + c(no.tamanho)
        is No.Corte -> caminho(if (x[no.atributo] <= no.valor) no.esquerda else no.direita, x, altura + 1)
    }

    companion object {
        private const val EULER = 0.5772156649

        /** Comprimento médio do caminho de uma busca sem sucesso numa árvore binária de busca com [n] pontos. */
        internal fun c(n: Int): Double = when {
            n <= 1 -> 0.0
            n == 2 -> 1.0
            else -> 2 * (ln(n - 1.0) + EULER) - 2.0 * (n - 1) / n
        }
    }
}
