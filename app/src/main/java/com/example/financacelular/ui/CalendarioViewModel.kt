package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AfazerEntity
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.ehPagamentoDeFatura
import com.example.financacelular.data.estaComFaturaPaga
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransacaoCalendario(
    val transacao: Transacao,
    val ehFaturaNaoPaga: Boolean
)

class CalendarioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val categorias = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val afazeres: StateFlow<List<AfazerEntity>> = repository.listarAfazeres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun inserirAfazer(data: LocalDate, titulo: String) {
        viewModelScope.launch {
            repository.salvarAfazer(AfazerEntity(data = data, titulo = titulo))
        }
    }

    fun alternarConcluido(afazer: AfazerEntity) {
        viewModelScope.launch {
            repository.atualizarAfazer(afazer.copy(concluido = !afazer.concluido))
        }
    }

    fun excluirAfazer(afazer: AfazerEntity) {
        viewModelScope.launch {
            repository.excluirAfazer(afazer)
        }
    }

    val transacoesCalendario: StateFlow<List<TransacaoCalendario>> = repository.listarTransacoes().map { todas ->
        val faturasPagas = conjuntoFaturasPagas(todas)
        todas.filter { t ->
            !t.ehPagamentoDeFatura()
        }.map { t ->
            // Fatura "não paga" = compra de cartão cuja fatura (daquele cartão, naquele mês) ainda não foi paga
            val naoPaga = t.formaPagamento == FormaPagamento.CARTAO_CREDITO &&
                    t.anoMes != null &&
                    !t.estaComFaturaPaga(faturasPagas)

            TransacaoCalendario(transacao = t, ehFaturaNaoPaga = naoPaga)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}