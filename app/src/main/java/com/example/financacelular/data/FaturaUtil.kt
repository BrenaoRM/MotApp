package com.example.financacelular.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Cartão ao qual pertencem os dados criados antes do suporte a vários cartões
 * (pagamentos antigos "Pagamento de Fatura - yyyy-MM", sem id do cartão).
 */
const val ID_CARTAO_PADRAO = 1L

private const val PREFIXO_PAGAMENTO = "Pagamento de Fatura"
private val REGEX_PAGAMENTO = Regex("""^Pagamento de Fatura - (\d{4}-\d{2})(?: - (\d+))?$""")
private val FORMATO_ANO_MES = DateTimeFormatter.ofPattern("yyyy-MM")

/** Identifica uma fatura: um cartão em um mês ("yyyy-MM"). */
data class FaturaChave(val cartaoId: Long, val anoMes: String)

/** Descrição gravada na transação de pagamento (o cartão padrão mantém o formato antigo). */
fun descricaoPagamentoFatura(cartaoId: Long, anoMes: String): String =
    if (cartaoId == ID_CARTAO_PADRAO) "$PREFIXO_PAGAMENTO - $anoMes"
    else "$PREFIXO_PAGAMENTO - $anoMes - $cartaoId"

/** True para qualquer transação de pagamento de fatura (de qualquer cartão). */
fun Transacao.ehPagamentoDeFatura(): Boolean = descricao?.startsWith(PREFIXO_PAGAMENTO) == true

/** Se esta transação é um pagamento de fatura, devolve qual fatura (cartão + mês) ela quita. */
fun Transacao.faturaQuitadaPorEstePagamento(): FaturaChave? {
    if (cartaoId != null) return null
    val match = REGEX_PAGAMENTO.matchEntire(descricao ?: return null) ?: return null
    val cartao = match.groupValues[2].toLongOrNull() ?: ID_CARTAO_PADRAO
    return FaturaChave(cartao, match.groupValues[1])
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
    val totais = HashMap<FaturaChave, Double>()
    for (t in transacoes) {
        if (t.formaPagamento != FormaPagamento.CARTAO_CREDITO) continue
        val chave = t.chaveFatura() ?: continue
        totais[chave] = (totais[chave] ?: 0.0) + t.valor
    }

    val mesAtual = YearMonth.from(hoje)
    val resultado = mutableListOf<FaturaAVencer>()
    for (cartao in cartoes) {
        for (ym in listOf(mesAtual.minusMonths(1), mesAtual, mesAtual.plusMonths(1))) {
            val anoMes = ym.format(FORMATO_ANO_MES)
            val chave = FaturaChave(cartao.id, anoMes)
            if (chave in pagas) continue
            val total = totais[chave] ?: 0.0
            if (total <= 0.0) continue
            val vencimento = calcularVencimentoFatura(ym, cartao.diaVencimento, cartao.diaFechamento)
            if (hoje in vencimento.minusDays(diasAntes)..vencimento) {
                resultado.add(FaturaAVencer(cartao, anoMes, vencimento, total))
            }
        }
    }
    return resultado
}
