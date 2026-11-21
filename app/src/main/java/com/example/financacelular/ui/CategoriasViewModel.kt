package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.TipoTransacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Uma categoria com quantos lançamentos e recorrências a usam. */
data class ItemCategoria(
    val categoria: Categoria,
    val lancamentos: Int,
    val recorrencias: Int
) {
    /** "Fatura" é do sistema: o pagamento de cartão a encontra pelo nome, então não pode ser renomeada nem excluída. */
    val sistema: Boolean get() = categoria.nome.equals("Fatura", ignoreCase = true)
    val emUso: Boolean get() = lancamentos > 0 || recorrencias > 0
}

class CategoriasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    /** Em ordem alfabética; null = carregando. */
    val itens: StateFlow<List<ItemCategoria>?> = combine(
        repository.listarCategorias(),
        repository.contarTransacoesPorCategoria(),
        repository.contarRecorrentesPorCategoria()
    ) { categorias, transacoes, recorrentes ->
        val lancamentosPorCategoria = transacoes.associate { it.categoriaId to it.total }
        val recorrenciasPorCategoria = recorrentes.associate { it.categoriaId to it.total }
        categorias
            .sortedBy { it.nome.lowercase() }
            .map { ItemCategoria(it, lancamentosPorCategoria[it.id] ?: 0, recorrenciasPorCategoria[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Cria a categoria ([id] nulo) ou atualiza nome e ícone da que já existe. */
    fun salvar(id: Long?, nome: String, tipo: TipoTransacao, icone: String?) {
        val nomeTratado = nome.trim()
        if (nomeTratado.isEmpty()) return
        viewModelScope.launch {
            if (id == null) {
                repository.salvarCategoria(Categoria(nome = nomeTratado, tipo = tipo, icone = icone))
            } else {
                repository.atualizarCategoria(Categoria(id = id, nome = nomeTratado, tipo = tipo, icone = icone))
            }
        }
    }

    /** Exclui a categoria e tudo que depende dela (lançamentos, recorrências e limites de orçamento). */
    fun excluir(categoria: Categoria) {
        viewModelScope.launch { repository.excluirCategoriaComDados(categoria) }
    }

    /** Move os lançamentos e as recorrências para [destino] (mesmo tipo) e exclui a categoria. */
    fun moverEExcluir(categoria: Categoria, destino: Categoria) {
        if (destino.id == categoria.id || destino.tipo != categoria.tipo) return
        viewModelScope.launch { repository.moverEExcluirCategoria(categoria, destino) }
    }
}
