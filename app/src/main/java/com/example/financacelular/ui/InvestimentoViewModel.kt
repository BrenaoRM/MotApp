package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.InvestimentoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class InvestimentoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val investimentos: StateFlow<List<InvestimentoEntity>> = repository.listarInvestimentos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Modificado para receber a data escolhida
    fun inserirAporte(nome: String, categoria: String, valor: Double, dataAporte: LocalDate) {
        viewModelScope.launch {
            val anoMesStr = String.format("%04d-%02d", dataAporte.year, dataAporte.monthValue)

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

    // Modificado para atualizar o mês caso o utilizador edite a data do aporte
    fun atualizarAporte(investimento: InvestimentoEntity) {
        viewModelScope.launch {
            val anoMesStr = String.format("%04d-%02d", investimento.data.year, investimento.data.monthValue)
            repository.atualizarInvestimento(investimento.copy(anoMes = anoMesStr))
        }
    }

    fun deletarAporte(investimento: InvestimentoEntity) {
        viewModelScope.launch {
            repository.deletarInvestimento(investimento)
        }
    }
}