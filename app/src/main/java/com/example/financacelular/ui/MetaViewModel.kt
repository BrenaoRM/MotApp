package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Meta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MetaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val metas: StateFlow<List<Meta>> = repository.listarMetas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun criar(nome: String, valorAlvo: Double) {
        if (nome.isBlank() || valorAlvo <= 0) return
        viewModelScope.launch {
            repository.salvarMeta(Meta(nome = nome, valorAlvo = valorAlvo))
        }
    }

    fun adicionarValor(meta: Meta, valor: Double) {
        viewModelScope.launch {
            repository.atualizarMeta(meta.copy(valorAtual = meta.valorAtual + valor))
        }
    }

    fun excluir(meta: Meta) {
        viewModelScope.launch { repository.excluirMeta(meta) }
    }
}
