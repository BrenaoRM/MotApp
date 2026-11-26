package com.example.financacelular.ui

import com.example.financacelular.data.somaEmReais
import com.example.financacelular.data.InvestimentoEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** Como as barras do card de Aportes dividem o período da Análise. */
enum class GranularidadeAportes(val texto: String) { DIA("por dia"), SEMANA("por semana"), MES("por mês") }

/** Um tipo de ativo (categoria do aporte). [ehOutros] junta os menores quando há mais de 4 tipos. */
data class TipoDeAporte(val nome: String, val categorias: List<String>, val total: Double, val ehOutros: Boolean)

/** Uma barra do gráfico; [valores] segue a ordem de [AportesDoPeriodo.tipos]. */
data class BarraDeAportes(
    val rotulo: String,
    val subrotulo: String?,
    val mostrarRotulo: Boolean,
    val descricao: String,
    val valores: List<Double>
) {
    val total: Double get() = valores.sum()
}

data class AportesDoPeriodo(
    val granularidade: GranularidadeAportes,
    val tipos: List<TipoDeAporte>,
    val barras: List<BarraDeAportes>,
    val total: Double,
    val totalAnterior: Double,
    /** "out" quando o período é um mês; senão "período anterior". */
    val rotuloAnterior: String
) {
    val temAportes: Boolean get() = total > 0.0

    /** Variação do total sobre o período anterior do mesmo tamanho; nula se um dos dois for zero. */
    val variacaoPercentual: Int?
        get() = if (total > 0.0 && totalAnterior > 0.0) ((total - totalAnterior) / totalAnterior * 100).roundToInt() else null
}

private const val MAXIMO_DE_TIPOS = 4
private val MesesCurtos = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

fun granularidadeDe(periodo: Periodo): GranularidadeAportes = when {
    !periodo.mesesFechados && periodo.dias <= 15 -> GranularidadeAportes.DIA
    periodo.ehMesUnico || periodo.dias <= 31 -> GranularidadeAportes.SEMANA
    else -> GranularidadeAportes.MES
}

private fun LocalDate.mesCurto() = MesesCurtos[monthValue - 1]

/** Até 4 tipos, do maior para o menor; havendo mais, os menores se juntam em "Outros". */
private fun montarTipos(totais: List<Pair<String, Double>>): List<TipoDeAporte> {
    val comValor = totais.filter { it.second > 0.0 }.sortedByDescending { it.second }
    if (comValor.size <= MAXIMO_DE_TIPOS) {
        return comValor.map { (nome, total) -> TipoDeAporte(nome, listOf(nome), total, false) }
    }
    val principais = comValor.take(MAXIMO_DE_TIPOS - 1)
    val resto = comValor.drop(MAXIMO_DE_TIPOS - 1)
    return principais.map { (nome, total) -> TipoDeAporte(nome, listOf(nome), total, false) } +
        TipoDeAporte("Outros", resto.map { it.first }, resto.sumOf { it.second }, true)
}

private fun somaDoIntervalo(lista: List<InvestimentoEntity>, inicio: LocalDate, fim: LocalDate) =
    lista.filter { !it.data.isBefore(inicio) && !it.data.isAfter(fim) }

/** "out" quando o período é um mês; senão "período anterior". */
internal fun rotuloDoPeriodoAnterior(periodo: Periodo): String =
    if (periodo.ehMesUnico) periodo.deslocar(-1).inicio.mesCurto().lowercase() else "período anterior"

/** Um pedaço do período que vira uma barra (um dia, uma semana ou um mês). */
internal data class TrechoDoPeriodo(
    val rotulo: String,
    val subrotulo: String?,
    val mostrarRotulo: Boolean,
    val descricao: String,
    val inicio: LocalDate,
    val fim: LocalDate
)

