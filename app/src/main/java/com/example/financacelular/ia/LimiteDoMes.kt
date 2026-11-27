package com.example.financacelular.ia

import com.example.financacelular.data.Orcamento
import java.time.YearMonth

/**
 * Limite vigente da categoria no mês, como `OrcamentoViewModel.itens`: vale o orçamento mais recente com
 * `anoMes <= mes` (o limite é herdado dos meses seguintes) e limite 0 significa "sem orçamento".
 */
internal fun limiteDoMes(orcamentos: List<Orcamento>, categoriaId: Long, mes: YearMonth): Long? =
    orcamentos.filter { it.categoriaId == categoriaId && it.anoMes <= mes.toString() }
        .maxWithOrNull(compareBy({ it.anoMes }, { it.id }))
        ?.valorLimiteCentavos?.takeIf { it > 0 }
