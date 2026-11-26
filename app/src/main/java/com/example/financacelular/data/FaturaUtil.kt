package com.example.financacelular.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Cartão ao qual pertencem os dados criados antes do suporte a vários cartões
 * (pagamentos antigos "Pagamento de Fatura - yyyy-MM", sem id do cartão).
 */
const val ID_CARTAO_PADRAO = 1L

private const val PREFIXO_PAGAMENTO = "Pagamento de Fatura"

/** Identifica uma fatura: um cartão em um mês ("yyyy-MM"). */
data class FaturaChave(val cartaoId: Long, val anoMes: String)

/**
 * Descrição gravada na transação de pagamento, só para mostrar (o cartão padrão mantém o formato antigo).
 * Quem identifica o pagamento é [Transacao.pagaFaturaDoCartaoId], não o texto.
 */
fun descricaoPagamentoFatura(cartaoId: Long, anoMes: String): String =
    if (cartaoId == ID_CARTAO_PADRAO) "$PREFIXO_PAGAMENTO - $anoMes"
    else "$PREFIXO_PAGAMENTO - $anoMes - $cartaoId"

/** True para transação de pagamento de fatura (de qualquer cartão), pelo campo próprio; a descrição não conta. */
fun Transacao.ehPagamentoDeFatura(): Boolean = faturaQuitadaPorEstePagamento() != null

/** Se esta transação é um pagamento de fatura, devolve qual fatura (cartão + mês) ela quita. */
fun Transacao.faturaQuitadaPorEstePagamento(): FaturaChave? {
    val cartao = pagaFaturaDoCartaoId ?: return null
    return FaturaChave(cartao, anoMes ?: return null)
}

/** Fatura (cartão + mês) em que esta compra cai; null se não for compra de cartão. */
fun Transacao.chaveFatura(): FaturaChave? =
    cartaoId?.let { FaturaChave(it, anoMes ?: data.toString().take(7)) }

/** Conjunto de faturas já pagas, por cartão e mês. Calculado uma vez, consultado em O(1). */
fun conjuntoFaturasPagas(todas: List<Transacao>): Set<FaturaChave> {
    val resultado = HashSet<FaturaChave>()
    for (t in todas) t.faturaQuitadaPorEstePagamento()?.let { resultado.add(it) }
    return resultado
}

/** True se a compra pertence a um cartão e a fatura desse cartão naquele mês já foi paga. */
fun Transacao.estaComFaturaPaga(pagas: Set<FaturaChave>): Boolean =
    chaveFatura()?.let { it in pagas } ?: false

/**
 * Data de vencimento da fatura de [anoMes] (mês em que a fatura fecha).
 * Se o vencimento é anterior ao fechamento, a fatura vence no mês seguinte.
 */
fun calcularVencimentoFatura(anoMes: YearMonth, diaVencimento: Int, diaFechamento: Int): LocalDate {
    val mesDoVencimento = if (diaVencimento < diaFechamento) anoMes.plusMonths(1) else anoMes
    return mesDoVencimento.atDay(diaVencimento.coerceIn(1, mesDoVencimento.lengthOfMonth()))
}

/**
 * Mês da fatura em que uma compra no cartão cai. Compra no dia do fechamento (ou depois) vai para o
 * mês seguinte; em meses curtos o cartão fecha no último dia (ex.: fechamento 31 em fevereiro).
 */
fun mesDaFaturaDaCompra(data: LocalDate, diaFechamento: Int): YearMonth {
    val fechamento = minOf(diaFechamento, data.lengthOfMonth())
    val mes = YearMonth.from(data)
    return if (data.dayOfMonth >= fechamento) mes.plusMonths(1) else mes
}

/** Igual a [mesDaFaturaDaCompra], no formato "yyyy-MM" gravado em `Transacao.anoMes`. */
fun anoMesDaCompraNoCartao(data: LocalDate, diaFechamento: Int): String =
    mesDaFaturaDaCompra(data, diaFechamento).format(FORMATO_ANO_MES)

/**
 * `anoMes` de uma cobrança de recorrente: no cartão ([diaFechamento] não nulo), o mês da fatura, igual a
 * uma compra avulsa ([anoMesDaCompraNoCartao]); sem cartão (débito/receita), o mês da própria data.
 */
fun anoMesDaCobrancaRecorrente(data: LocalDate, diaFechamento: Int?): String =
    if (diaFechamento != null) anoMesDaCompraNoCartao(data, diaFechamento)
    else YearMonth.from(data).format(FORMATO_ANO_MES)

/**
 * Cobranças de recorrente no cartão, de [hoje] em diante, cujo `anoMes` não bate com o fechamento atual do
 * cartão; devolve cópias já corrigidas. Fica de fora quem tem a fatura atual ou a nova já paga, e quem é de
 * cartão que não está em [diaFechamentoPorCartao]. Idempotente: aplicar o resultado e rodar de novo dá vazio.
 */
fun recorrentesNoCartaoComAnoMesErrado(
    transacoes: List<Transacao>,
    diaFechamentoPorCartao: Map<Long, Int>,
    pagas: Set<FaturaChave>,
    hoje: LocalDate
): List<Transacao> = transacoes.mapNotNull { t ->
    val cartaoId = t.cartaoId ?: return@mapNotNull null
    if (t.recorrenteId == null || t.data.isBefore(hoje)) return@mapNotNull null
    val fechamento = diaFechamentoPorCartao[cartaoId] ?: return@mapNotNull null
    val novo = anoMesDaCobrancaRecorrente(t.data, fechamento)
    val atual = t.anoMes
    when {
        novo == atual -> null
        atual != null && FaturaChave(cartaoId, atual) in pagas -> null
        FaturaChave(cartaoId, novo) in pagas -> null
        else -> t.copy(anoMes = novo)
    }
}

data class FaturaAVencer(
    val cartao: CartaoEntity,
    val anoMes: String,
    val vencimento: LocalDate,
    val total: Double
)

/**
 * Faturas em aberto, com valor, cujo vencimento está nos próximos [diasAntes] dias (ou é hoje).
 * Avalia cada cartão separadamente.
 */
fun faturasProximasDoVencimento(
    cartoes: List<CartaoEntity>,
    transacoes: List<Transacao>,
    hoje: LocalDate = LocalDate.now(),
    diasAntes: Long = 2
): List<FaturaAVencer> {
    if (cartoes.isEmpty()) return emptyList()
    val pagas = conjuntoFaturasPagas(transacoes)
    val totais = HashMap<FaturaChave, Long>()
    for (t in transacoes) {
        if (t.formaPagamento != FormaPagamento.CARTAO_CREDITO) continue
        val chave = t.chaveFatura() ?: continue
        totais[chave] = (totais[chave] ?: 0L) + t.valorCentavos
    }

    val mesAtual = YearMonth.from(hoje)
    val resultado = mutableListOf<FaturaAVencer>()
    for (cartao in cartoes) {
        for (ym in listOf(mesAtual.minusMonths(1), mesAtual, mesAtual.plusMonths(1))) {
            val anoMes = ym.format(FORMATO_ANO_MES)
            val chave = FaturaChave(cartao.id, anoMes)
            if (chave in pagas) continue
            val total = totais[chave] ?: 0L
            if (total <= 0L) continue
            val vencimento = calcularVencimentoFatura(ym, cartao.diaVencimento, cartao.diaFechamento)
            if (hoje in vencimento.minusDays(diasAntes)..vencimento) {
                resultado.add(FaturaAVencer(cartao, anoMes, vencimento, centavosEmReais(total)))
            }
        }
    }
    return resultado
}
