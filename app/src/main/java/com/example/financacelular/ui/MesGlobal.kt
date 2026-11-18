package com.example.financacelular.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.YearMonth

/** Mês escolhido no cabeçalho, igual em todas as telas (Início, Análise, Agenda, Cartão, Orçamento). */
object MesGlobal {
    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes

    fun definir(mes: YearMonth) {
        _mes.value = mes
    }

    fun anterior() {
        _mes.value = _mes.value.minusMonths(1)
    }

    fun seguinte() {
        _mes.value = _mes.value.plusMonths(1)
    }
}
