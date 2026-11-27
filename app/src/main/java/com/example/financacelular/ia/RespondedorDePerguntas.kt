package com.example.financacelular.ia

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.estaRealizada
import com.example.financacelular.data.idDaCategoriaFatura
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** Texto da resposta mais os chips (perguntas de exemplo) que a tela pode mostrar embaixo dela. */
data class RespostaDePergunta(val texto: String, val sugestoes: List<String>)

/**
 * Responde às perguntas com um texto montado a partir de números calculados dos lançamentos (sem LLM). Mesma regra
 * da aba Análise: só o que já está realizado, sem pagamento de fatura nem a categoria "Fatura", no mês de
 * competência (`anoMes`, ou a data). Valores em centavos, mostrados com [formatarReaisInteiros].
 */
object RespondedorDePerguntas {
    private const val TEXTO_DESCONHECIDA = "Ainda não sei responder isso. Tente: \"Onde estou gastando mais?\""
    private const val MESES_DA_MEDIA = 6

    private val SUGESTOES_DEPOIS: Map<Intencao, List<String>> = mapOf(
        Intencao.RANKING_CATEGORIAS to listOf("Gastei mais que no mês passado?", "Qual foi meu maior gasto?"),
        Intencao.GASTO_EM_CATEGORIA to listOf("Onde estou gastando mais?", "Quanto ainda posso gastar?"),
        Intencao.TOTAL_DO_MES to listOf("Onde estou gastando mais?", "Quanto sobrou esse mês?"),
        Intencao.COMPARAR_MES_ANTERIOR to listOf("Onde estou gastando mais?", "Como posso economizar?"),
        Intencao.MAIOR_GASTO to listOf("Tem algum gasto fora do padrão?", "Onde estou gastando mais?"),
        Intencao.ORCAMENTO_RESTANTE to listOf("Onde estou gastando mais?", "Como posso economizar?"),
        Intencao.ASSINATURAS to listOf("Como posso economizar?", "Quanto gastei esse mês?"),
        Intencao.POUPANCA to listOf("Como posso economizar?", "Quanto recebi esse mês?"),
        Intencao.RECEITAS to listOf("Quanto sobrou esse mês?", "Quanto gastei esse mês?"),
        Intencao.MEDIA_MENSAL to listOf("Gastei mais que no mês passado?", "Quanto gastei esse mês?"),
        Intencao.ATIPICOS to listOf("Como posso economizar?", "Qual foi meu maior gasto?"),
        Intencao.COMO_ECONOMIZAR to listOf("Tem algum gasto fora do padrão?", "Quais são minhas assinaturas?"),
    )

    /** A resposta como texto. Pura: roda em qualquer thread. */
    fun responder(
        p: Pergunta,
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        recorrentes: List<DespesaRecorrente>,
        orcamentos: List<Orcamento>,
        recomendacoes: List<Recomendacao>,
        atipicos: List<Atipico>,
        hoje: LocalDate,
    ): String {
        val base = Base(transacoes, categorias, hoje)
        return when (p.intencao) {
            Intencao.DESCONHECIDA -> TEXTO_DESCONHECIDA
            Intencao.TOTAL_DO_MES -> totalDoMes(p, base)
            Intencao.RANKING_CATEGORIAS -> ranking(p, base)
            Intencao.GASTO_EM_CATEGORIA -> gastoEmCategoria(p, base)
            Intencao.COMPARAR_MES_ANTERIOR -> comparar(p, base)
            Intencao.MAIOR_GASTO -> maiorGasto(p, base)
            Intencao.ORCAMENTO_RESTANTE -> orcamentoRestante(p, base, orcamentos)
            Intencao.ASSINATURAS -> assinaturas(recorrentes)
            Intencao.POUPANCA -> poupanca(p, base)
            Intencao.RECEITAS -> receitas(p, base)
            Intencao.MEDIA_MENSAL -> mediaMensal(p, base)
            Intencao.ATIPICOS -> atipicosDoMes(p, base, atipicos)
            Intencao.COMO_ECONOMIZAR -> comoEconomizar(recomendacoes)
        }
    }

