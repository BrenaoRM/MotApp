package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CartaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    // Lista de todos os cartões cadastrados
    val listaCartoes: StateFlow<List<CartaoEntity>> = repository.listarTodosCartoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ID do cartão atualmente selecionado no carrossel
    private val _cartaoSelecionadoId = MutableStateFlow<Long>(1L)
    val cartaoSelecionadoId: StateFlow<Long> = _cartaoSelecionadoId

    fun selecionarCartao(id: Long) {
        _cartaoSelecionadoId.value = id
    }

    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    @OptIn(ExperimentalCoroutinesApi::class)
    val transacoesFatura: StateFlow<List<Transacao>> = combine(_cartaoSelecionadoId, _mesSelecionado) { cartaoId, ym ->
        cartaoId to ym
    }.flatMapLatest { (cartaoId, ym) ->
        val anoMesStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        repository.listarTransacoesFatura(cartaoId, anoMesStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val faturaPaga: StateFlow<Boolean> = combine(_cartaoSelecionadoId, _mesSelecionado) { cartaoId, ym ->
        cartaoId to ym
    }.flatMapLatest { (cartaoId, ym) ->
        val anoMesStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        repository.verificarFaturaPagaCartao(cartaoId, anoMesStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cartaoAtivo: StateFlow<CartaoEntity?> = _cartaoSelecionadoId.flatMapLatest { id ->
        repository.obterCartao(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            val cartaoId = _cartaoSelecionadoId.value
            if (valor > 0.0) {
                repository.pagarFaturaCartao(cartaoId, anoMesStr, valor)
                onSucesso()
            }
        }
    }

    fun cancelarPagamentoFatura(onSucesso: () -> Unit) {
        viewModelScope.launch {
            val anoMesStr = _mesSelecionado.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val cartaoId = _cartaoSelecionadoId.value
            repository.cancelarPagamentoFaturaCartao(cartaoId, anoMesStr)
            onSucesso()
        }
    }

    fun criarOuAtualizarCartao(cartaoId: Long?, nome: String, diaFechamento: Int, diaVencimento: Int, onSucesso: () -> Unit) {
        viewModelScope.launch {
            if (cartaoId == null) {
                val novo = CartaoEntity(nome = nome, diaFechamento = diaFechamento, diaVencimento = diaVencimento)
                repository.salvarCartao(novo)
            } else {
                val atual = repository.obterCartaoSync(cartaoId)
                if (atual != null) {
                    repository.salvarCartao(atual.copy(nome = nome, diaFechamento = diaFechamento, diaVencimento = diaVencimento))
                }
            }
            onSucesso()
        }
    }
}