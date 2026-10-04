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

    // ID do cartão atualmente selecionado no carrossel (null até o carrossel selecionar o primeiro)
    private val _cartaoSelecionadoId = MutableStateFlow<Long?>(null)
    val cartaoSelecionadoId: StateFlow<Long?> = _cartaoSelecionadoId

    fun selecionarCartao(id: Long) {
        _cartaoSelecionadoId.value = id
    }

    // True quando o carrossel está na página "Adicionar cartão" (não há fatura para pagar)
    private val _naPaginaAdicionar = MutableStateFlow(false)
    val naPaginaAdicionar: StateFlow<Boolean> = _naPaginaAdicionar

    fun definirNaPaginaAdicionar(valor: Boolean) {
        _naPaginaAdicionar.value = valor
    }

    // Pedido vindo da barra inferior (botão "Adicionar cartão") para a tela abrir o formulário
    private val _pedidoAdicionarCartao = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pedidoAdicionarCartao: SharedFlow<Unit> = _pedidoAdicionarCartao

    fun solicitarAdicionarCartao() {
        _pedidoAdicionarCartao.tryEmit(Unit)
    }

    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    @OptIn(ExperimentalCoroutinesApi::class)
    val transacoesFatura: StateFlow<List<Transacao>> = combine(_cartaoSelecionadoId, _mesSelecionado) { cartaoId, ym ->
        cartaoId to ym
    }.flatMapLatest { (cartaoId, ym) ->
        if (cartaoId == null) {
            flowOf(emptyList())
        } else {
            val anoMesStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            repository.listarTransacoesFatura(cartaoId, anoMesStr)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val faturaPaga: StateFlow<Boolean> = combine(_cartaoSelecionadoId, _mesSelecionado) { cartaoId, ym ->
        cartaoId to ym
    }.flatMapLatest { (cartaoId, ym) ->
        if (cartaoId == null) {
            flowOf(false)
        } else {
            val anoMesStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            repository.verificarFaturaPagaCartao(cartaoId, anoMesStr)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Habilita o botão "Pagar/Cancelar fatura": só num cartão real e com fatura paga ou com valor a pagar
    val podePagarFatura: StateFlow<Boolean> = combine(
        _naPaginaAdicionar, _cartaoSelecionadoId, transacoesFatura, faturaPaga
    ) { naPaginaAdicionar, cartaoId, transacoes, paga ->
        !naPaginaAdicionar && cartaoId != null && (paga || transacoes.sumOf { it.valor } > 0.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)

    fun mesAnterior() {
        _mesSelecionado.value = _mesSelecionado.value.minusMonths(1)
    }

    fun mesSeguinte() {
        _mesSelecionado.value = _mesSelecionado.value.plusMonths(1)
    }

    fun pagarFatura(onSucesso: () -> Unit) {
        viewModelScope.launch {
            if (_naPaginaAdicionar.value) return@launch
            val anoMesStr = _mesSelecionado.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val valor = transacoesFatura.value.sumOf { it.valor }
            val cartaoId = _cartaoSelecionadoId.value ?: return@launch
            if (valor > 0.0) {
                repository.pagarFaturaCartao(cartaoId, anoMesStr, valor)
                onSucesso()
            }
        }
    }

    fun cancelarPagamentoFatura(onSucesso: () -> Unit) {
        viewModelScope.launch {
            if (_naPaginaAdicionar.value) return@launch
            val anoMesStr = _mesSelecionado.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val cartaoId = _cartaoSelecionadoId.value ?: return@launch
            repository.cancelarPagamentoFaturaCartao(cartaoId, anoMesStr)
            onSucesso()
        }
    }

    fun excluirCartao(cartao: CartaoEntity, onSucesso: () -> Unit) {
        viewModelScope.launch {
            repository.excluirCartao(cartao)
            if (_cartaoSelecionadoId.value == cartao.id) _cartaoSelecionadoId.value = null
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