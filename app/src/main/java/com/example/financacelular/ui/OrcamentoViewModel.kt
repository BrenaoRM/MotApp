package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.GastoCategoria
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ItemOrcamento(
    val categoria: Categoria,
    val limite: Double?,
    // true quando o valor exibido veio de um mês anterior (não foi definido explicitamente neste mês)
    val limiteHerdado: Boolean,
    val gasto: Double
)

class OrcamentoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))
    private val formatoAnoMes = DateTimeFormatter.ofPattern("yyyy-MM")

    private val _mesSelecionado = MutableStateFlow(YearMonth.now())
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    private val anoMesTexto: Flow<String> = _mesSelecionado.map { it.format(formatoAnoMes) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val historicoOrcamentos: Flow<List<Orcamento>> =
        anoMesTexto.flatMapLatest { repository.listarHistoricoOrcamentos(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val gastosDoMes: Flow<List<GastoCategoria>> =
        anoMesTexto.flatMapLatest { repository.gastoPorCategoriaNoMes(it) }

    val itens: StateFlow<List<ItemOrcamento>> = combine(
        repository.listarCategoriasPorTipo(TipoTransacao.DESPESA),
        historicoOrcamentos,
        gastosDoMes,
        anoMesTexto
    ) { categorias, historico, gastos, anoMesAtual ->
        categorias.map { categoria ->
            // historico já vem ordenado do mês mais recente pro mais antigo e sem valores zerados,
            // então o primeiro que bater com a categoria é o orçamento "efetivo" pra esse mês
            // (definido nele mesmo, ou herdado do último mês anterior que teve um valor)
            val entradaEfetiva = historico.firstOrNull { it.categoriaId == categoria.id }
            ItemOrcamento(
                categoria = categoria,
                limite = entradaEfetiva?.valorLimite,
                limiteHerdado = entradaEfetiva != null && entradaEfetiva.anoMes != anoMesAtual,
                gasto = gastos.find { it.categoriaId == categoria.id }?.total ?: 0.0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun mesAnterior() {
        _mesSelecionado.value = _mesSelecionado.value.minusMonths(1)
    }

    fun mesSeguinte() {
        _mesSelecionado.value = _mesSelecionado.value.plusMonths(1)
    }

    fun definirLimite(categoria: Categoria, valor: Double) {
        viewModelScope.launch {
            val anoMesAtual = _mesSelecionado.value.format(formatoAnoMes)
            repository.definirOrcamento(
                Orcamento(categoriaId = categoria.id, anoMes = anoMesAtual, valorLimite = valor)
            )
        }
    }
}