    /**
     * A resposta pronta para a tela, calculada em `Dispatchers.Default`. Qualquer erro (menos cancelamento) vira a
     * resposta "não entendi": a conversa nunca derruba o app.
     */
    suspend fun responderEmSegundoPlano(
        p: Pergunta,
        transacoes: List<Transacao>,
        categorias: List<Categoria>,
        recorrentes: List<DespesaRecorrente>,
        orcamentos: List<Orcamento>,
        recomendacoes: List<Recomendacao>,
        atipicos: List<Atipico>,
        hoje: LocalDate,
    ): RespostaDePergunta = withContext(Dispatchers.Default) {
        try {
            val texto = responder(p, transacoes, categorias, recorrentes, orcamentos, recomendacoes, atipicos, hoje)
            RespostaDePergunta(texto, SUGESTOES_DEPOIS[p.intencao] ?: Perguntas.SUGESTOES_INICIAIS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RespostaDePergunta(TEXTO_DESCONHECIDA, Perguntas.SUGESTOES_INICIAIS)
        }
    }

    // ---------------------------------------------------------------- base de cálculo

    private class Base(private val transacoes: List<Transacao>, categorias: List<Categoria>, val hoje: LocalDate) {
        val nomes: Map<Long, String> = categorias.associate { it.id to it.nome }
        private val porId = transacoes.associateBy { it.id }
        val faturaId = idDaCategoriaFatura(categorias)
        private val pagas = conjuntoFaturasPagas(transacoes)

        /** Realizadas (a regra de [estaRealizada] já deixa de fora pagamento de fatura) e fora da categoria Fatura. */
        private val validas = transacoes.filter { it.categoriaId != faturaId && it.estaRealizada(pagas, hoje) }

        fun despesas(mes: YearMonth): List<Transacao> = deTipo(TipoTransacao.DESPESA, mes)
        fun receitas(mes: YearMonth): List<Transacao> = deTipo(TipoTransacao.RECEITA, mes)

        private fun deTipo(tipo: TipoTransacao, mes: YearMonth) =
            validas.filter { it.tipo == tipo && mesDe(it) == mes.toString() }

        /** Mês de competência, como a Análise: o da fatura (`anoMes`) ou, sem ele, o da data. */
        private fun mesDe(t: Transacao): String = t.anoMes ?: t.data.toString().take(7)

        fun transacao(id: Long): Transacao? = porId[id]

        fun nome(id: Long): String = nomes[id] ?: "Sem categoria"

        /** Total por categoria do mês, da maior para a menor. */
        fun porCategoria(mes: YearMonth): List<Pair<Long, Long>> =
            despesas(mes).groupBy { it.categoriaId }.map { (id, lista) -> id to lista.sumOf { it.valorCentavos } }
                .sortedByDescending { it.second }

        /**
         * Gasto por categoria como a tela de Orçamento (`gastoPorCategoriaNoMes`): toda despesa com `anoMes` igual ao
         * mês, sem filtro de realização (compra em fatura aberta já conta).
         */
        fun gastoDoOrcamento(mes: YearMonth): Map<Long, Long> =
            transacoes.filter { it.tipo == TipoTransacao.DESPESA && mesDe(it) == mes.toString() }
                .groupBy { it.categoriaId }.mapValues { (_, l) -> l.sumOf { it.valorCentavos } }

        fun meses(): List<YearMonth> = validas.filter { it.tipo == TipoTransacao.DESPESA }
            .map { YearMonth.parse(mesDe(it)) }.distinct()
    }

    // ---------------------------------------------------------------- respostas

    private fun semGastos(mes: YearMonth, hoje: LocalDate) = "Não encontrei gastos em ${rotulo(mes, hoje)}."

    private fun rotulo(mes: YearMonth, hoje: LocalDate): String {
        val nome = Perguntas.NOMES_DOS_MESES[mes.monthValue - 1]
        return if (mes.year == hoje.year) nome else "$nome de ${mes.year}"
    }

    private fun porcento(parte: Long, total: Long): Int = if (total <= 0) 0 else (parte * 100.0 / total).roundToInt()

    private fun totalDoMes(p: Pergunta, b: Base): String {
        val total = b.despesas(p.mes).sumOf { it.valorCentavos }
        if (total == 0L) return semGastos(p.mes, b.hoje)
        return "Em ${rotulo(p.mes, b.hoje)} você gastou ${formatarReaisInteiros(total)}."
    }

    private fun ranking(p: Pergunta, b: Base): String {
        val categorias = b.porCategoria(p.mes)
        val total = categorias.sumOf { it.second }
        if (total == 0L) return semGastos(p.mes, b.hoje)
        val (id, valor) = categorias.first()
        val inicio = "Em ${rotulo(p.mes, b.hoje)} você gastou mais com ${b.nome(id)}: " +
            "${formatarReaisInteiros(valor)} (${porcento(valor, total)}%)."
        val seguintes = categorias.drop(1).take(2).map { "${b.nome(it.first)} (${formatarReaisInteiros(it.second)})" }
        return when (seguintes.size) {
            0 -> inicio
            1 -> "$inicio Depois vem ${seguintes[0]}."
            else -> "$inicio Depois vêm ${seguintes[0]} e ${seguintes[1]}."
        }
    }

    private fun gastoEmCategoria(p: Pergunta, b: Base): String {
        val id = p.categoriaId?.takeIf { it in b.nomes }
            ?: return "De qual categoria? Tente: \"quanto gastei com Mercado\"."
        val categorias = b.porCategoria(p.mes)
        val total = categorias.sumOf { it.second }
        if (total == 0L) return semGastos(p.mes, b.hoje)
        val valor = categorias.firstOrNull { it.first == id }?.second ?: 0L
        val mes = rotulo(p.mes, b.hoje)
        if (valor == 0L) return "Em $mes você não gastou com ${b.nome(id)}."
        return "Em $mes você gastou ${formatarReaisInteiros(valor)} com ${b.nome(id)} (${porcento(valor, total)}% do total)."
    }

    private fun comparar(p: Pergunta, b: Base): String {
        val anterior = p.mes.minusMonths(1)
        val atuais = b.porCategoria(p.mes)
        val total = atuais.sumOf { it.second }
        if (total == 0L) return semGastos(p.mes, b.hoje)
        val anteriores = b.porCategoria(anterior)
        val totalAnterior = anteriores.sumOf { it.second }
        if (totalAnterior == 0L) return "Não encontrei gastos em ${rotulo(anterior, b.hoje)} para comparar."
        val mes = rotulo(p.mes, b.hoje)
        val mesAnterior = rotulo(anterior, b.hoje)
        val diferenca = total - totalAnterior
        if (diferenca == 0L) return "Em $mes você gastou o mesmo que em $mesAnterior."
        val valor = formatarReaisInteiros(abs(diferenca))
        val inicio = "Em $mes você gastou $valor ${if (diferenca > 0) "a mais" else "a menos"} que em $mesAnterior."
        val antes = anteriores.toMap()
        val subiu = atuais.map { (id, v) -> id to v - (antes[id] ?: 0L) }.filter { it.second > 0 }.maxByOrNull { it.second }
        return if (subiu == null) inicio else "$inicio Quem mais subiu foi ${b.nome(subiu.first)} (+${formatarReaisInteiros(subiu.second)})."
    }

    private fun maiorGasto(p: Pergunta, b: Base): String {
        val categoria = p.categoriaId?.takeIf { it in b.nomes }
        val gastos = b.despesas(p.mes)
        if (gastos.isEmpty()) return semGastos(p.mes, b.hoje)
        val candidatos = if (categoria == null) gastos else gastos.filter { it.categoriaId == categoria }
        val maior = candidatos.maxWithOrNull(compareBy({ it.valorCentavos }, { -it.id }))
        val mes = rotulo(p.mes, b.hoje)
        if (maior == null) return "Em $mes você não gastou com ${b.nome(categoria!!)}."
        val onde = maior.descricao?.takeIf { it.isNotBlank() } ?: b.nome(maior.categoriaId)
        val dia = (maior.dataCompra ?: maior.data).dayOfMonth
        val em = if (categoria == null) "" else " em ${b.nome(categoria)}"
        return "Seu maior gasto de $mes$em foi ${formatarReaisInteiros(maior.valorCentavos)}: $onde, no dia $dia."
    }

    private fun orcamentoRestante(p: Pergunta, b: Base, orcamentos: List<Orcamento>): String {
        val categoria = p.categoriaId?.takeIf { it in b.nomes }
        val mes = rotulo(p.mes, b.hoje)
        // Mesma regra da tela de Orçamento: limite herdado do mais recente até o mês, 0 = sem orçamento, sem "Fatura"
        val limites = orcamentos.map { it.categoriaId }.distinct()
            .filter { it != b.faturaId && (categoria == null || it == categoria) }
            .mapNotNull { id -> limiteDoMes(orcamentos, id, p.mes)?.let { id to it } }
        if (limites.isEmpty()) {
            return if (categoria == null) "Você não definiu orçamento para $mes."
            else "Você não definiu orçamento de ${b.nome(categoria)} para $mes."
        }
        val gastoPorCategoria = b.gastoDoOrcamento(p.mes)
        val limite = limites.sumOf { it.second }
        val gasto = limites.sumOf { gastoPorCategoria[it.first] ?: 0L }
        val orcamento = if (categoria == null) "orçamento de $mes" else "orçamento de ${b.nome(categoria)} em $mes"
        val restante = limite - gasto
        if (restante < 0) return "Você passou ${formatarReaisInteiros(-restante)} do $orcamento (${formatarReaisInteiros(limite)})."
        val inicio = "Do $orcamento ainda restam ${formatarReaisInteiros(restante)} de " +
            "${formatarReaisInteiros(limite)} (${porcento(gasto, limite)}% usado)."
        if (categoria != null || limites.size < 2) return inicio
        val maisPerto = limites.maxByOrNull { (gastoPorCategoria[it.first] ?: 0L).toDouble() / it.second } ?: return inicio
        val pct = porcento(gastoPorCategoria[maisPerto.first] ?: 0L, maisPerto.second)
        return "$inicio Mais perto do limite: ${b.nome(maisPerto.first)} ($pct%)."
    }

    private fun assinaturas(recorrentes: List<DespesaRecorrente>): String {
        val lista = recorrentes.filter { it.tipo == TipoTransacao.DESPESA }.sortedByDescending { it.valorCentavos }
        if (lista.isEmpty()) return "Não encontrei assinaturas."
        val total = formatarReaisInteiros(lista.sumOf { it.valorCentavos })
        val quantas = if (lista.size == 1) "1 assinatura" else "${lista.size} assinaturas"
        val itens = lista.take(3).map { "${it.nome} (${formatarReaisInteiros(it.valorCentavos)})" }
        val enumerado = if (itens.size == 1) itens[0] else itens.dropLast(1).joinToString(", ") + " e " + itens.last()
        return if (lista.size <= 3) "Você tem $quantas, $total por mês: $enumerado."
        else "Você tem $quantas, $total por mês. As maiores: $enumerado."
    }

    private fun poupanca(p: Pergunta, b: Base): String {
        val mes = rotulo(p.mes, b.hoje)
        val renda = b.receitas(p.mes).sumOf { it.valorCentavos }
        if (renda == 0L) return "Não encontrei receitas em $mes, então não dá para calcular quanto sobrou."
        val gasto = b.despesas(p.mes).sumOf { it.valorCentavos }
        val inicio = "Em $mes você recebeu ${formatarReaisInteiros(renda)} e gastou ${formatarReaisInteiros(gasto)}:"
        val saldo = renda - gasto
        return if (saldo >= 0) "$inicio sobraram ${formatarReaisInteiros(saldo)} (${porcento(saldo, renda)}% da renda)."
        else "$inicio faltaram ${formatarReaisInteiros(-saldo)}."
    }

    private fun receitas(p: Pergunta, b: Base): String {
        val mes = rotulo(p.mes, b.hoje)
        val entradas = b.receitas(p.mes)
        if (entradas.isEmpty()) return "Não encontrei receitas em $mes."
        val inicio = "Em $mes você recebeu ${formatarReaisInteiros(entradas.sumOf { it.valorCentavos })}."
        if (entradas.size < 2) return inicio
        val maior = entradas.maxWith(compareBy({ it.valorCentavos }, { -it.id }))
        val de = maior.descricao?.takeIf { it.isNotBlank() } ?: b.nome(maior.categoriaId)
        return "$inicio A maior entrada foi ${formatarReaisInteiros(maior.valorCentavos)} ($de)."
    }

    /** Média dos meses fechados (os até 6 antes do mês de [Base.hoje]) que têm gasto, só da categoria se houver. */
    private fun mediaMensal(p: Pergunta, b: Base): String {
        val atual = YearMonth.from(b.hoje)
        val meses = b.meses().filter { it < atual && it >= atual.minusMonths(MESES_DA_MEDIA.toLong()) }
        if (meses.isEmpty()) return "Ainda não há um mês fechado com gastos para calcular a média."
        val categoria = p.categoriaId?.takeIf { it in b.nomes }
        val total = meses.sumOf { mes ->
            b.despesas(mes).filter { categoria == null || it.categoriaId == categoria }.sumOf { it.valorCentavos }
        }
        val periodo = if (meses.size == 1) "último mês" else "últimos ${meses.size} meses"
        val com = if (categoria == null) "" else " com ${b.nome(categoria)}"
        return "Em média você gasta ${formatarReaisInteiros(total / meses.size)} por mês$com ($periodo)."
    }

    private fun atipicosDoMes(p: Pergunta, b: Base, atipicos: List<Atipico>): String {
        val itens = atipicos.sortedByDescending { it.pontuacao }.mapNotNull { a ->
            val t = b.transacao(a.transacaoId)?.takeIf { YearMonth.from(it.dataCompra ?: it.data) == p.mes } ?: return@mapNotNull null
            val onde = t.descricao?.takeIf { it.isNotBlank() } ?: b.nome(t.categoriaId)
            "$onde, ${formatarReaisInteiros(a.valorCentavos)} (${motivo(a)})"
        }.take(3)
        if (itens.isEmpty()) return "Não vi nada fora do padrão em ${rotulo(p.mes, b.hoje)}."
        return "Fora do padrão em ${rotulo(p.mes, b.hoje)}: ${itens.joinToString("; ")}."
    }

    private fun motivo(a: Atipico): String {
        val tipico = a.valorReferenciaCentavos?.let { ", típico ${formatarReaisInteiros(it)}" }.orEmpty()
        return when (a.motivo) {
            MotivoAtipico.DUPLICADO -> "possível duplicado"
            MotivoAtipico.VALOR_ALTO_NO_LOCAL -> "acima do comum no local$tipico"
            MotivoAtipico.VALOR_ALTO_NA_CATEGORIA -> "acima do comum na categoria$tipico"
            MotivoAtipico.LOCAL_NOVO_CARO -> "local novo e caro$tipico"
        }
    }

    private fun comoEconomizar(recomendacoes: List<Recomendacao>): String {
        if (recomendacoes.isEmpty()) return "Não vi nada para ajustar agora."
        return "Para economizar: ${recomendacoes.take(3).joinToString("; ") { it.titulo }}."
    }
}
