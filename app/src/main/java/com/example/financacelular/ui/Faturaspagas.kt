package com.example.financacelular.ui

import com.example.financacelular.data.Transacao

/**
 * Conjunto com os anoMes ("yyyy-MM") cuja fatura do cartão já foi paga.
 * Calculado uma única vez por emissão do banco, para as telas consultarem
 * com `anoMes in conjunto` (O(1)) em vez de varrer a lista inteira a cada item.
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