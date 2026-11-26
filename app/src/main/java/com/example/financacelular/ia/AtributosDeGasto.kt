package com.example.financacelular.ia

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.expm1
import kotlin.math.ln1p
import kotlin.math.max

/**
 * Um gasto do hábito da pessoa, já filtrado e agrupado ([DetectorDeAtipicos.gastosParaAnalise]): compra parcelada
 * vira um gasto só, no dia da compra. [chave] identifica o estabelecimento (`"#cat<id>"` quando não há descrição).
 */
data class GastoParaAnalise(
    val transacaoId: Long,
    val data: LocalDate,
    val valorCentavos: Long,
    val categoriaId: Long,
    val chave: String,
)

/**
 * Atributos de um gasto relativos ao histórico da própria pessoa (porte do `build_features` do FinIA).
 * Os z são robustos (mediana e MAD) sobre `ln(1 + valor em reais)`.
 * [valorReferenciaCentavos]: mediana do estabelecimento se [chaveConhecida], senão a da categoria (se tem ≥ 3
 * gastos), senão null.
 */
data class AtributosGasto(
    val gasto: GastoParaAnalise,
    val logValor: Double,
    val zChave: Double,
    val zCategoria: Double,
    val duplicado: Boolean,
    val logIntervaloCategoria: Double,
    val noMesmoDia: Int,
    val valorReferenciaCentavos: Long?,
    val chaveConhecida: Boolean,
) {
    /** Ordem do FinIA (`FEATS`): logValor, zChave, zCategoria, duplicado (0/1), logIntervaloCategoria, noMesmoDia. */
    fun vetor(): DoubleArray = doubleArrayOf(
        logValor, zChave, zCategoria, if (duplicado) 1.0 else 0.0, logIntervaloCategoria, noMesmoDia.toDouble(),
    )
}

object AtributosDeGasto {
    /** Grupo (estabelecimento ou categoria) com menos gastos que isso não serve de referência. */
    private const val MINIMO_REFERENCIA = 3
    private const val INTERVALO_PADRAO_DIAS = 30.0
    private const val ESCALA_MAD = 1.4826

    private class Referencia(val mediana: Double, val mad: Double)

    /** Devolve um item por gasto, em ordem cronológica (desempate pelo id). */
    fun calcular(gastos: List<GastoParaAnalise>, cfg: ConfigAtipicos): List<AtributosGasto> {
        if (gastos.isEmpty()) return emptyList()
        val d = gastos.sortedWith(compareBy({ it.data }, { it.transacaoId }))
        val logs = DoubleArray(d.size) { ln1p(d[it].valorCentavos / 100.0) }
        val global = referencia(logs.toList())
        val porChave = referencias(d.indices.groupBy { d[it].chave }, logs)
        val porCategoria = referencias(d.indices.groupBy { d[it].categoriaId }, logs)
        val porDia = d.groupingBy { it.data }.eachCount()
        val piso = cfg.pisoMad.emDouble()
        val anteriorNaCategoria = HashMap<Long, LocalDate>()

        return d.mapIndexed { i, g ->
            val refChave = porChave[g.chave]
            val refCategoria = porCategoria[g.categoriaId]
            val intervalo = anteriorNaCategoria.put(g.categoriaId, g.data)
                ?.let { ChronoUnit.DAYS.between(it, g.data).toDouble() } ?: INTERVALO_PADRAO_DIAS
            AtributosGasto(
                gasto = g,
                logValor = logs[i],
                zChave = z(logs[i], refChave ?: refCategoria ?: global, piso),
                zCategoria = z(logs[i], refCategoria ?: global, piso),
                duplicado = duplicado(d, i, cfg.janelaDuplicataDias.toLong()),
                logIntervaloCategoria = ln1p(intervalo),
                noMesmoDia = porDia.getValue(g.data),
                valorReferenciaCentavos = (refChave ?: refCategoria)?.let { Math.round(expm1(it.mediana) * 100) },
                chaveConhecida = refChave != null,
            )
        }
    }

    private fun z(x: Double, ref: Referencia, piso: Double): Double = (x - ref.mediana) / (ESCALA_MAD * max(ref.mad, piso))

    /** Mesma chave e mesmo valor em até [janelaDias] dias antes (o primeiro de uma série nunca é duplicado). */
    private fun duplicado(d: List<GastoParaAnalise>, i: Int, janelaDias: Long): Boolean {
        val g = d[i]
        for (j in i - 1 downTo 0) {
            if (ChronoUnit.DAYS.between(d[j].data, g.data) > janelaDias) return false
            if (d[j].chave == g.chave && d[j].valorCentavos == g.valorCentavos) return true
        }
        return false
    }

    private fun <K> referencias(grupos: Map<K, List<Int>>, logs: DoubleArray): Map<K, Referencia> =
        grupos.filterValues { it.size >= MINIMO_REFERENCIA }.mapValues { (_, idx) -> referencia(idx.map { logs[it] }) }

    private fun referencia(valores: List<Double>): Referencia {
        val m = mediana(valores)
        return Referencia(m, mediana(valores.map { abs(it - m) }))
    }

    private fun mediana(valores: List<Double>): Double {
        val s = valores.sorted()
        val meio = s.size / 2
        return if (s.size % 2 == 1) s[meio] else (s[meio - 1] + s[meio]) / 2
    }
}

/** Float do config (`0.2f`) como o decimal do JSON (`0.2`), não `0.20000000298…`: mesmas contas que o Python. */
internal fun Float.emDouble(): Double = toString().toDouble()
