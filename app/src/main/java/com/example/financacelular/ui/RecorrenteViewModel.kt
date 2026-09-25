package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecorrenteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val recorrentes: StateFlow<List<DespesaRecorrente>> = repository.listarRecorrentes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun excluir(despesa: DespesaRecorrente) {
        viewModelScope.launch { repository.excluirRecorrente(despesa) }
    }

    fun lancarEsteMes(despesa: DespesaRecorrente, aoLancar: () -> Unit) {
        viewModelScope.launch {
            repository.salvarTransacao(
                Transacao(
                    valor = despesa.valor,
                    data = LocalDate.now(),
                    categoriaId = despesa.categoriaId,
                    tipo = despesa.tipo,
                    descricao = despesa.nome
                )
            )
            aoLancar()
        }
    }
}
