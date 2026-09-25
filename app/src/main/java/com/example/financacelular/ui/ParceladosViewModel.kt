package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CompraParceladaAgrupada(
    val descricaoBase: String,
    val totalParcelas: Int,
    val valorParcela: Double,
    val parcelasPagas: Int,
    val parcelasRestantes: Int,
    val valorTotal: Double,
    val transacoesPendentes: List<Transacao>,
    val dataCompra: LocalDate
)

class ParceladosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val comprasAgrupadas: StateFlow<List<CompraParceladaAgrupada>> = repository.listarTransacoes().map { transacoes ->
        // Filtra apenas as transações parceladas (totalParcelas > 1)
        val transacoesParceladas = transacoes.filter { it.totalParcelas > 1 }
        
        // Descobre quais meses tiveram a fatura paga
        val faturasPagas = transacoes.filter { 
            it.cartaoId == null && it.descricao?.startsWith("Pagamento de Fatura") == true 
        }.mapNotNull { it.anoMes }.toSet()

        // Agrupa as parcelas usando Regex para remover o " (1/10)" do final do nome
        val agrupado = transacoesParceladas.groupBy { t ->
            t.descricao?.replace(Regex(" \\(\\d+/\\d+\\)$"), "") ?: "Compra Parcelada"
        }

        // Mapeia o grupo para a nossa classe de dados formatada
        agrupado.map { (descricaoBase, lista) ->
            val transacoesOrdenadas = lista.sortedBy { it.numeroParcela }
            val primeiraTransacao = transacoesOrdenadas.first()
            
            val totalParcelas = primeiraTransacao.totalParcelas
            val valorParcela = primeiraTransacao.valor
            val valorTotal = lista.sumOf { it.valor }

            val parcelasPagas = lista.count { it.anoMes in faturasPagas }
            val parcelasRestantes = lista.size - parcelasPagas
            
            // Separa as transações que ainda não caíram em uma fatura paga
            val transacoesPendentes = lista.filter { it.anoMes !in faturasPagas }

            CompraParceladaAgrupada(
                descricaoBase = descricaoBase,
                totalParcelas = totalParcelas,
                valorParcela = valorParcela,
                parcelasPagas = parcelasPagas,
                parcelasRestantes = parcelasRestantes,
                valorTotal = valorTotal,
                transacoesPendentes = transacoesPendentes,
                dataCompra = primeiraTransacao.data
            )
        }.sortedByDescending { it.dataCompra } // Ordena das mais recentes para as mais antigas
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exclui todas as parcelas futuras que ainda não foram pagas
    fun cancelarParcelasRestantes(transacoes: List<Transacao>) {
        viewModelScope.launch {
            transacoes.forEach {
                repository.excluirTransacao(it)
            }
        }
    }
}