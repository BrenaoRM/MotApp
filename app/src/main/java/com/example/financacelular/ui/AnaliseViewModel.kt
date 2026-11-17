package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.ehPagamentoDeFatura
import com.example.financacelular.data.estaComFaturaPaga
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.TotalMensal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ItemGastoCategoria(val nomeCategoria: String, val total: Double, val icone: String? = null)
data class TopDespesaItem(val descricao: String, val nomeCategoria: String, val valor: Double, val data: String, val icone: String? = null)
data class TotalMensalInvestimento(val anoMes: String, val total: Double)

class AnaliseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    private val _periodo = MutableStateFlow(Periodo.mes(YearMonth.now()))
    val periodo: StateFlow<Periodo> = _periodo

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transacoesValidasDoPeriodo = combine(
        _periodo,
        repository.listarTransacoes(),
        repository.listarCategorias()
    ) { periodo, todas, categorias ->
        val hoje = LocalDate.now()
        val primeiroAnoMes = YearMonth.from(periodo.inicio).toString()
        val ultimoAnoMes = YearMonth.from(periodo.fim).toString()
        val faturasPagas = conjuntoFaturasPagas(todas)
        val categoriaFaturaId = categorias.find { it.nome.equals("Fatura", ignoreCase = true) }?.id ?: 1L

        todas.filter { t ->
            // Meses fechados seguem o mês de competência (fatura do cartão); intervalo livre segue a data
            val ehDoPeriodo = if (periodo.mesesFechados) {
                val anoMesTransacao = t.anoMes ?: t.data.toString().take(7)
                anoMesTransacao >= primeiroAnoMes && anoMesTransacao <= ultimoAnoMes
            } else {
                !t.data.isBefore(periodo.inicio) && !t.data.isAfter(periodo.fim)
            }
            if (!ehDoPeriodo) return@filter false
            if (t.ehPagamentoDeFatura()) return@filter false
            val ehFuturo = t.data.isAfter(hoje)

            if (t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null) {
                t.estaComFaturaPaga(faturasPagas)
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
    }.flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val despesasPorCategoria: StateFlow<List<ItemGastoCategoria>> = combine(transacoesValidasDoPeriodo, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }
            .groupBy { it.categoriaId }
            .map { (catId, lista) ->
                val cat = categorias.find { it.id == catId }
                ItemGastoCategoria(cat?.nome ?: "Sem categoria", lista.sumOf { it.valor }, cat?.icone)
            }.sortedByDescending { it.total }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val receitasPorCategoria: StateFlow<List<ItemGastoCategoria>> = combine(transacoesValidasDoPeriodo, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.RECEITA }
            .groupBy { it.categoriaId }
            .map { (catId, lista) ->
                val cat = categorias.find { it.id == catId }
                ItemGastoCategoria(cat?.nome ?: "Sem categoria", lista.sumOf { it.valor }, cat?.icone)
            }.sortedByDescending { it.total }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDespesasPeriodo: StateFlow<Double> = transacoesValidasDoPeriodo.map { transacoes ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }.sumOf { it.valor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalReceitasPeriodo: StateFlow<Double> = transacoesValidasDoPeriodo.map { transacoes ->
        transacoes.filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val evolucaoMensal: StateFlow<List<TotalMensal>> = repository.listarTransacoes()
        .map { todas ->
            val hoje = LocalDate.now()
            val faturasPagas = conjuntoFaturasPagas(todas)
            val meses = (0..5).map { hoje.minusMonths(it.toLong()) }.reversed()

            meses.map { ym ->
                val anoMes = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val transacoesDoPeriodo = todas.filter { t ->
                    val anoMesTransacao = t.anoMes ?: t.data.toString().take(7)
                    val ehDoMes = anoMesTransacao == anoMes
                    if (!ehDoMes) return@filter false
                    if (t.ehPagamentoDeFatura()) return@filter false
                    val ehFuturo = t.data.isAfter(hoje)

                    if (t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null) {
                        t.estaComFaturaPaga(faturasPagas)
                    } else {
                        if (ym.year == hoje.year && ym.month == hoje.month) !ehFuturo else true
                    }
                }
                val receitas = transacoesDoPeriodo.filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valor }
                val despesas = transacoesDoPeriodo.filter { it.tipo == TipoTransacao.DESPESA }.sumOf { it.valor }
                TotalMensal(anoMes = anoMes, totalReceitas = receitas, totalDespesas = despesas)
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topDespesas: StateFlow<List<TopDespesaItem>> = combine(transacoesValidasDoPeriodo, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }
            .sortedByDescending { it.valor }
            .take(5)
            .map { t ->
                val categoriaDaDespesa = categorias.find { it.id == t.categoriaId }
                val cat = categoriaDaDespesa?.nome ?: "Geral"
                TopDespesaItem(t.descricao ?: cat, cat, t.valor, t.data.toString(), categoriaDaDespesa?.icone)
            }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val evolucaoInvestimentos: StateFlow<List<TotalMensalInvestimento>> = repository.listarInvestimentos()
        .map { lista ->
            lista.groupBy { it.anoMes }
                .map { (mes, invs) -> TotalMensalInvestimento(mes, invs.sumOf { it.valorInvestido }) }
                .sortedBy { it.anoMes }
                .takeLast(6)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Acumulado por dia, do início do período até hoje (ou o fim); o último valor é igual ao total exibido. */
    data class SerieDoPeriodo(
        val entradas: List<Float> = emptyList(),
        val saidas: List<Float> = emptyList()
    )

    val serieDoPeriodo: StateFlow<SerieDoPeriodo> = combine(_periodo, transacoesValidasDoPeriodo) { periodo, lista ->
        val ultimoDia = minOf(periodo.fim, LocalDate.now())
        if (ultimoDia.isBefore(periodo.inicio)) return@combine SerieDoPeriodo()
        val quantidade = (ChronoUnit.DAYS.between(periodo.inicio, ultimoDia) + 1).toInt()

        fun acumular(tipo: TipoTransacao): List<Float> {
            val porDia = DoubleArray(quantidade)
            lista.filter { it.tipo == tipo }.forEach { t ->
                // Compra no crédito pode ter data fora do período da fatura: limita ao intervalo
                val indice = ChronoUnit.DAYS.between(periodo.inicio, t.data).toInt().coerceIn(0, quantidade - 1)
                porDia[indice] += t.valor
            }
            var soma = 0.0
            return porDia.map { soma += it; soma.toFloat() }
        }

        SerieDoPeriodo(acumular(TipoTransacao.RECEITA), acumular(TipoTransacao.DESPESA))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SerieDoPeriodo())

    fun periodoAnterior() {
        _periodo.value = _periodo.value.deslocar(-1)
    }

    fun periodoSeguinte() {
        _periodo.value = _periodo.value.deslocar(1)
    }

    fun definirPeriodo(novo: Periodo) {
        _periodo.value = novo
    }
}