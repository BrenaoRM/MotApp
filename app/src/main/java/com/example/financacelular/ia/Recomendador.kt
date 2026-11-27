package com.example.financacelular.ia

import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import java.time.YearMonth

enum class CodigoRecomendacao { POUPANCA_BAIXA, EXCESSO_CATEGORIA, TENDENCIA_ALTA, ASSINATURAS, ORCAMENTO_ESTOURADO, GASTOS_ATIPICOS }

enum class Severidade { ALTA, MEDIA }

data class Recomendacao(
    val codigo: CodigoRecomendacao,
    val severidade: Severidade,
    val titulo: String,
    val detalhe: String,
    val porQue: String,
    val economiaMensalCentavos: Long,
    val categoriaId: Long?,
)

/**
 * Tudo que o [Recomendador] lê. [meses] são os meses **completos** (até 6, sem o corrente); [despesas] (mês →
 * categoria → centavos) já vem sem os atípicos marcados, que entram só pela regra própria; [genericaDaCategoria]
 * liga a categoria do usuário a uma genérica de [ConfigGenerico] (ausente = personalizada sem mapeamento).
 */
data class EntradaRecomendador(
    val meses: List<YearMonth>,
    val despesas: Map<YearMonth, Map<Long, Long>>,
    val receitas: Map<YearMonth, Long>,
    val recorrentesAtivas: List<DespesaRecorrente>,
    val orcamentos: List<Orcamento>,
    val atipicos: List<Atipico>,
    val genericaDaCategoria: Map<Long, String>,
    val nomeDaCategoria: Map<Long, String>,
)

/** Limites (fração da renda) das genéricas discricionárias; mesmos valores de `ConfigGenerico.discricionarias`. */
val LIMITES_DISCRICIONARIOS_PADRAO: Map<String, Float> =
    mapOf("Restaurantes e Delivery" to 0.12f, "Lazer e Assinaturas" to 0.10f, "Compras" to 0.10f)

/** Recomendações de hábito por regras (porte do `recommender.py` do FinIA). Puro e sem Android. */
object Recomendador {
    private const val META_POUPANCA = 0.10
    private const val MINIMO_MESES_TENDENCIA = 4
    private const val CRESCIMENTO_TENDENCIA = 1.25
    private const val DIFERENCA_TENDENCIA = 0.015
    private const val MINIMO_ASSINATURAS = 6
    private const val JANELA_ORCAMENTO = 3
    private const val MINIMO_ESTOUROS = 2
    private const val LIMIAR_ATIPICOS = 0.04

    /** Ordenada por severidade (ALTA primeiro) e depois por economia decrescente. */
    fun recomendar(
        e: EntradaRecomendador,
        discricionarias: Map<String, Float> = LIMITES_DISCRICIONARIOS_PADRAO,
    ): List<Recomendacao> {
        val meses = e.meses.sorted()
        if (meses.isEmpty()) return emptyList()
        val n = meses.size
        val mediaRenda = meses.sumOf { e.receitas[it] ?: 0L }.toDouble() / n
        val mediaDespesa = meses.sumOf { e.despesas[it].orEmpty().values.sum() }.toDouble() / n
        val ctx = Contexto(e, meses, mediaRenda, mediaDespesa, discricionarias)
        val todas = poupanca(ctx) + excessoETendencia(ctx) + assinaturas(ctx) + orcamento(ctx) + atipicos(ctx)
        return todas.sortedWith(compareBy<Recomendacao> { it.severidade }.thenByDescending { it.economiaMensalCentavos })
    }

    /** Inclinação `b1 = Sxy / Sxx` da reta de mínimos quadrados de [serie] contra 0, 1, 2… */
    internal fun inclinacao(serie: List<Double>): Double {
        if (serie.size < 2) return 0.0
        val xm = (serie.size - 1) / 2.0
        val ym = serie.average()
        var sxy = 0.0
        var sxx = 0.0
        serie.forEachIndexed { i, y ->
            sxy += (i - xm) * (y - ym)
            sxx += (i - xm) * (i - xm)
        }
        return sxy / sxx
    }

