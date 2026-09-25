package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.TotalMensal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ItemGastoCategoria(val nomeCategoria: String, val total: Double)
data class TopDespesaItem(val descricao: String, val nomeCategoria: String, val valor: Double, val data: String)
data class TotalMensalInvestimento(val anoMes: String, val total: Double)

class AnaliseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    private val anoMesTexto: Flow<String> = _mesSelecionado.map {
        it.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transacoesValidasDoMes = combine(
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
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val despesasPorCategoria: StateFlow<List<ItemGastoCategoria>> = combine(transacoesValidasDoMes, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }
            .groupBy { it.categoriaId }
            .map { (catId, lista) ->
                val nome = categorias.find { it.id == catId }?.nome ?: "Sem categoria"
                ItemGastoCategoria(nome, lista.sumOf { it.valor })
            }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val receitasPorCategoria: StateFlow<List<ItemGastoCategoria>> = combine(transacoesValidasDoMes, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.RECEITA }
            .groupBy { it.categoriaId }
            .map { (catId, lista) ->
                val nome = categorias.find { it.id == catId }?.nome ?: "Sem categoria"
                ItemGastoCategoria(nome, lista.sumOf { it.valor })
            }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDespesasMes: StateFlow<Double> = transacoesValidasDoMes.map { transacoes ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }.sumOf { it.valor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalReceitasMes: StateFlow<Double> = transacoesValidasDoMes.map { transacoes ->
        transacoes.filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Fluxo de caixa calculado diretamente a partir das transações passadas e faturas pagas dos últimos 6 meses
    val evolucaoMensal: StateFlow<List<TotalMensal>> = repository.listarTransacoes()
        .map { todas ->
            val hoje = LocalDate.now()
            val meses = (0..5).map { hoje.minusMonths(it.toLong()) }.reversed()

            meses.map { ym ->
                val anoMes = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val faturaPaga = todas.any { it.cartaoId == null && it.anoMes == anoMes && it.descricao == "Pagamento de Fatura - $anoMes" }

                val transacoesDoPeriodo = todas.filter { t ->
                    val ehDoMes = t.data.toString().startsWith(anoMes)
                    if (!ehDoMes) return@filter false

                    val ehPagamentoFatura = t.descricao?.startsWith("Pagamento de Fatura") == true
                    if (ehPagamentoFatura) return@filter false

                    val ehFuturo = t.data.isAfter(hoje)

                    if (t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null) {
                        faturaPaga
                    } else {
                        // Para meses anteriores, exibe tudo. Para o mês atual, apenas até hoje.
                        if (ym.year == hoje.year && ym.month == hoje.month) !ehFuturo else true
                    }
                }

                val receitas = transacoesDoPeriodo.filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valor }
                val despesas = transacoesDoPeriodo.filter { it.tipo == TipoTransacao.DESPESA }.sumOf { it.valor }

                TotalMensal(anoMes = anoMes, totalReceitas = receitas, totalDespesas = despesas)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topDespesas: StateFlow<List<TopDespesaItem>> = combine(transacoesValidasDoMes, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }
            .sortedByDescending { it.valor }
            .take(5)
            .map { t ->
                val cat = categorias.find { it.id == t.categoriaId }?.nome ?: "Geral"
                TopDespesaItem(t.descricao ?: cat, cat, t.valor, t.data.toString())
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val evolucaoInvestimentos: StateFlow<List<TotalMensalInvestimento>> = repository.listarInvestimentos()
        .map { lista ->
            lista.groupBy { it.anoMes }
                .map { (mes, invs) -> TotalMensalInvestimento(mes, invs.sumOf { it.valorInvestido }) }
                .sortedBy { it.anoMes }
                .takeLast(6)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun mesAnterior() {
        _mesSelecionado.value = _mesSelecionado.value.minusMonths(1)
    }

    fun mesSeguinte() {
        _mesSelecionado.value = _mesSelecionado.value.plusMonths(1)
    }
}