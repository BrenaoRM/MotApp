package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.Meta
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ResumoMovimentacao(
    val titulo: String,
    val categoriaId: Long,
    val tipo: TipoTransacao,
    val total: Double,
    val transacoes: List<Transacao>
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    private val anoMesTexto: Flow<String> = _mesSelecionado.map {
        it.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }

    private var alertaJaExibidoNestaSessao = false
    private val _triggerVerificacao = MutableStateFlow(0)

    val alertaFaturaPendente: StateFlow<Boolean> = combine(
        repository.obterCartao(1L),
        repository.verificarFaturaPaga(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))),
        _triggerVerificacao
    ) { cartao, faturaPaga, _ ->
        if (faturaPaga || cartao == null) return@combine false
        if (alertaJaExibidoNestaSessao) return@combine false

        val hoje = LocalDate.now()
        val diaVencimentoReal = minOf(cartao.diaVencimento, YearMonth.now().lengthOfMonth())
        val dataVencimento = YearMonth.now().atDay(diaVencimentoReal)
        val dataInicioAlerta = dataVencimento.minusDays(2)

        hoje in dataInicioAlerta..dataVencimento
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun marcarAlertaComoExibido() {
        alertaJaExibidoNestaSessao = true
        _triggerVerificacao.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transacoesFiltradasDoMes: Flow<List<Transacao>> = combine(
        anoMesTexto,
        repository.listarTransacoes(),
        repository.listarCategorias()
    ) { anoMes, todas, categorias ->
        val hoje = LocalDate.now()
        val faturaPaga = todas.any { it.cartaoId == null && it.anoMes == anoMes && it.descricao == "Pagamento de Fatura - $anoMes" }
        val categoriaFaturaId = categorias.find { it.nome.equals("Fatura", ignoreCase = true) }?.id ?: 1L

        todas.filter { t ->
            val ehDoMes = t.data.toString().startsWith(anoMes)
            if (!ehDoMes) return@filter false

            val ehPagamentoFatura = t.descricao?.startsWith("Pagamento de Fatura") == true
            if (ehPagamentoFatura) return@filter false

            val ehFuturo = t.data.isAfter(hoje)

            if (t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null) {
                faturaPaga
            } else {
                !ehFuturo
            }
        }.map { t ->
            if (t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null) {
                t.copy(categoriaId = categoriaFaturaId)
            } else {
                t
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalDespesas: StateFlow<Double> = transacoesFiltradasDoMes
        .map { lista -> lista.filter { it.tipo == TipoTransacao.DESPESA }.sumOf { it.valor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalReceitas: StateFlow<Double> = transacoesFiltradasDoMes
        .map { lista -> lista.filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalInvestidoDoMes: StateFlow<Double> = combine(
        anoMesTexto,
        repository.listarInvestimentos()
    ) { anoMes, investimentos ->
        investimentos.filter { it.anoMes == anoMes }.sumOf { it.valorInvestido }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val metas: StateFlow<List<Meta>> = repository.listarMetas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<com.example.financacelular.data.Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val resumoDoMes: StateFlow<List<ResumoMovimentacao>> = combine(
        transacoesFiltradasDoMes,
        categorias
    ) { transacoesMes, listaCategorias ->
        transacoesMes.groupBy { t ->
            t.categoriaId to t.tipo
        }.map { (chave, lista) ->
            val categoriaId = chave.first
            val tipo = chave.second
            val nomeCategoria = listaCategorias.find { c -> c.id == categoriaId }?.nome ?: "Sem categoria"
            ResumoMovimentacao(
                titulo = nomeCategoria,
                categoriaId = categoriaId,
                tipo = tipo,
                total = lista.sumOf { it.valor },
                transacoes = lista
            )
        }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun mesAnterior() {
        _mesSelecionado.value = _mesSelecionado.value.minusMonths(1)
    }

    fun mesSeguinte() {
        _mesSelecionado.value = _mesSelecionado.value.plusMonths(1)
    }
}