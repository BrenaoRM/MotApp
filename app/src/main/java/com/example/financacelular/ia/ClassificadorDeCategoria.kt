package com.example.financacelular.ia

import com.example.financacelular.data.chaveDoEstabelecimento
import kotlin.math.exp

/** Exemplo de treino: vetor da descrição e a categoria que a pessoa deu. [peso] > 1 junta exemplos repetidos. */
data class ExemploRotulado(val vetor: FloatArray, val categoriaId: Long, val peso: Float = 1f)

/** Categoria da pessoa com o vetor do nome (vira protótipo e liga a categoria às genéricas). */
data class CategoriaParaIa(val id: Long, val nome: String, val vetorNome: FloatArray)

data class Probabilidade(val categoriaId: Long, val p: Float)

/**
 * Modelo de categoria do app, espelho de `modelo_app` em `ia/avaliar_categorias.py` (especificação em
 * `ia/resultados/categorias_personalizadas.md`): RL softmax pessoal com protótipos + prior genérico (embeddings ou
 * léxico) + memória exata do estabelecimento. Treino determinístico (W = 0, b = 0, lote completo). Contas em Double.
 */
class ClassificadorDeCategoria(private val cfg: ConfigIa) {

    /**
     * [memoria]: chave do estabelecimento ([chaveDoEstabelecimento]) → último rótulo que a pessoa deu.
     * Exemplos e memória de categorias que não estão em [categorias] são descartados (categoria apagada).
     */
    fun treinar(
        exemplos: List<ExemploRotulado>,
        categorias: List<CategoriaParaIa>,
        memoria: Map<String, Long> = emptyMap(),
    ): ModeloDeCategoria {
        val ids = categorias.map { it.id }
        val indice = ids.withIndex().associate { (i, id) -> id to i }
        val validos = exemplos.filter { it.categoriaId in indice }
        val c = categorias.size
        val d = cfg.dimensao
        val cc = cfg.classificador

        // Treino = exemplos (peso dado) + um protótipo por categoria (peso = pesoPrototipo)
        val xs = validos.map { it.vetor } + categorias.map { it.vetorNome }
        val ys = IntArray(xs.size) { i -> if (i < validos.size) indice.getValue(validos[i].categoriaId) else i - validos.size }
        val pesos = DoubleArray(xs.size) { i -> if (i < validos.size) validos[i].peso.toDouble() else cc.pesoPrototipo.toDouble() }
        val (w, b) = descidaDoGradiente(xs, ys, pesos, c, d)

        val contagem = DoubleArray(c)
        validos.forEach { contagem[indice.getValue(it.categoriaId)] += it.peso.toDouble() }
        val memoriaValida = memoria.filter { (chave, id) -> chave.isNotEmpty() && id in indice }
            .mapValues { indice.getValue(it.value) }
        return ModeloDeCategoria(
            cfg, ids, categorias.map { it.vetorNome }, w, b, mapear(categorias), contagem,
            n = validos.sumOf { it.peso.toDouble() }, memoria = memoriaValida,
        )
    }

    private fun descidaDoGradiente(
        xs: List<FloatArray>, ys: IntArray, pesos: DoubleArray, c: Int, d: Int,
    ): Pair<Array<DoubleArray>, DoubleArray> {
        val cc = cfg.classificador
        val w = Array(c) { DoubleArray(d) }
        val b = DoubleArray(c)
        if (c == 0) return w to b
        val somaPesos = pesos.sum()
        val taxa = cc.taxa.toDouble()
        val lambda = cc.lambdaL2.toDouble()
        val g = DoubleArray(c)
        val gradW = Array(c) { DoubleArray(d) }
        val gradB = DoubleArray(c)
        repeat(cc.epocas) {
            gradW.forEach { it.fill(0.0) }
            gradB.fill(0.0)
            for (i in xs.indices) {
                val x = xs[i]
                for (k in 0 until c) g[k] = b[k] + produto(w[k], x)
                softmaxNoLugar(g)
                val fator = pesos[i] / somaPesos
                for (k in 0 until c) {
                    val gik = fator * (g[k] - if (ys[i] == k) 1.0 else 0.0)
                    gradB[k] += gik
                    val linha = gradW[k]
                    for (j in 0 until d) linha[j] += gik * x[j]
                }
            }
            for (k in 0 until c) {
                val wk = w[k]
                val gk = gradW[k]
                for (j in 0 until d) wk[j] -= taxa * (gk[j] + lambda * wk[j])
                b[k] -= taxa * gradB[k]
            }
        }
        return w to b
    }

