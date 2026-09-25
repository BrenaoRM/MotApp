package com.example.financacelular.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.TipoTransacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var novoNome by mutableStateOf("")
        private set
    var novoTipo by mutableStateOf(TipoTransacao.DESPESA)
        private set

    fun onNomeChange(nome: String) { novoNome = nome }
    fun onTipoChange(tipo: TipoTransacao) { novoTipo = tipo }

    fun adicionar() {
        if (novoNome.isBlank()) return
        viewModelScope.launch {
            repository.salvarCategoria(Categoria(nome = novoNome, tipo = novoTipo))
            novoNome = ""
        }
    }

    fun excluir(categoria: Categoria) {
        viewModelScope.launch { repository.excluirCategoria(categoria) }
    }

    fun atualizar(categoria: Categoria) {
        viewModelScope.launch { repository.atualizarCategoria(categoria) }
    }
}
