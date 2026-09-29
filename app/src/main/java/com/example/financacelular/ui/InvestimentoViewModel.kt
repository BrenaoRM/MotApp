package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.InvestimentoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class InvestimentoAgrupado(
    val nome: String,
    val categoria: String,
    val valorTotal: Double,
    val percentualDoTotal: Float,
    val aportes: List<InvestimentoEntity>
)

class InvestimentoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val investimentos: StateFlow<List<InvestimentoEntity>> = repository.listarInvestimentos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Agrupa os investimentos pelo nome somando os valores e calculando % da carteira
    val investimentosAgrupados: StateFlow<List<InvestimentoAgrupado>> = investimentos.map { lista ->
        val patrimonioTotal = lista.sumOf { it.valorInvestido }
        lista.groupBy { it.nome.trim().lowercase() }.map { (_, grupo) ->
            val primeiro = grupo.first()
            val totalAtivo = grupo.sumOf { it.valorInvestido }
            val percentual = if (patrimonioTotal > 0) (totalAtivo / patrimonioTotal).toFloat() else 0f
            InvestimentoAgrupado(
                nome = primeiro.nome,
                categoria = primeiro.categoria,
                valorTotal = totalAtivo,
                percentualDoTotal = percentual,
                aportes = grupo.sortedByDescending { it.data }
            )
        }.sortedByDescending { it.valorTotal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun inserirAporte(nome: String, categoria: String, valor: Double, dataAporte: LocalDate) {
        viewModelScope.launch {
            val anoMesStr = String.format(Locale.ROOT, "%04d-%02d", dataAporte.year, dataAporte.monthValue)
            repository.inserirInvestimento(
                InvestimentoEntity(
                    nome = nome,
                    categoria = categoria,
                    valorInvestido = valor,
                    data = dataAporte,
                    anoMes = anoMesStr
                )
            )
        }
    }

    fun deletarAporte(investimento: InvestimentoEntity) {
        viewModelScope.launch {
            repository.deletarInvestimento(investimento)
        }
    }

    // Desconta o valor solicitado consumindo dos aportes do mais recente para o mais antigo
    fun resgatarDoAtivo(aportes: List<InvestimentoEntity>, valorResgate: Double) {
        viewModelScope.launch {
            var restante = valorResgate
            val ordenados = aportes.sortedByDescending { it.data }
            for (aporte in ordenados) {
                if (restante <= 0.0) break
                if (aporte.valorInvestido <= restante) {
                    restante -= aporte.valorInvestido
                    repository.deletarInvestimento(aporte)
                } else {
                    val novoValor = aporte.valorInvestido - restante
                    restante = 0.0
                    val anoMesStr = String.format(Locale.ROOT,"%04d-%02d", aporte.data.year, aporte.data.monthValue)
                    repository.atualizarInvestimento(aporte.copy(valorInvestido = novoValor, anoMes = anoMesStr))
                }
            }
        }
    }
}