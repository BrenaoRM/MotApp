package com.example.financacelular.ia

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.estaRealizada
import com.example.financacelular.data.idDaCategoriaFatura
import java.time.LocalDate
import java.time.YearMonth

/** Gasto fora do padrão pronto para a tela: [descricao] é o estabelecimento (ou a categoria, se não há descrição). */
data class AtipicoParaTela(val atipico: Atipico, val descricao: String, val data: LocalDate, val nomeCategoria: String)

sealed interface EstadoAssistente {
    data object Carregando : EstadoAssistente

    /** O config da IA não abriu (asset ausente ou corrompido): sem ele não há como analisar. */
    data object Indisponivel : EstadoAssistente

    data class PoucosDados(val gastos: Int, val minimo: Int) : EstadoAssistente
    data class Pronto(
        val economiaTotalCentavos: Long,
        val recomendacoes: List<Recomendacao>,
        val atipicosDoMes: List<AtipicoParaTela>,
    ) : EstadoAssistente
}

/** Resultado completo: o [estado] da tela e todos os [atipicos] da janela (o chat responde de qualquer mês). */
internal data class AnaliseCompleta(val estado: EstadoAssistente, val atipicos: List<Atipico>)

/** Junta detector e recomendador no que a aba Assistente mostra. Puro e sem Android: roda em `Dispatchers.Default`. */
object AnaliseDoAssistente {
    private const val MESES_MAXIMO = 6

    fun montar(
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        recorrentes: List<DespesaRecorrente>,
        orcamentos: List<Orcamento>,
        dispensados: Set<Long>,
        mesAtual: YearMonth,
        cfg: ConfigIa,
        genericaDaCategoria: Map<Long, String>,
        hoje: LocalDate = LocalDate.now(),
    ): EstadoAssistente =
        analisar(transacoes, categorias, recorrentes, orcamentos, dispensados, mesAtual, cfg, genericaDaCategoria, hoje).estado

    internal fun analisar(
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        recorrentes: List<DespesaRecorrente>,
        orcamentos: List<Orcamento>,
        dispensados: Set<Long>,
        mesAtual: YearMonth,
        cfg: ConfigIa,
        genericaDaCategoria: Map<Long, String>,
        hoje: LocalDate,
    ): AnaliseCompleta {
        val nomes = categorias.associate { it.id to it.nome }
        val faturaId = idDaCategoriaFatura(categorias)
        val gastos = DetectorDeAtipicos.gastosParaAnalise(transacoes, faturaId, nomes, hoje)
        if (gastos.size < cfg.atipicos.minimoGastos) {
            return AnaliseCompleta(EstadoAssistente.PoucosDados(gastos.size, cfg.atipicos.minimoGastos), emptyList())
        }
        val atipicos = DetectorDeAtipicos.detectar(gastos, cfg.atipicos, dispensados)
        val gastoPorId = gastos.associateBy { it.transacaoId }
        val porId = transacoes.associateBy { it.id }
        val doMes = atipicos.mapNotNull { a ->
            val gasto = gastoPorId[a.transacaoId] ?: return@mapNotNull null
            if (YearMonth.from(gasto.data) != mesAtual) return@mapNotNull null
            val nomeCategoria = nomes[gasto.categoriaId] ?: "Sem categoria"
            val descricao = porId[a.transacaoId]?.descricao?.takeIf { it.isNotBlank() } ?: nomeCategoria
            AtipicoParaTela(a, descricao, gasto.data, nomeCategoria)
        }
        val entrada = entradaDoRecomendador(transacoes, categorias, recorrentes, orcamentos, atipicos, mesAtual, genericaDaCategoria, faturaId, hoje)
        val recomendacoes = Recomendador.recomendar(entrada, cfg.generico.discricionarias)
        val economia = recomendacoes.sumOf { it.economiaMensalCentavos }
        return AnaliseCompleta(EstadoAssistente.Pronto(economia, recomendacoes, doMes), atipicos)
    }

    /**
     * Meses completos (até 6, só os que têm movimento, antes de [mesAtual]) no mês de competência (`anoMes`, ou a
     * data), só o realizado, sem pagamento de fatura nem categoria Fatura; os atípicos marcados saem das despesas.
     */
    private fun entradaDoRecomendador(
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        recorrentes: List<DespesaRecorrente>,
        orcamentos: List<Orcamento>,
        atipicos: List<Atipico>,
        mesAtual: YearMonth,
        genericaDaCategoria: Map<Long, String>,
        faturaId: Long?,
        hoje: LocalDate,
    ): EntradaRecomendador {
        val pagas = conjuntoFaturasPagas(transacoes)
        val validas = transacoes.filter { it.categoriaId != faturaId && it.estaRealizada(pagas, hoje) }
        val marcados = atipicos.map { it.transacaoId }.toSet()
        val grupos = transacoes.filter { it.id in marcados }.mapNotNull { it.grupoParcelamentoId }.toSet()
        fun mes(t: Transacao) = t.anoMes?.let { YearMonth.parse(it) } ?: YearMonth.from(t.data)
        val porMes = validas.groupBy { mes(it) }.filterKeys { it < mesAtual }
        val meses = porMes.keys.sorted().takeLast(MESES_MAXIMO)
        val despesas = meses.associateWith { m ->
            porMes.getValue(m)
                .filter { it.tipo == TipoTransacao.DESPESA && it.id !in marcados && it.grupoParcelamentoId !in grupos }
                .groupBy { it.categoriaId }.mapValues { (_, l) -> l.sumOf { it.valorCentavos } }
        }
        val receitas = meses.associateWith { m ->
            porMes.getValue(m).filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valorCentavos }
        }
        // O impacto divide pelos meses completos: só entram os atípicos que caem neles (não o mês corrente nem o futuro)
        val porId = transacoes.associateBy { it.id }
        val atipicosDosMeses = atipicos.filter { a -> porId[a.transacaoId]?.let { mes(it) in meses } == true }
        return EntradaRecomendador(
            meses, despesas, receitas, recorrentes, orcamentos, atipicosDosMeses, genericaDaCategoria,
            categorias.associate { it.id to it.nome },
        )
    }

    /** Motivo do alerta em uma frase curta; [valorCentavos] não entra, só o valor típico quando há. */
    fun textoDoMotivo(a: Atipico): String {
        val vezes = a.valorReferenciaCentavos?.takeIf { it > 0 }?.let { Math.round(a.valorCentavos.toDouble() / it) }
        return when (a.motivo) {
            MotivoAtipico.DUPLICADO -> "Cobrado mais de uma vez em poucos dias, com o mesmo valor."
            MotivoAtipico.VALOR_ALTO_NO_LOCAL ->
                if (vezes != null && vezes >= 2) "${vezes}× o que você costuma gastar aqui." else "Acima do que você costuma gastar aqui."
            MotivoAtipico.VALOR_ALTO_NA_CATEGORIA -> "Alto para essa categoria."
            MotivoAtipico.LOCAL_NOVO_CARO -> "Lugar novo com valor alto."
        }
    }

    /** Versão curta para a linha da lista. */
    fun resumoDoMotivo(a: Atipico): String = when (a.motivo) {
        MotivoAtipico.DUPLICADO -> "Cobrança repetida"
        MotivoAtipico.VALOR_ALTO_NO_LOCAL -> "Valor alto no local"
        MotivoAtipico.VALOR_ALTO_NA_CATEGORIA -> "Valor alto na categoria"
        MotivoAtipico.LOCAL_NOVO_CARO -> "Lugar novo e caro"
    }
}
