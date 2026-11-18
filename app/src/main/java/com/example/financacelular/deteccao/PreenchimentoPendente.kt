package com.example.financacelular.deteccao

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Transação detectada esperando a pessoa abrir Nova Transação. A MainActivity define, a tela consome. */
object PreenchimentoPendente {
    private val _atual = MutableStateFlow<TransacaoDetectada?>(null)
    val atual: StateFlow<TransacaoDetectada?> = _atual

    fun definir(detectada: TransacaoDetectada) {
        _atual.value = detectada
    }

    fun consumir() {
        _atual.value = null
    }
}