    /** g(c) = argmax_g cos(vetorNome_c, vetoresNomes_g) se ≥ similaridadeMinimaMapeamento; senão -1. */
    private fun mapear(categorias: List<CategoriaParaIa>): IntArray =
        IntArray(categorias.size) { c -> indiceGenerico(cfg.generico, categorias[c].vetorNome) }
}

/** Índice (em `gen.nomes`) da genérica mais parecida com o nome da categoria, ou -1 se nenhuma passa do mínimo. */
internal fun indiceGenerico(gen: ConfigGenerico, vetorNome: FloatArray): Int {
    var melhor = -1
    var maior = Double.NEGATIVE_INFINITY
    gen.vetoresNomes.forEachIndexed { g, v ->
        val s = produto(v, vetorNome)
        if (s > maior) { maior = s; melhor = g }
    }
    return if (melhor >= 0 && maior >= gen.similaridadeMinimaMapeamento) melhor else -1
}

/** Todos os passos de uma previsão (índices nas categorias do modelo; -1 = nenhum). Usado pelo golden. */
internal class DetalheDaPrevisao(
    val genericaLexico: Int,
    val s: DoubleArray,
    val prior: DoubleArray,
    val pPessoal: DoubleArray,
    val alfa: Double,
    val rotuloMemoria: Int,
    val pFinal: DoubleArray,
    val sugestao: Int,
)

