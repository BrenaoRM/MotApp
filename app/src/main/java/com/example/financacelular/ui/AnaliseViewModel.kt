package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.comCategoriaDaFatura
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.estaRealizada
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.idDaCategoriaFatura
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.temporal.ChronoUnit

data class ItemGastoCategoria(val nomeCategoria: String, val total: Double, val icone: String? = null)
data class TopDespesaItem(val descricao: String, val nomeCategoria: String, val valor: Double, val data: String, val icone: String? = null)
data class TotalMensalInvestimento(val anoMes: String, val total: Double)

class AnaliseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    /** Intervalo personalizado; `null` = a Análise segue o mês global do cabeçalho. */
    private val _personalizado = MutableStateFlow<Periodo?>(null)

    private val _periodo: StateFlow<Periodo> = combine(MesGlobal.mes, _personalizado) { mes, custom ->
        custom ?: Periodo.mes(mes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Periodo.mes(MesGlobal.mes.value))
    val periodo: StateFlow<Periodo> = _periodo

    /** Transações que entram nas contas da Análise para o [periodo]: sem pagamento de fatura, sem futuras nem fatura em aberto. */
    private fun transacoesValidas(periodo: Periodo, todas: List<Transacao>, categorias: List<Categoria>): List<Transacao> {
        val hoje = LocalDate.now()
        val primeiroAnoMes = YearMonth.from(periodo.inicio).toString()
        val ultimoAnoMes = YearMonth.from(periodo.fim).toString()
        val faturasPagas = conjuntoFaturasPagas(todas)
        val categoriaFaturaId = idDaCategoriaFatura(categorias)

        return todas.filter { t ->
            // Meses fechados seguem o mês de competência (fatura do cartão); intervalo livre segue a data
            val ehDoPeriodo = if (periodo.mesesFechados) {
                val anoMesTransacao = t.anoMes ?: t.data.toString().take(7)
                anoMesTransacao >= primeiroAnoMes && anoMesTransacao <= ultimoAnoMes
            } else {
                !t.data.isBefore(periodo.inicio) && !t.data.isAfter(periodo.fim)
            }
            if (!ehDoPeriodo) return@filter false
            t.estaRealizada(faturasPagas, hoje)
        }.map { t ->
            t.comCategoriaDaFatura(categoriaFaturaId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transacoesValidasDoPeriodo = combine(
        _periodo,
        repository.listarTransacoes(),
        repository.listarCategorias()
    ) { periodo, todas, categorias ->
        transacoesValidas(periodo, todas, categorias)
    }.flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val despesasPorCategoria: StateFlow<List<ItemGastoCategoria>> = combine(transacoesValidasDoPeriodo, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }
            .groupBy { it.categoriaId }
            .map { (catId, lista) ->
                val cat = categorias.find { it.id == catId }
                ItemGastoCategoria(cat?.nome ?: "Sem categoria", lista.sumOf { it.valor }, cat?.icone)
            }.sortedByDescending { it.total }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val receitasPorCategoria: StateFlow<List<ItemGastoCategoria>> = combine(transacoesValidasDoPeriodo, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.RECEITA }
            .groupBy { it.categoriaId }
            .map { (catId, lista) ->
                val cat = categorias.find { it.id == catId }
                ItemGastoCategoria(cat?.nome ?: "Sem categoria", lista.sumOf { it.valor }, cat?.icone)
            }.sortedByDescending { it.total }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDespesasPeriodo: StateFlow<Double> = transacoesValidasDoPeriodo.map { transacoes ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }.sumOf { it.valor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalReceitasPeriodo: StateFlow<Double> = transacoesValidasDoPeriodo.map { transacoes ->
        transacoes.filter { it.tipo == TipoTransacao.RECEITA }.sumOf { it.valor }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Fluxo de caixa do período escolhido na tela, com o período anterior do mesmo tamanho para a variação; `null` enquanto carrega. */
    val fluxo: StateFlow<FluxoDoPeriodo?> = combine(
        _periodo,
        repository.listarTransacoes(),
        repository.listarCategorias()
    ) { periodo, todas, categorias ->
        calcularFluxo(
            transacoes = transacoesValidas(periodo, todas, categorias),
            transacoesAnteriores = transacoesValidas(periodo.deslocar(-1), todas, categorias),
            periodo = periodo
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topDespesas: StateFlow<List<TopDespesaItem>> = combine(transacoesValidasDoPeriodo, repository.listarCategorias()) { transacoes, categorias ->
        transacoes.filter { it.tipo == TipoTransacao.DESPESA }
            .sortedByDescending { it.valor }
            .take(5)
            .map { t ->
                val categoriaDaDespesa = categorias.find { it.id == t.categoriaId }
                val cat = categoriaDaDespesa?.nome ?: "Geral"
                TopDespesaItem(t.descricao ?: cat, cat, t.valor, t.data.toString(), categoriaDaDespesa?.icone)
            }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Aportes do período escolhido na tela; `null` enquanto carrega. */
    val aportes: StateFlow<AportesDoPeriodo?> = combine(_periodo, repository.listarInvestimentos()) { periodo, lista ->
        calcularAportes(lista, periodo)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Acumulado por dia, do início do período até hoje (ou o fim); o último valor é igual ao total exibido. */
    data class SerieDoPeriodo(
        val entradas: List<Float> = emptyList(),
        val saidas: List<Float> = emptyList()
    )

    val serieDoPeriodo: StateFlow<SerieDoPeriodo> = combine(_periodo, transacoesValidasDoPeriodo) { periodo, lista ->
        val ultimoDia = minOf(periodo.fim, LocalDate.now())
        if (ultimoDia.isBefore(periodo.inicio)) return@combine SerieDoPeriodo()
        val quantidade = (ChronoUnit.DAYS.between(periodo.inicio, ultimoDia) + 1).toInt()

        fun acumular(tipo: TipoTransacao): List<Float> {
            val porDia = DoubleArray(quantidade)
            lista.filter { it.tipo == tipo }.forEach { t ->
                // Compra no crédito pode ter data fora do período da fatura: limita ao intervalo
                val indice = ChronoUnit.DAYS.between(periodo.inicio, t.data).toInt().coerceIn(0, quantidade - 1)
                porDia[indice] += t.valor
            }
            var soma = 0.0
            return porDia.map { soma += it; soma.toFloat() }
        }

        SerieDoPeriodo(acumular(TipoTransacao.RECEITA), acumular(TipoTransacao.DESPESA))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SerieDoPeriodo())

    /** Mês global: anda o mês de todas as telas. Intervalo personalizado: anda só o intervalo da Análise. */
    fun periodoAnterior() {
        val custom = _personalizado.value
        if (custom == null) MesGlobal.anterior() else _personalizado.value = custom.deslocar(-1)
    }

    fun periodoSeguinte() {
        val custom = _personalizado.value
        if (custom == null) MesGlobal.seguinte() else _personalizado.value = custom.deslocar(1)
    }

    /** Um mês inteiro vira o mês global (vale para todas as telas); qualquer outro intervalo fica só na Análise. */
    fun definirPeriodo(novo: Periodo) {
        if (novo.ehMesUnico) {
            _personalizado.value = null
            MesGlobal.definir(YearMonth.from(novo.inicio))
        } else {
            _personalizado.value = novo
        }
    }
}