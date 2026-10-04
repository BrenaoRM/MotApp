package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FaturaChave
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.ehPagamentoDeFatura
import com.example.financacelular.data.estaComFaturaPaga
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

    // Cartões cadastrados (para mostrar o nome do cartão em cada lançamento)
    val cartoes: StateFlow<List<CartaoEntity>> = repository.listarTodosCartoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1. LANÇAMENTOS REALIZADOS (Débito/Dinheiro com data <= hoje OU Crédito cuja fatura já foi PAGA)
    val todasTransacoes: StateFlow<List<Transacao>> = repository.listarTransacoes()
        .map { transacoes ->
            val hoje = LocalDate.now()
            val faturasPagas = conjuntoFaturasPagas(transacoes)

            transacoes.filter { t ->
                if (t.ehPagamentoDeFatura()) return@filter false

                val faturaPaga = t.estaComFaturaPaga(faturasPagas)
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
        repository.listarTodosCartoes()
    ) { transacoes, recorrentes, cartoes ->
        val hoje = LocalDate.now()
        val mesAtualStr = String.format(Locale.ROOT, "%04d-%02d", hoje.year, hoje.monthValue)
        val faturasPagas = conjuntoFaturasPagas(transacoes)
        val primeiroCartaoId = cartoes.firstOrNull()?.id

        // Transações de cartão com fatura em aberto OU dinheiro/débito futuros
        val transacoesFuturasBanco = transacoes.filter { t ->
            if (t.ehPagamentoDeFatura()) return@filter false

            val faturaPaga = t.estaComFaturaPaga(faturasPagas)
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
            val ehReceita = recorrente.tipo == TipoTransacao.RECEITA
            val diaSeguro = minOf(recorrente.diaDoMes, hoje.lengthOfMonth())
            val dataPrevista = LocalDate.of(hoje.year, hoje.monthValue, diaSeguro)

            val jaLancada = transacoes.any { t ->
                val mesmoMes = t.anoMes == mesAtualStr || t.data.toString().startsWith(mesAtualStr)
                mesmoMes && (
                        t.recorrenteId == recorrente.id ||
                                // Descrição só vale para cobranças antigas sem vínculo (evita confundir assinaturas de mesmo nome)
                                (t.recorrenteId == null && (
                                        t.descricao == "${recorrente.nome} (Assinatura)" ||
                                                t.descricao == "${recorrente.nome} (Recorrente)"
                                        ))
                        )
            }

            // Despesa: cartão da própria assinatura (ou o primeiro cartão, para assinaturas antigas). Receita: sem cartão.
            val cartaoPrevisto = if (ehReceita) null else (recorrente.cartaoId ?: primeiroCartaoId)
            val faturaDesteMesPaga = cartaoPrevisto != null &&
                    FaturaChave(cartaoPrevisto, mesAtualStr) in faturasPagas

            if (!ehReceita && cartaoPrevisto == null) {
                null // despesa de cartão sem nenhum cartão cadastrado: não há fatura para prever
            } else if (!jaLancada && (ehReceita || !faturaDesteMesPaga)) {
                Transacao(
                    id = -1,
                    valor = recorrente.valor,
                    data = dataPrevista,
                    tipo = recorrente.tipo,
                    categoriaId = recorrente.categoriaId,
                    descricao = "${recorrente.nome} (Previsto)",
                    formaPagamento = if (ehReceita) FormaPagamento.DEBITO else FormaPagamento.CARTAO_CREDITO,
                    cartaoId = cartaoPrevisto,
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