class ModeloDeCategoria internal constructor(
    private val cfg: ConfigIa,
    internal val ids: List<Long>,
    private val vetoresNomes: List<FloatArray>,
    private val w: Array<DoubleArray>,
    private val b: DoubleArray,
    internal val mapa: IntArray,
    private val contagem: DoubleArray,
    private val n: Double,
    private val memoria: Map<String, Int>,
) {
    val categorias: Set<Long> = ids.toSet()

    /** Probabilidades por categoria, ordenadas da maior para a menor (soma 1). Sem [descricao], sem léxico nem memória. */
    fun prever(vetor: FloatArray, descricao: String? = null): List<Probabilidade> {
        if (ids.isEmpty()) return emptyList()
        val p = detalhar(vetor, descricao).pFinal
        return ids.indices.map { Probabilidade(ids[it], p[it].toFloat()) }.sortedByDescending { it.p }
    }

    /** Categoria sugerida, ou null: memória exata sempre; senão p ≥ limiar e (≥ minimoExemplos exemplos ou mapeada). */
    fun sugerir(vetor: FloatArray, descricao: String? = null): Probabilidade? {
        if (ids.isEmpty()) return null
        val det = detalhar(vetor, descricao)
        return if (det.sugestao < 0) null else Probabilidade(ids[det.sugestao], det.pFinal[det.sugestao].toFloat())
    }

    internal fun detalhar(vetor: FloatArray, descricao: String?): DetalheDaPrevisao {
        val c = ids.size
        val cc = cfg.classificador
        val pPessoal = DoubleArray(c) { b[it] + produto(w[it], vetor) }.also(::softmaxNoLugar)

        val lex = if (descricao == null) -1 else genericaDoLexico(descricao, cfg.lexico, cfg.generico.nomes)
        val s = distribuicaoGenerica(vetor, lex)
        val prior = priorNasCategorias(vetor, s)

        val alfa = n / (n + cc.alfaK)
        val pFinal = DoubleArray(c) { alfa * pPessoal[it] + (1 - alfa) * prior[it] }
        val rotulo = descricao?.let { memoria[chaveDoEstabelecimento(it)] } ?: -1
        val sugestao = if (rotulo >= 0) {
            pFinal.fill(0.0)
            pFinal[rotulo] = 1.0
            rotulo
        } else {
            val melhor = pFinal.indices.maxBy { pFinal[it] }
            val confiavel = contagem[melhor] >= cc.minimoExemplos || mapa[melhor] >= 0
            if (pFinal[melhor] >= cc.limiarSugestao && confiavel) melhor else -1
        }
        return DetalheDaPrevisao(lex, s, prior, pPessoal, alfa, rotulo, pFinal, sugestao)
    }

    /** Léxico casou → (1 − conf)/G + conf·1[g = g*]; senão softmax(pesos·x + vieses). */
    private fun distribuicaoGenerica(vetor: FloatArray, lex: Int): DoubleArray {
        val gen = cfg.generico
        val total = gen.nomes.size
        if (lex >= 0) {
            val conf = gen.confiancaLexico.toDouble()
            return DoubleArray(total) { (1 - conf) / total + if (it == lex) conf else 0.0 }
        }
        return DoubleArray(total) { gen.vieses[it] + produto(gen.pesos[it], vetor) }.also(::softmaxNoLugar)
    }

    /** prior = 0,5·q + 0,5·softmax(cos(x, vetorNome)/temperaturaPrior). */
    private fun priorNasCategorias(vetor: FloatArray, s: DoubleArray): DoubleArray {
        val c = ids.size
        val q = DoubleArray(c)
        val pegas = mapa.filter { it >= 0 }.toSet()
        for (k in 0 until c) if (mapa[k] >= 0) q[k] = s[mapa[k]] / mapa.count { it == mapa[k] }
        val soltas = (0 until c).filter { mapa[it] < 0 }
        if (soltas.isNotEmpty()) {
            val livre = s.indices.filter { it !in pegas }.sumOf { s[it] }
            soltas.forEach { q[it] = livre / soltas.size }
        }
        val total = q.sum()
        for (k in 0 until c) q[k] = if (total > 0) q[k] / total else 1.0 / c
        val temperatura = cfg.classificador.temperaturaPrior.toDouble()
        val z = DoubleArray(c) { produto(vetoresNomes[it], vetor) / temperatura }.also(::softmaxNoLugar)
        return DoubleArray(c) { 0.5 * q[it] + 0.5 * z[it] }
    }
}

/**
 * Índice (em [nomes]) da genérica do léxico, ou -1. Casa o termo que aparece como palavra(s) inteira(s) em
 * `sem_acento(normalizar_para_modelo(descricao))`; vence o menor índice (espelho de `generica_do_lexico`).
 */
internal fun genericaDoLexico(descricao: String, lexico: Map<String, String>, nomes: List<String>): Int {
    val t = " " + NormalizacaoTexto.semAcento(NormalizacaoTexto.paraModelo(descricao)) + " "
    return lexico.entries.filter { (termo, _) -> " $termo " in t }
        .map { nomes.indexOf(it.value) }.filter { it >= 0 }.minOrNull() ?: -1
}

private fun produto(a: DoubleArray, x: FloatArray): Double {
    var s = 0.0
    for (j in a.indices) s += a[j] * x[j]
    return s
}

private fun produto(a: FloatArray, x: FloatArray): Double {
    var s = 0.0
    for (j in a.indices) s += a[j].toDouble() * x[j]
    return s
}

private fun softmaxNoLugar(z: DoubleArray) {
    if (z.isEmpty()) return
    val maximo = z.max()
    var soma = 0.0
    for (k in z.indices) { z[k] = exp(z[k] - maximo); soma += z[k] }
    for (k in z.indices) z[k] /= soma
}
