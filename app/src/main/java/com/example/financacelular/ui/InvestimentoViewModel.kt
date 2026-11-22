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

    /** null = carregando. */
    val investimentos: StateFlow<List<InvestimentoEntity>?> = repository.listarInvestimentos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Agrupa os investimentos pelo nome somando os valores e calculando % da carteira
    val investimentosAgrupados: StateFlow<List<InvestimentoAgrupado>?> = investimentos.map { lista ->
        if (lista == null) return@map null
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            for (acao in planejarResgate(aportes, valorResgate)) {
                when (acao) {
                    is AcaoResgate.Apagar -> repository.deletarInvestimento(acao.aporte)
                    is AcaoResgate.Atualizar -> {
                        val aporte = acao.aporte
                        val anoMesStr = String.format(Locale.ROOT, "%04d-%02d", aporte.data.year, aporte.data.monthValue)
                        repository.atualizarInvestimento(aporte.copy(valorInvestido = acao.novoValor, anoMes = anoMesStr))
                    }
                }
            }
        }
    }
}

internal sealed class AcaoResgate {
    data class Apagar(val aporte: InvestimentoEntity) : AcaoResgate()
    data class Atualizar(val aporte: InvestimentoEntity, val novoValor: Double) : AcaoResgate()
}

/** Valor em centavos inteiros, para evitar erro de ponto flutuante (0,1 + 0,7 != 0,8). */
internal fun emCentavos(valor: Double): Long = Math.round(valor * 100)

/** Consome o resgate dos aportes do mais recente para o mais antigo, trabalhando em centavos. */
internal fun planejarResgate(aportes: List<InvestimentoEntity>, valorResgate: Double): List<AcaoResgate> {
    var restante = emCentavos(valorResgate)
    val acoes = mutableListOf<AcaoResgate>()
    for (aporte in aportes.sortedByDescending { it.data }) {
        if (restante <= 0L) break
        val centavosAporte = emCentavos(aporte.valorInvestido)
        if (restante >= centavosAporte) {
            restante -= centavosAporte
            acoes += AcaoResgate.Apagar(aporte)
        } else {
            acoes += AcaoResgate.Atualizar(aporte, (centavosAporte - restante) / 100.0)
            restante = 0L
        }
    }
    return acoes
}