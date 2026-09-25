package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.CartaoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CartaoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))
    private val anoMesAtual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    val total: StateFlow<Double> = repository.totalCartaoNoMes(anoMesAtual)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val compras: StateFlow<List<Transacao>> = repository.transacoesCartaoNoMes(anoMesAtual)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- LÓGICA DE CONFIGURAÇÃO DO CARTÃO ---
    val cartao: StateFlow<CartaoEntity?> = repository.obterCartao(1L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun atualizarDatasCartao(diaFechamento: Int, diaVencimento: Int) {
        viewModelScope.launch {
            val cartaoAtual = repository.obterCartaoSync(1L)

            if (cartaoAtual != null) {
                // Se já existir, atualiza
                repository.salvarCartao(
                    cartaoAtual.copy(diaFechamento = diaFechamento, diaVencimento = diaVencimento)
                )
            } else {
                // SE NÃO EXISTIR, CRIA UM NOVO NO BANCO!
                repository.salvarCartao(
                    CartaoEntity(
                        id = 1L,
                        nome = "Cartão Principal",
                        diaFechamento = diaFechamento,
                        diaVencimento = diaVencimento
                    )
                )
            }
        }
    }
}