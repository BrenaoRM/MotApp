package com.example.financacelular.ui

import com.example.financacelular.data.somaEmReais
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlin.math.roundToInt

/** Uma barra do fluxo de caixa: ganhos e gastos de um dia, uma semana ou um mês. */
data class BarraDeFluxo(
    val rotulo: String,
    val subrotulo: String?,
    val mostrarRotulo: Boolean,
    val descricao: String,
    val ganhos: Double,
    val gastos: Double
) {
    val saldo: Double get() = ganhos - gastos
}

data class FluxoDoPeriodo(
    val granularidade: GranularidadeAportes,
    val barras: List<BarraDeFluxo>,
    val ganhos: Double,
    val gastos: Double,
    val ganhosAnterior: Double,
    val gastosAnterior: Double,
    /** "out" quando o período é um mês; senão "período anterior". */
    val rotuloAnterior: String
) {
    val saldo: Double get() = ganhos - gastos
    val saldoAnterior: Double get() = ganhosAnterior - gastosAnterior
    val temMovimento: Boolean get() = ganhos > 0.0 || gastos > 0.0
    val temBase: Boolean get() = ganhosAnterior > 0.0 || gastosAnterior > 0.0
}

/** Percentual do que entrou que sobrou (negativo se gastou mais); nulo se nada entrou. */
fun percentualPoupado(ganhos: Double, gastos: Double): Int? =
    if (ganhos > 0.0) ((ganhos - gastos) / ganhos * 100).roundToInt() else null

/**
 * Fluxo de caixa do [periodo] da Análise, dividido em barras (dia, semana ou mês, como em Aportes).
 * [transacoes] e [transacoesAnteriores] já vêm filtradas (período válido, sem pagamento de fatura);
 * aqui só se soma. Compra no crédito pode ter data fora do período da fatura: entra na barra mais próxima.
 */
fun calcularFluxo(
    transacoes: List<Transacao>,
    transacoesAnteriores: List<Transacao>,
    periodo: Periodo
): FluxoDoPeriodo {
    val trechos = dividirPeriodo(periodo)
    val ganhos = DoubleArray(trechos.size)
    val gastos = DoubleArray(trechos.size)

    transacoes.forEach { t ->
        val data = t.data.coerceIn(periodo.inicio, periodo.fim)
        val indice = trechos.indexOfFirst { !data.isBefore(it.inicio) && !data.isAfter(it.fim) }
        if (indice < 0) return@forEach
        if (t.tipo == TipoTransacao.RECEITA) ganhos[indice] += t.valor else gastos[indice] += t.valor
    }

    return FluxoDoPeriodo(
        granularidade = granularidadeDe(periodo),
        barras = trechos.mapIndexed { i, trecho ->
            BarraDeFluxo(trecho.rotulo, trecho.subrotulo, trecho.mostrarRotulo, trecho.descricao, ganhos[i], gastos[i])
        },
        ganhos = transacoes.filter { it.tipo == TipoTransacao.RECEITA }.somaEmReais { it.valorCentavos },
        gastos = transacoes.filter { it.tipo == TipoTransacao.DESPESA }.somaEmReais { it.valorCentavos },
        ganhosAnterior = transacoesAnteriores.filter { it.tipo == TipoTransacao.RECEITA }.somaEmReais { it.valorCentavos },
        gastosAnterior = transacoesAnteriores.filter { it.tipo == TipoTransacao.DESPESA }.somaEmReais { it.valorCentavos },
        rotuloAnterior = rotuloDoPeriodoAnterior(periodo)
    )
}
