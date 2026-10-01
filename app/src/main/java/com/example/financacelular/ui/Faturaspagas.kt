package com.example.financacelular.ui

import com.example.financacelular.data.Transacao

/**
 * Conjunto com os anoMes ("yyyy-MM") cuja fatura do cartão foi paga.
 * Calculado em O(N) para consultas O(1) com `anoMes in conjunto`.
 */
internal fun conjuntoFaturasPagas(todas: List<Transacao>): Set<String> {
    val resultado = HashSet<String>()
    for (t in todas) {
        val anoMes = t.anoMes ?: continue
        if (t.cartaoId == null && t.descricao == "Pagamento de Fatura - $anoMes") {
            resultado.add(anoMes)
        }
    }
    return resultado
}