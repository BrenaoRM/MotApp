package com.example.financacelular.data

import java.time.LocalDate

/** Em que pé está um lançamento em relação a [hoje]: já conta como gasto/ganho ou ainda não. */
enum class SituacaoLancamento {
    /** Débito/dinheiro com data até hoje, ou compra no cartão cuja fatura já foi paga. */
    REALIZADO,

    /** Compra no cartão cuja fatura ainda não foi paga (inclui parcelas com data futura). */
    NA_FATURA_EM_ABERTO,

    /** Débito/dinheiro (ou crédito sem cartão) com data depois de hoje. */
    FUTURO,

    /** Pagamento de fatura: nunca conta como gasto (o valor já está nas compras do cartão). */
    PAGAMENTO_DE_FATURA
}

/** True para compra no crédito vinculada a um cartão (a que entra numa fatura). */
fun Transacao.ehCompraNoCartao(): Boolean =
    formaPagamento == FormaPagamento.CARTAO_CREDITO && cartaoId != null

/**
 * Situação deste lançamento. Única fonte da regra "realizado / em aberto / futuro":
 * compra no cartão depende só da fatura estar paga; o resto depende só da data.
 */
fun Transacao.situacao(faturasPagas: Set<FaturaChave>, hoje: LocalDate): SituacaoLancamento = when {
    ehPagamentoDeFatura() -> SituacaoLancamento.PAGAMENTO_DE_FATURA
    ehCompraNoCartao() ->
        if (estaComFaturaPaga(faturasPagas)) SituacaoLancamento.REALIZADO
        else SituacaoLancamento.NA_FATURA_EM_ABERTO
    data.isAfter(hoje) -> SituacaoLancamento.FUTURO
    else -> SituacaoLancamento.REALIZADO
}

/** True se o lançamento já conta nos totais (ver [situacao]). */
fun Transacao.estaRealizada(faturasPagas: Set<FaturaChave>, hoje: LocalDate): Boolean =
    situacao(faturasPagas, hoje) == SituacaoLancamento.REALIZADO

/** True se o lançamento ainda vai acontecer: fatura em aberto ou data futura (pagamento de fatura fica de fora). */
fun Transacao.estaPendente(faturasPagas: Set<FaturaChave>, hoje: LocalDate): Boolean =
    when (situacao(faturasPagas, hoje)) {
        SituacaoLancamento.NA_FATURA_EM_ABERTO, SituacaoLancamento.FUTURO -> true
        else -> false
    }

/** Id da categoria chamada "Fatura" (sem diferenciar maiúsculas); null se não existir. */
fun idDaCategoriaFatura(categorias: List<Categoria>): Long? =
    categorias.find { it.nome.equals("Fatura", ignoreCase = true) }?.id

/**
 * Compra no cartão passa a aparecer na categoria [categoriaFaturaId]; os demais lançamentos ficam iguais.
 * Sem categoria "Fatura" (null), a compra mantém a própria categoria.
 */
fun Transacao.comCategoriaDaFatura(categoriaFaturaId: Long?): Transacao =
    if (categoriaFaturaId != null && ehCompraNoCartao()) copy(categoriaId = categoriaFaturaId) else this
