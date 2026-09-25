package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ItemOrcamento(
    val categoria: Categoria,
    val limite: Double?,
    val gasto: Double
)

class OrcamentoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))
    private val anoMesAtual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    val itens: StateFlow<List<ItemOrcamento>> = combine(
        repository.listarCategoriasPorTipo(TipoTransacao.DESPESA),
        repository.listarOrcamentosDoMes(anoMesAtual),
        repository.gastoPorCategoriaNoMes(anoMesAtual)
    ) { categorias, orcamentos, gastos ->
        categorias.map { categoria ->
            ItemOrcamento(
                categoria = categoria,
                limite = orcamentos.find { it.categoriaId == categoria.id }?.valorLimite,
                gasto = gastos.find { it.categoriaId == categoria.id }?.total ?: 0.0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun definirLimite(categoria: Categoria, valor: Double) {
        viewModelScope.launch {
            repository.definirOrcamento(
                Orcamento(categoriaId = categoria.id, anoMes = anoMesAtual, valorLimite = valor)
            )
        }
    }
}
