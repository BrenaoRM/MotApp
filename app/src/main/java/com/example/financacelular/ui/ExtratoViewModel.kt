package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExtratoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val categorias: StateFlow<List<Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1. LANÇAMENTOS REALIZADOS
    val todasTransacoes: StateFlow<List<Transacao>> = combine(
        repository.listarTransacoes(),
        repository.listarTransacoes()
    ) { transacoes, todas ->
        val hoje = LocalDate.now()

        transacoes.filter { t ->
            val ehPagamentoFatura = t.descricao?.startsWith("Pagamento de Fatura") == true
            if (ehPagamentoFatura) return@filter false

            val anoMesStr = t.anoMes ?: String.format("%d-%02d", t.data.year, t.data.monthValue)
            val faturaPaga = todas.any { it.cartaoId == null && it.anoMes == anoMesStr && it.descricao == "Pagamento de Fatura - $anoMesStr" }
            val ehCartao = t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null
            val ehFuturo = t.data.isAfter(hoje)

            if (ehCartao) {
                // CORREÇÃO: Se for cartão, só vira "Realizado" se a fatura foi paga (independente do dia do mês)
                faturaPaga
            } else {
                // Se for dinheiro/débito, vira "Realizado" assim que a data chegar
                !ehFuturo
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. FUTUROS LANÇAMENTOS (PREVISTOS / PENDENTES)
    val futurosLancamentos: StateFlow<List<Transacao>> = combine(
        repository.listarTransacoes(),
        repository.listarRecorrentes(),
        repository.listarTransacoes()
    ) { transacoes, recorrentes, todas ->
        val hoje = LocalDate.now()
        val mesAtualStr = String.format("%04d-%02d", hoje.year, hoje.monthValue)

        val faturaPaga = todas.any { it.cartaoId == null && it.anoMes == mesAtualStr && it.descricao == "Pagamento de Fatura - $mesAtualStr" }

        // A. Filtra as transações reais do banco que ainda estão pendentes
        val transacoesFuturasBanco = transacoes.filter { t ->
            val eDoMesAtual = t.data.toString().startsWith(mesAtualStr)
            if (!eDoMesAtual) return@filter false

            val anoMesStr = t.anoMes ?: mesAtualStr
            val faturaDesteMesPaga = todas.any { it.cartaoId == null && it.anoMes == anoMesStr && it.descricao == "Pagamento de Fatura - $anoMesStr" }
            val ehCartao = t.formaPagamento == FormaPagamento.CARTAO_CREDITO && t.cartaoId != null
            val ehFuturo = t.data.isAfter(hoje)

            if (ehCartao) {
                // CORREÇÃO: Se for cartão, fica no "Futuro" apenas se a fatura NÃO foi paga. Pagou, ele some daqui.
                !faturaDesteMesPaga
            } else {
                // Se não for cartão, fica no "Futuro" apenas se a data ainda não chegou
                ehFuturo
            }
        }

        // B. Projeta as Assinaturas/Recorrentes que ainda não foram lançadas
        val recorrentesPrevistas = recorrentes.mapNotNull { recorrente ->
            val dataPrevista = LocalDate.of(hoje.year, hoje.month, recorrente.diaDoMes)

            // CORREÇÃO: Usar startsWith para reconhecer o sufixo "(Assinatura)" salvo no banco
            val jaLancada = transacoes.any {
                it.data.toString().startsWith(mesAtualStr) &&
                        it.descricao?.startsWith(recorrente.nome) == true
            }

            // Só cria a projeção se ela ainda não existe no banco e se a fatura ainda não foi paga
            if (!jaLancada && (!faturaPaga || recorrente.tipo == TipoTransacao.RECEITA)) {
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

        // Junta tudo e ordena por data
        (transacoesFuturasBanco + recorrentesPrevistas).sortedBy { it.data }

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun atualizar(transacao: Transacao) {
        if (transacao.id < 0) return
        viewModelScope.launch { repository.atualizarTransacao(transacao) }
    }

    fun excluir(transacao: Transacao) {
        if (transacao.id < 0) return
        viewModelScope.launch { repository.excluirTransacao(transacao) }
    }
}