    private class Contexto(
        val e: EntradaRecomendador,
        val meses: List<YearMonth>,
        val mediaRenda: Double,
        val mediaDespesa: Double,
        val discricionarias: Map<String, Float>,
    ) {
        val n get() = meses.size
        fun nome(categoriaId: Long) = e.nomeDaCategoria[categoriaId] ?: "Categoria"
        fun serie(categoriaId: Long) = meses.map { (e.despesas[it]?.get(categoriaId) ?: 0L).toDouble() }
        fun limite(categoriaId: Long): Double? =
            e.genericaDaCategoria[categoriaId]?.let { discricionarias[it] }?.toString()?.toDouble() // 0.12f vira 0.12, não 0.11999999
        val categorias get() = meses.flatMap { e.despesas[it].orEmpty().keys }.distinct()
    }

    private fun centavos(x: Double) = Math.round(x)
    private fun reais(x: Double) = formatarReaisInteiros(centavos(x))
    private fun pct(fracao: Double) = Math.round(fracao * 100)
    private fun mesesTexto(n: Int) = if (n == 1) "1 mês" else "$n meses"

    private fun poupanca(c: Contexto): List<Recomendacao> {
        if (c.mediaRenda <= 0) return emptyList()
        // Comparação sem dividir (1 − 3600/4000 daria 0,0999…): guardar exatamente 10% não dispara
        if ((c.mediaRenda - c.mediaDespesa) * 10 >= c.mediaRenda) return emptyList()
        val taxa = 1 - c.mediaDespesa / c.mediaRenda
        val gap = META_POUPANCA * c.mediaRenda - (c.mediaRenda - c.mediaDespesa)
        val p = pct(taxa)
        val titulo = if (p >= 0) "Você está guardando $p% da renda" else "Você gasta ${-p}% a mais do que ganha"
        return listOf(
            Recomendacao(
                CodigoRecomendacao.POUPANCA_BAIXA, Severidade.ALTA, titulo,
                "Para chegar a 10%, reduza cerca de ${reais(gap)}/mês.",
                "Em ${mesesTexto(c.n)}, sua renda média foi ${reais(c.mediaRenda)} e a despesa média ${reais(c.mediaDespesa)}, " +
                    "abaixo da meta de guardar 10% da renda.",
                centavos(gap), null,
            ),
        )
    }

    private fun excessoETendencia(c: Contexto): List<Recomendacao> {
        if (c.mediaRenda <= 0) return emptyList()
        val saida = mutableListOf<Recomendacao>()
        for (id in c.categorias.sorted()) {
            val lim = c.limite(id) ?: continue
            val serie = c.serie(id)
            val media = serie.average()
            val parte = media / c.mediaRenda
            if (parte > lim) {
                saida += Recomendacao(
                    CodigoRecomendacao.EXCESSO_CATEGORIA, Severidade.MEDIA,
                    "${c.nome(id)}: ${pct(parte)}% da renda",
                    "Média de ${reais(media)}/mês; referência de ${pct(lim)}% da renda.",
                    "Comparamos sua média de ${mesesTexto(c.n)} (${reais(media)}) com ${pct(lim)}% da sua renda média (${reais(c.mediaRenda)}).",
                    centavos((parte - lim) * c.mediaRenda), id,
                )
            }
            tendencia(c, id, serie)?.let { saida += it }
        }
        return saida
    }

    /** Metades de mesmo tamanho (a do meio fica de fora com número ímpar de meses). Sem p-valor: ver [[Decisões]]. */
    private fun tendencia(c: Contexto, id: Long, serie: List<Double>): Recomendacao? {
        if (serie.size < MINIMO_MESES_TENDENCIA || inclinacao(serie) <= 0) return null
        val metade = serie.size / 2
        val antes = serie.take(metade).average()
        val depois = serie.takeLast(metade).average()
        if (antes <= 0 || depois <= CRESCIMENTO_TENDENCIA * antes) return null
        if (depois - antes <= DIFERENCA_TENDENCIA * c.mediaRenda) return null
        return Recomendacao(
            CodigoRecomendacao.TENDENCIA_ALTA, Severidade.MEDIA,
            "${c.nome(id)} em alta",
            "${reais(depois)} nos últimos meses, contra ${reais(antes)} antes (+${pct(depois / antes - 1)}%).",
            "A média dos $metade últimos meses (${reais(depois)}) passou 25% da média dos $metade primeiros (${reais(antes)}), " +
                "com a reta de tendência subindo e diferença acima de 1,5% da renda média (${reais(c.mediaRenda)}).",
            centavos(depois - antes), id,
        )
    }