/** Divide o [periodo] em dia, semana ou mês conforme [granularidadeDe]; usado pelos cards de Aportes e de Fluxo de caixa. */
internal fun dividirPeriodo(periodo: Periodo): List<TrechoDoPeriodo> = when (granularidadeDe(periodo)) {
    GranularidadeAportes.DIA -> (0 until periodo.dias).map { i ->
        val dia = periodo.inicio.plusDays(i.toLong())
        TrechoDoPeriodo(
            rotulo = dia.dayOfMonth.toString(),
            subrotulo = null,
            mostrarRotulo = periodo.dias <= 8 || i % 2 == 0 || i == periodo.dias - 1,
            descricao = "dia ${dia.dayOfMonth} ${dia.mesCurto().lowercase()}",
            inicio = dia,
            fim = dia
        )
    }
    GranularidadeAportes.SEMANA -> {
        val semanas = mutableListOf<TrechoDoPeriodo>()
        var inicio = periodo.inicio
        var numero = 1
        while (!inicio.isAfter(periodo.fim)) {
            val fim = minOf(inicio.plusDays(6), periodo.fim)
            semanas += TrechoDoPeriodo(
                rotulo = "S$numero",
                subrotulo = "${inicio.dayOfMonth}–${fim.dayOfMonth}",
                mostrarRotulo = true,
                descricao = "semana $numero, ${inicio.dayOfMonth} a ${fim.dayOfMonth} de ${fim.mesCurto().lowercase()}",
                inicio = inicio,
                fim = fim
            )
            inicio = fim.plusDays(1)
            numero++
        }
        semanas
    }
    GranularidadeAportes.MES -> (0 until periodo.quantidadeDeMeses).map { i ->
        val mes = YearMonth.from(periodo.inicio).plusMonths(i.toLong())
        TrechoDoPeriodo(
            rotulo = mes.atDay(1).mesCurto(),
            subrotulo = null,
            mostrarRotulo = true,
            descricao = mes.atDay(1).mesCurto().lowercase(),
            inicio = maxOf(mes.atDay(1), periodo.inicio),
            fim = minOf(mes.atEndOfMonth(), periodo.fim)
        )
    }
}

/**
 * Aportes do [periodo] da Análise, divididos em barras (dia, semana ou mês conforme o tamanho do período)
 * e por tipo de ativo. Nenhum valor é buscado fora de [investimentos].
 */
fun calcularAportes(investimentos: List<InvestimentoEntity>, periodo: Periodo): AportesDoPeriodo {
    val doPeriodo = somaDoIntervalo(investimentos, periodo.inicio, periodo.fim)
    val tipos = montarTipos(
        doPeriodo.groupBy { it.categoria.trim() }.map { (categoria, lista) -> categoria to lista.somaEmReais { it.valorInvestidoCentavos } }
    )
    val indicePorCategoria = tipos.withIndex().flatMap { (i, tipo) -> tipo.categorias.map { it to i } }.toMap()

    fun barra(rotulo: String, sub: String?, mostrar: Boolean, nome: String, inicio: LocalDate, fim: LocalDate): BarraDeAportes {
        val valores = DoubleArray(tipos.size)
        somaDoIntervalo(doPeriodo, inicio, fim).forEach { aporte ->
            indicePorCategoria[aporte.categoria.trim()]?.let { valores[it] += aporte.valorInvestido }
        }
        return BarraDeAportes(rotulo, sub, mostrar, nome, valores.toList())
    }

    val granularidade = granularidadeDe(periodo)
    val barras = dividirPeriodo(periodo).map { barra(it.rotulo, it.subrotulo, it.mostrarRotulo, it.descricao, it.inicio, it.fim) }

    val anterior = periodo.deslocar(-1)
    val totalAnterior = somaDoIntervalo(investimentos, anterior.inicio, anterior.fim).somaEmReais { it.valorInvestidoCentavos }
    return AportesDoPeriodo(
        granularidade = granularidade,
        tipos = tipos,
        barras = barras,
        total = doPeriodo.somaEmReais { it.valorInvestidoCentavos },
        totalAnterior = totalAnterior,
        rotuloAnterior = rotuloDoPeriodoAnterior(periodo)
    )
}
