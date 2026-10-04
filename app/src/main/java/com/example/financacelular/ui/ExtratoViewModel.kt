package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.util.Locale
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExtratoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1. LANÇAMENTOS REALIZADOS (Débito/Dinheiro com data <= hoje OU Crédito cuja fatura já foi PAGA)
    val todasTransacoes: StateFlow<List<Transacao>> = repository.listarTransacoes()
        .map { transacoes ->
            val hoje = LocalDate.now()
            val faturasPagas = transacoes.asSequence()
                .filter { it.cartaoId == null && it.descricao?.startsWith("Pagamento de Fatura - ") == true }
                .mapNotNull { it.anoMes }
                .toSet()

        transacoes.filter { t ->
            val ehPagamentoFatura = t.descricao?.startsWith("Pagamento de Fatura") == true
            if (ehPagamentoFatura) return@filter false

            val anoMesStr = t.anoMes ?: String.format(Locale.ROOT, "%d-%02d", t.data.year, t.data.monthValue)

            val faturaPaga = anoMesStr in faturasPagas
            val ehCartao = t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null
            val ehFuturo = t.data.isAfter(hoje)

            if (ehCartao) {
                faturaPaga // Compras no cartão entram em "Realizados" assim que a fatura do anoMes for paga
            } else {
                !ehFuturo // Débito/Dinheiro entram em "Realizados" pela data de ocorrência
            }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. FUTUROS LANÇAMENTOS / PENDENTES (Débito/Dinheiro futuro OU Crédito com Fatura PENDENTE)
    val futurosLancamentos: StateFlow<List<Transacao>> = combine(
        repository.listarTransacoes(),
        repository.listarRecorrentes(),
        repository.listarTransacoes()
    ) { transacoes, recorrentes, todas ->
        val hoje = LocalDate.now()
        val mesAtualStr = String.format(Locale.ROOT, "%04d-%02d", hoje.year, hoje.monthValue)
        val faturasPagas = todas.asSequence()
            .filter { it.cartaoId == null && it.descricao?.startsWith("Pagamento de Fatura - ") == true }
            .mapNotNull { it.anoMes }
            .toSet()

        // Transações de cartão com fatura em aberto OU dinheiro/débito futuros
        val transacoesFuturasBanco = transacoes.filter { t ->
            val ehPagamentoFatura = t.descricao?.startsWith("Pagamento de Fatura") == true
            if (ehPagamentoFatura) return@filter false

            val anoMesStr = t.anoMes ?: String.format(Locale.ROOT, "%d-%02d", t.data.year, t.data.monthValue)
            val faturaPaga = anoMesStr in faturasPagas
            val ehCartao = t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null
            val ehFuturo = t.data.isAfter(hoje)

            if (ehCartao) {
                !faturaPaga
            } else {
                ehFuturo
            }
        }

        // Projeta assinaturas/recorrentes do mês atual que ainda não foram lançadas
        val recorrentesPrevistas = recorrentes.mapNotNull { recorrente ->
            val diaSeguro = minOf(recorrente.diaDoMes, hoje.lengthOfMonth())
            val dataPrevista = LocalDate.of(hoje.year, hoje.monthValue, diaSeguro)

            val jaLancada = transacoes.any { t ->
                val mesmoMes = t.anoMes == mesAtualStr || t.data.toString().startsWith(mesAtualStr)
                mesmoMes && (
                        t.recorrenteId == recorrente.id ||
                                t.descricao == "${recorrente.nome} (Assinatura)" ||
                                t.descricao == "${recorrente.nome} (Recorrente)"
                        )
            }

            val faturaDesteMesPaga = mesAtualStr in faturasPagas
            if (!jaLancada && (!faturaDesteMesPaga || recorrente.tipo == TipoTransacao.RECEITA)) {
                Transacao(
                    id = -1,
                    valor = recorrente.valor,
                    data = dataPrevista,
                    tipo = recorrente.tipo,
                    categoriaId = recorrente.categoriaId,
                    descricao = "${recorrente.nome} (Previsto)",
                    formaPagamento = FormaPagamento.CARTAO_CREDITO,
                    cartaoId = 1L,
                    anoMes = mesAtualStr
                )
            } else null
        }

        (transacoesFuturasBanco + recorrentesPrevistas).sortedBy { it.data }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun atualizar(transacao: Transacao) {
        if (transacao.id < 0) return
        viewModelScope.launch { repository.atualizarTransacao(transacao) }
    }

    fun excluir(transacao: Transacao) {
        if (transacao.id < 0) return
        viewModelScope.launch { repository.excluirTransacao(transacao) }
    }
}