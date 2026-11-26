package com.example.financacelular.ia

import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.chaveDoEstabelecimento
import kotlin.math.ceil

/** Por que um gasto foi marcado, na ordem de prioridade em que é decidido. */
enum class MotivoAtipico { DUPLICADO, VALOR_ALTO_NO_LOCAL, VALOR_ALTO_NA_CATEGORIA, LOCAL_NOVO_CARO }

/**
 * Gasto fora do padrão. [valorReferenciaCentavos] é o valor típico que explica o [motivo] (mediana do local ou da
 * categoria); null quando não há um (duplicado, ou referência que não descreve o motivo).
 */
data class Atipico(
    val transacaoId: Long,
    val pontuacao: Double,
    val motivo: MotivoAtipico,
    val valorCentavos: Long,
    val valorReferenciaCentavos: Long?,
)

/**
 * Detector de gastos fora do padrão da própria pessoa (porte do FinIA): atributos de [AtributosDeGasto] +
 * [IsolationForest]; marca a fração `contaminacao` mais atípica e explica cada uma com um [MotivoAtipico].
 * Puro e sem Android: roda fora da thread principal.
 */
object DetectorDeAtipicos {

    /**
     * Só o hábito: despesas que não são pagamento de fatura, nem da categoria Fatura, nem recorrentes. Compra
     * parcelada (mesmo `grupoParcelamentoId`) vira um gasto com o valor total, no dia da compra, com o id da
     * primeira parcela. Chave = descrição normalizada; sem descrição ou igual ao nome da categoria → `"#cat<id>"`.
     */
    fun gastosParaAnalise(
        transacoes: List<Transacao>,
        categoriaFaturaId: Long?,
        nomesDasCategorias: Map<Long, String> = emptyMap(),
    ): List<GastoParaAnalise> {
        val habito = transacoes.filter {
            it.tipo == TipoTransacao.DESPESA && it.pagaFaturaDoCartaoId == null &&
                it.categoriaId != categoriaFaturaId && it.recorrenteId == null
        }
        val (parceladas, avulsas) = habito.partition { it.grupoParcelamentoId != null }
        val compras = parceladas.groupBy { it.grupoParcelamentoId }.values.map { parcelas ->
            val primeira = parcelas.minWith(compareBy({ it.numeroParcela }, { it.id }))
            primeira.copy(valorCentavos = parcelas.sumOf { it.valorCentavos }, data = primeira.dataCompra ?: primeira.data)
        }
        return (avulsas + compras).map { t ->
            GastoParaAnalise(t.id, t.data, t.valorCentavos, t.categoriaId, chave(t, nomesDasCategorias[t.categoriaId]))
        }
    }

    private fun chave(t: Transacao, nomeDaCategoria: String?): String {
        val descricao = t.descricao?.let(::chaveDoEstabelecimento).orEmpty()
        val categoria = nomeDaCategoria?.let(::chaveDoEstabelecimento)
        return if (descricao.isEmpty() || descricao == categoria) "#cat${t.categoriaId}" else descricao
    }

    /**
     * Atípicos dos últimos `mesesHistorico` meses (contados do gasto mais recente), do mais ao menos atípico.
     * Vazio com menos de `minimoGastos` gastos. Os [dispensados] saem depois da seleção: dispensar um não
     * promove outro.
     */
    fun detectar(gastos: List<GastoParaAnalise>, cfg: ConfigAtipicos, dispensados: Set<Long>): List<Atipico> {
        val pontuados = pontuar(gastos, cfg)
        if (pontuados.isEmpty()) return emptyList()
        val quantos = ceil(cfg.contaminacao.emDouble() * pontuados.size - 1e-6).toInt()
        val z = cfg.zLimite.emDouble()
        return pontuados
            .sortedWith(compareByDescending<Pair<AtributosGasto, Double>> { it.second }.thenBy { it.first.gasto.transacaoId })
            .take(quantos)
            .filter { it.first.gasto.valorCentavos >= cfg.valorMinimoCentavos }
            .mapNotNull { (a, s) ->
                motivo(a, z)?.let { (m, ref) -> Atipico(a.gasto.transacaoId, s, m, a.gasto.valorCentavos, ref) }
            }
            .filter { it.transacaoId !in dispensados }
    }

    /** Atributos e pontuação de cada gasto da janela (vazio se não há gastos suficientes). */
    internal fun pontuar(gastos: List<GastoParaAnalise>, cfg: ConfigAtipicos): List<Pair<AtributosGasto, Double>> {
        if (gastos.isEmpty()) return emptyList()
        val corte = gastos.maxOf { it.data }.minusMonths(cfg.mesesHistorico.toLong())
        val janela = gastos.filter { it.data.isAfter(corte) }
        if (janela.size < cfg.minimoGastos) return emptyList()
        val atributos = AtributosDeGasto.calcular(janela, cfg)
        val x = atributos.map { it.vetor() }
        val floresta = IsolationForest(cfg.arvores, cfg.amostra, cfg.semente.toLong()).apply { treinar(x) }
        return atributos.mapIndexed { i, a -> a to floresta.pontuacao(x[i]) }
    }

    /** Motivo e valor de referência, por prioridade; null quando nada explica o gasto (não é listado). */
    internal fun motivo(a: AtributosGasto, zLimite: Double): Pair<MotivoAtipico, Long?>? = when {
        a.duplicado -> MotivoAtipico.DUPLICADO to null
        a.zChave > zLimite && a.chaveConhecida -> MotivoAtipico.VALOR_ALTO_NO_LOCAL to a.valorReferenciaCentavos
        // Com local conhecido a referência é a do local, que não descreve "caro para a categoria"
        a.zCategoria > zLimite ->
            MotivoAtipico.VALOR_ALTO_NA_CATEGORIA to a.valorReferenciaCentavos.takeIf { !a.chaveConhecida }
        !a.chaveConhecida -> MotivoAtipico.LOCAL_NOVO_CARO to a.valorReferenciaCentavos
        else -> null
    }
}
