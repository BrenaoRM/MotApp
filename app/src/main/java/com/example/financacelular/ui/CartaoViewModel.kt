package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CartaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    @OptIn(ExperimentalCoroutinesApi::class)
    val transacoesFatura: StateFlow<List<Transacao>> = _mesSelecionado.flatMapLatest { ym ->
        val anoMesStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        repository.listarTransacoesFatura(1L, anoMesStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val faturaPaga: StateFlow<Boolean> = _mesSelecionado.flatMapLatest { ym ->
        val anoMesStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        repository.verificarFaturaPaga(anoMesStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cartao: StateFlow<CartaoEntity?> = repository.obterCartao(1L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun mesAnterior() {
        _mesSelecionado.value = _mesSelecionado.value.minusMonths(1)
    }

    fun mesSeguinte() {
        _mesSelecionado.value = _mesSelecionado.value.plusMonths(1)
    }

    fun pagarFatura(onSucesso: () -> Unit) {
        viewModelScope.launch {
            val anoMesStr = _mesSelecionado.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val valor = transacoesFatura.value.sumOf { it.valor }
            if (valor > 0.0) {
                repository.pagarFatura(anoMesStr, valor)
                onSucesso()
            }
        }
    }

    fun cancelarPagamentoFatura(onSucesso: () -> Unit) {
        viewModelScope.launch {
            val anoMesStr = _mesSelecionado.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            repository.cancelarPagamentoFatura(anoMesStr)
            onSucesso()
        }
    }

    fun atualizarDatasCartao(diaFechamento: Int, diaVencimento: Int) {
        viewModelScope.launch {
            val cartaoAtual = repository.obterCartaoSync(1L)
            if (cartaoAtual != null) {
                repository.salvarCartao(
                    cartaoAtual.copy(diaFechamento = diaFechamento, diaVencimento = diaVencimento)
                )
            } else {
                repository.salvarCartao(
                    CartaoEntity(
                        id = 1L,
                        nome = "Cartão Principal",
                        diaFechamento = diaFechamento,
                        diaVencimento = diaVencimento
                    )
                )
            }
        }
    }
}