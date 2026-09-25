package com.example.financacelular.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExportarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun exportarPara(
        context: Context,
        uri: Uri,
        categorias: List<Categoria>,
        aoTerminar: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val transacoes = repository.listarTransacoesUmaVez()
                val linhas = StringBuilder("data,tipo,categoria,valor,descricao,formaPagamento\n")
                transacoes.forEach { t ->
                    val nomeCategoria = categorias.find { it.id == t.categoriaId }?.nome ?: ""
                    val descricaoSegura = (t.descricao ?: "").replace("\"", "'")
                    linhas.append("${t.data},${t.tipo},${nomeCategoria},${t.valor},\"$descricaoSegura\",${t.formaPagamento}\n")
                }
                context.contentResolver.openOutputStream(uri)?.use { saida ->
                    saida.write(linhas.toString().toByteArray())
                }
                aoTerminar(true)
            } catch (e: Exception) {
                aoTerminar(false)
            }
        }
    }
}
