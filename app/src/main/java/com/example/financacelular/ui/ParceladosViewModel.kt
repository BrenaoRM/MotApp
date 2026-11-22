package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.estaComFaturaPaga
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CompraParceladaAgrupada(
    val chave: String,
    val descricaoBase: String,
    val totalParcelas: Int,
    val valorParcela: Double,
    val parcelasPagas: Int,
    val parcelasRestantes: Int,
    val valorTotal: Double,
    val valorPago: Double,
    val valorRestante: Double,
    val transacoesPendentes: List<Transacao>,
    val dataCompra: LocalDate,
    val cartaoNome: String?
) {
    /** Todas as parcelas foram pagas. */
    val quitada: Boolean get() = parcelasPagas == totalParcelas

    /** Sem parcelas a pagar, mas nem todas foram pagas: as pendentes foram canceladas. */
    val encerrada: Boolean get() = transacoesPendentes.isEmpty() && !quitada
}

private val REGEX_SUFIXO_PARCELA = Regex(" \\(\\d+/\\d+\\)$")

/** Nome da compra sem o " (3/10)" do final. */
private fun nomeBaseDaCompra(transacao: Transacao): String =
    transacao.descricao?.replace(REGEX_SUFIXO_PARCELA, "") ?: "Compra Parcelada"

/** Agrupa as parcelas por compra e calcula pago/restante pela soma real dos valores (a 1ª parcela pode ter centavos a mais). */
internal fun agruparComprasParceladas(
    transacoes: List<Transacao>,
    nomeDoCartao: (Long?) -> String? = { null }
): List<CompraParceladaAgrupada> {
    // Filtra apenas as transações parceladas (totalParcelas > 1)
    val transacoesParceladas = transacoes.filter { it.totalParcelas > 1 }

    // Descobre quais faturas (cartão + mês) já foram pagas
    val faturasPagas = conjuntoFaturasPagas(transacoes)

    // Compras novas agrupam pelo ID do grupo; as antigas (sem ID) pelo nome + total de parcelas
    val agrupado = transacoesParceladas.groupBy { t ->
        t.grupoParcelamentoId ?: "legado|${nomeBaseDaCompra(t)}|${t.totalParcelas}"
    }

    return agrupado.map { (chave, lista) ->
        val transacoesOrdenadas = lista.sortedBy { it.numeroParcela }
        val primeiraTransacao = transacoesOrdenadas.first()

        val pagas = lista.filter { it.estaComFaturaPaga(faturasPagas) }
        val transacoesPendentes = lista.filter { !it.estaComFaturaPaga(faturasPagas) }

        CompraParceladaAgrupada(
            chave = chave,
            descricaoBase = nomeBaseDaCompra(primeiraTransacao),
            totalParcelas = primeiraTransacao.totalParcelas,
            valorParcela = primeiraTransacao.valor,
            parcelasPagas = pagas.size,
            parcelasRestantes = transacoesPendentes.size,
            valorTotal = lista.sumOf { it.valor },
            valorPago = pagas.sumOf { it.valor },
            valorRestante = transacoesPendentes.sumOf { it.valor },
            transacoesPendentes = transacoesPendentes,
            dataCompra = primeiraTransacao.data,
            cartaoNome = nomeDoCartao(primeiraTransacao.cartaoId)
        )
    }.sortedByDescending { it.dataCompra } // Das mais recentes para as mais antigas
}

class ParceladosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    // null enquanto carrega
    val comprasAgrupadas: StateFlow<List<CompraParceladaAgrupada>?> = combine(
        repository.listarTransacoes(),
        repository.listarTodosCartoes()
    ) { transacoes, cartoes ->
        agruparComprasParceladas(transacoes) { id -> cartoes.find { it.id == id }?.nome }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Exclui todas as parcelas futuras que ainda não foram pagas (tudo ou nada)
    fun cancelarParcelasRestantes(transacoes: List<Transacao>) {
        viewModelScope.launch {
            repository.excluirTransacoes(transacoes)
        }
    }
}