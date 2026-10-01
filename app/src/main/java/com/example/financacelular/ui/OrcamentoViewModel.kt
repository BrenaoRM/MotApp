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
            val entradasCategoria = historico.filter { it.categoriaId == categoria.id }
            val entradaMaisRecente = entradasCategoria.firstOrNull()

            val limiteEfetivo: Double?
            val limiteHerdado: Boolean

            if (entradaMaisRecente == null) {
                limiteEfetivo = null
                limiteHerdado = false
            } else if (entradaMaisRecente.anoMes == anoMesAtual) {
                limiteEfetivo = if (entradaMaisRecente.valorLimite > 0) entradaMaisRecente.valorLimite else null
                limiteHerdado = false
            } else {
                limiteEfetivo = if (entradaMaisRecente.valorLimite > 0) entradaMaisRecente.valorLimite else null
                limiteHerdado = limiteEfetivo != null
            }

            ItemOrcamento(
                categoria = categoria,
                limite = limiteEfetivo,
                limiteHerdado = limiteHerdado,
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
            val existente = repository.buscarOrcamentoDoMes(categoria.id, anoMesAtual)

            repository.definirOrcamento(
                Orcamento(
                    id = existente?.id ?: 0,
                    categoriaId = categoria.id,
                    anoMes = anoMesAtual,
                    valorLimite = valor
                )
            )
        }
    }
}