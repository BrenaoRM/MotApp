package com.example.financacelular.ui

import com.example.financacelular.data.somaEmReais
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FORMATO_ANO_MES
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.comCategoriaDaFatura
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.estaRealizada
import com.example.financacelular.data.faturasProximasDoVencimento
import com.example.financacelular.data.idDaCategoriaFatura
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class ResumoMovimentacao(
    val titulo: String,
    val categoriaId: Long,
    val tipo: TipoTransacao,
    val total: Double,
    val transacoes: List<Transacao>,
    val icone: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinancaRepository.getInstance(AppDatabase.getInstance(application))
    private val _mesSelecionado: StateFlow<YearMonth> = MesGlobal.mes
    val mesSelecionado: StateFlow<YearMonth> = _mesSelecionado

    private val anoMesTexto: Flow<String> = _mesSelecionado.map {
        it.format(FORMATO_ANO_MES)
    }

    private var alertaJaExibidoNestaSessao = false
    private val _triggerVerificacao = MutableStateFlow(0)

    // Alerta se QUALQUER cartão tem fatura em aberto (com valor) vencendo em até 2 dias
    val alertaFaturaPendente: StateFlow<Boolean> = combine(
        repository.listarTodosCartoes(),
        repository.listarTransacoes(),
        _triggerVerificacao
    ) { cartoes, transacoes, _ ->
        if (alertaJaExibidoNestaSessao) return@combine false
        faturasProximasDoVencimento(cartoes, transacoes).isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun marcarAlertaComoExibido() {
        alertaJaExibidoNestaSessao = true
        _triggerVerificacao.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transacoesFiltradasDoMes: Flow<List<Transacao>> = combine(
        anoMesTexto,
        repository.listarTransacoes(),
        repository.listarCategorias()
    ) { anoMes, todas, categorias ->
        val hoje = LocalDate.now()

        val faturasPagas = conjuntoFaturasPagas(todas)

        val categoriaFaturaId = idDaCategoriaFatura(categorias)

        todas.filter { t ->
            val anoMesTransacao = t.anoMes ?: t.data.toString().take(7)
            val ehDoMes = anoMesTransacao == anoMes
            if (!ehDoMes) return@filter false

            t.estaRealizada(faturasPagas, hoje)
        }.map { t ->
            t.comCategoriaDaFatura(categoriaFaturaId)
        }
    }.flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalDespesas: StateFlow<Double> = transacoesFiltradasDoMes
        .map { lista -> lista.filter { it.tipo == TipoTransacao.DESPESA }.somaEmReais { it.valorCentavos } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalReceitas: StateFlow<Double> = transacoesFiltradasDoMes
        .map { lista -> lista.filter { it.tipo == TipoTransacao.RECEITA }.somaEmReais { it.valorCentavos } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Acumulado por dia (índice 0 = dia 1) até [ultimoDia]; o último valor é igual ao total exibido. */
    data class SerieDoMes(
        val entradas: List<Float> = emptyList(),
        val saidas: List<Float> = emptyList()
    )

    val serieDoMes: StateFlow<SerieDoMes> = combine(_mesSelecionado, transacoesFiltradasDoMes) { mes, lista ->
        val hoje = LocalDate.now()
        val ultimoDia = if (mes == YearMonth.from(hoje)) hoje.dayOfMonth else mes.lengthOfMonth()

        fun acumular(tipo: TipoTransacao): List<Float> {
            val porDia = DoubleArray(ultimoDia)
            lista.filter { it.tipo == tipo }.forEach { t ->
                val ym = YearMonth.from(t.data)
                // Compra no crédito pode ter data fora do mês da fatura: limita ao intervalo do mês
                val dia = when {
                    ym.isBefore(mes) -> 1
                    ym.isAfter(mes) -> ultimoDia
                    else -> t.data.dayOfMonth.coerceIn(1, ultimoDia)
                }
                porDia[dia - 1] += t.valor
            }
            var soma = 0.0
            return porDia.map { soma += it; soma.toFloat() }
        }

        SerieDoMes(acumular(TipoTransacao.RECEITA), acumular(TipoTransacao.DESPESA))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SerieDoMes())

    /** Aportes dos 6 meses que terminam no mês selecionado (meses sem aporte valem 0); o último item é o mês exibido. */
    val aportesDosUltimosMeses: StateFlow<List<TotalMensalInvestimento>> = combine(
        _mesSelecionado,
        repository.listarInvestimentos()
    ) { mes, investimentos ->
        val totaisPorMes = investimentos.groupBy { it.anoMes }.mapValues { (_, invs) -> invs.somaEmReais { it.valorInvestidoCentavos } }
        (5 downTo 0).map { atras ->
            val anoMes = mes.minusMonths(atras.toLong()).format(FORMATO_ANO_MES)
            TotalMensalInvestimento(anoMes, totaisPorMes[anoMes] ?: 0.0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias: StateFlow<List<com.example.financacelular.data.Categoria>> = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val resumoDoMes: StateFlow<List<ResumoMovimentacao>> = combine(
        transacoesFiltradasDoMes,
        categorias
    ) { transacoesMes, listaCategorias ->
        transacoesMes.groupBy { t ->
            t.categoriaId to t.tipo
        }.map { (chave, lista) ->
            val categoriaId = chave.first
            val tipo = chave.second
            val categoriaDoResumo = listaCategorias.find { c -> c.id == categoriaId }
            val nomeCategoria = categoriaDoResumo?.nome ?: "Sem categoria"
            ResumoMovimentacao(
                titulo = nomeCategoria,
                categoriaId = categoriaId,
                tipo = tipo,
                total = lista.somaEmReais { it.valorCentavos },
                transacoes = lista,
                icone = categoriaDoResumo?.icone
            )
        }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun mesAnterior() {
        MesGlobal.anterior()
    }

    fun mesSeguinte() {
        MesGlobal.seguinte()
    }
}