    private fun assinaturas(c: Contexto): List<Recomendacao> {
        val valores = c.e.recorrentesAtivas.filter { it.tipo == TipoTransacao.DESPESA }.map { it.valorCentavos }.sorted()
        if (valores.size < MINIMO_ASSINATURAS) return emptyList()
        val meio = valores.size / 2
        val mediana = if (valores.size % 2 == 1) valores[meio] else (valores[meio - 1] + valores[meio] + 1) / 2
        val total = valores.sum()
        return listOf(
            Recomendacao(
                CodigoRecomendacao.ASSINATURAS, Severidade.MEDIA,
                "${valores.size} assinaturas (${formatarReaisInteiros(total)}/mês)",
                "Cancelar 2 delas economiza cerca de ${formatarReaisInteiros(2 * mediana)}/mês.",
                "Você tem ${valores.size} despesas recorrentes ativas (mínimo de $MINIMO_ASSINATURAS para avisar); " +
                    "2 vezes a mediana delas (${formatarReaisInteiros(mediana)}) dá ${formatarReaisInteiros(2 * mediana)}.",
                2 * mediana, null,
            ),
        )
    }

    private fun orcamento(c: Contexto): List<Recomendacao> {
        val janela = c.meses.takeLast(JANELA_ORCAMENTO)
        return c.e.orcamentos.map { it.categoriaId }.distinct().sorted().mapNotNull { id ->
            val excessos = janela.mapNotNull { mes ->
                val limite = limiteDoMes(c.e.orcamentos, id, mes) ?: return@mapNotNull null
                ((c.e.despesas[mes]?.get(id) ?: 0L) - limite).takeIf { it > 0 }
            }
            if (excessos.size < MINIMO_ESTOUROS) return@mapNotNull null
            val media = excessos.sum() / excessos.size
            Recomendacao(
                CodigoRecomendacao.ORCAMENTO_ESTOURADO, Severidade.MEDIA,
                "${c.nome(id)} passou do orçamento",
                "Excedeu em ${excessos.size} dos últimos ${janela.size} meses, em média ${formatarReaisInteiros(media)}.",
                "Nos últimos ${janela.size} meses completos, o gasto passou do limite em ${excessos.size} " +
                    "(mínimo de $MINIMO_ESTOUROS), com excesso médio de ${formatarReaisInteiros(media)}.",
                media, id,
            )
        }
    }

    private fun atipicos(c: Contexto): List<Recomendacao> {
        if (c.e.atipicos.isEmpty()) return emptyList()
        val total = c.e.atipicos.sumOf {
            if (it.motivo == MotivoAtipico.DUPLICADO) it.valorCentavos
            else maxOf(0L, it.valorCentavos - (it.valorReferenciaCentavos ?: 0L))
        }
        val impacto = total.toDouble() / c.n
        if (impacto <= 0 || impacto < LIMIAR_ATIPICOS * c.mediaDespesa) return emptyList()
        val duplicados = c.e.atipicos.count { it.motivo == MotivoAtipico.DUPLICADO }
        val qtd = c.e.atipicos.size
        val sufixo = when (duplicados) {
            0 -> "."
            1 -> "; 1 possível cobrança duplicada."
            else -> "; $duplicados possíveis cobranças duplicadas."
        }
        return listOf(
            Recomendacao(
                CodigoRecomendacao.GASTOS_ATIPICOS, Severidade.ALTA,
                if (qtd == 1) "1 gasto fora do seu padrão" else "$qtd gastos fora do seu padrão",
                "Impacto de ${reais(impacto)}/mês$sufixo",
                "Somamos o que passou do valor típico de cada gasto marcado (${formatarReaisInteiros(total)} em ${mesesTexto(c.n)}), " +
                    "${reais(impacto)} por mês, acima de 4% da despesa média (${reais(c.mediaDespesa)}).",
                centavos(impacto), null,
            ),
        )
    }
}
