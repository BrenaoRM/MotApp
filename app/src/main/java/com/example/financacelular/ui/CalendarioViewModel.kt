package com.example.financacelular.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AfazerEntity
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.CalendarService
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.EventoGoogleAgenda
import com.example.financacelular.data.FaturaChave
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.ResultadoCalendario
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.calcularVencimentoFatura
import com.example.financacelular.data.chaveFatura
import com.example.financacelular.data.conjuntoFaturasPagas
import com.example.financacelular.data.ehPagamentoDeFatura
import com.example.financacelular.data.faturaQuitadaPorEstePagamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Item da lista financeira do calendário.
 *
 * - Lançamento comum: compra, receita, despesa avulsa.
 * - [ehPagamentoFatura]: pagamento de uma fatura de cartão. Aparece na lista, mas NÃO entra nas
 *   somas nem nos pontinhos do dia (o valor já está nas compras do cartão; contar de novo duplicaria).
 * - [ehFaturaNaoPaga]: item virtual (não existe no banco) que mostra, no dia do vencimento,
 *   uma fatura ainda em aberto. Também fica fora das somas.
 */
data class ItemCalendario(
    val transacao: Transacao,
    val ehFaturaNaoPaga: Boolean = false,
    val ehPagamentoFatura: Boolean = false,
    val titulo: String = transacao.descricao ?: "Transação",
    val subtitulo: String? = null,
    val categoriaNome: String? = null
) {
    /** True quando o item deve ser somado como gasto/ganho do dia. */
    val contaNosTotais: Boolean get() = !ehFaturaNaoPaga && !ehPagamentoFatura
}

private val LocalePtBr: Locale = Locale.forLanguageTag("pt-BR")

/** "2026-09" -> "set/2026". */
private fun rotuloMesFatura(anoMes: String): String {
    val ym = runCatching { YearMonth.parse(anoMes) }.getOrNull() ?: return anoMes
    val mes = ym.month.getDisplayName(TextStyle.SHORT, LocalePtBr).removeSuffix(".")
    return "$mes/${ym.year}"
}

private fun montarItensCalendario(
    transacoes: List<Transacao>,
    cartoes: List<CartaoEntity>,
    categorias: List<Categoria>
): List<ItemCalendario> {
    val cartoesPorId = cartoes.associateBy { it.id }
    val categoriasPorId = categorias.associateBy { it.id }
    val categoriaFaturaId = categorias.find { it.nome.equals("Fatura", ignoreCase = true) }?.id ?: 0L
    val pagas = conjuntoFaturasPagas(transacoes)

    val itens = ArrayList<ItemCalendario>(transacoes.size + 8)

    // 1) Lançamentos reais (compras, receitas, pagamentos de fatura)
    for (t in transacoes) {
        val quitada = t.faturaQuitadaPorEstePagamento()
        if (quitada != null || t.ehPagamentoDeFatura()) {
            val nomeCartao = quitada?.let { cartoesPorId[it.cartaoId]?.nome ?: "Cartão" }
            val titulo = if (quitada != null) {
                "Fatura ${rotuloMesFatura(quitada.anoMes)} · $nomeCartao"
            } else {
                t.descricao ?: "Pagamento de fatura"
            }
            itens += ItemCalendario(
                transacao = t,
                ehPagamentoFatura = true,
                titulo = titulo,
                subtitulo = "Pagamento · não soma nos totais do dia",
                categoriaNome = "Fatura"
            )
        } else {
            val nomeCategoria = categoriasPorId[t.categoriaId]?.nome
            val nomeCartao = t.cartaoId?.let { cartoesPorId[it]?.nome }
            itens += ItemCalendario(
                transacao = t,
                titulo = t.descricao ?: nomeCategoria ?: "Transação",
                subtitulo = listOfNotNull(nomeCategoria ?: "Sem categoria", nomeCartao).joinToString(" · "),
                categoriaNome = nomeCategoria
            )
        }
    }

    // 2) Faturas em aberto: um item virtual no dia do vencimento de cada fatura não paga
    val totais = HashMap<FaturaChave, Double>()
    for (t in transacoes) {
        if (t.formaPagamento != FormaPagamento.CARTAO_CREDITO) continue
        val chave = t.chaveFatura() ?: continue
        totais[chave] = (totais[chave] ?: 0.0) + t.valor
    }
    for ((chave, total) in totais) {
        if (total <= 0.0 || chave in pagas) continue
        val cartao = cartoesPorId[chave.cartaoId] ?: continue
        val mes = runCatching { YearMonth.parse(chave.anoMes) }.getOrNull() ?: continue
        val vencimento = calcularVencimentoFatura(mes, cartao.diaVencimento, cartao.diaFechamento)
        itens += ItemCalendario(
            transacao = Transacao(
                valor = total,
                data = vencimento,
                categoriaId = categoriaFaturaId,
                tipo = TipoTransacao.DESPESA,
                descricao = "Fatura ${rotuloMesFatura(chave.anoMes)} · ${cartao.nome}",
                formaPagamento = FormaPagamento.DINHEIRO,
                anoMes = chave.anoMes
            ),
            ehFaturaNaoPaga = true,
            titulo = "Fatura ${rotuloMesFatura(chave.anoMes)} · ${cartao.nome}",
            subtitulo = "Vence neste dia · Não paga",
            categoriaNome = "Fatura"
        )
    }

    // Ordem dentro do dia: faturas em aberto, lançamentos comuns, pagamentos de fatura.
    // (sortedBy é estável: a ordem original de cada grupo é mantida.)
    return itens.sortedBy {
        when {
            it.ehFaturaNaoPaga -> 0
            it.ehPagamentoFatura -> 2
            else -> 1
        }
    }
}

class CalendarioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val transacoesCalendario: StateFlow<List<ItemCalendario>> = combine(
        repository.listarTransacoes(),
        repository.listarTodosCartoes(),
        repository.listarCategorias()
    ) { transacoes, cartoes, categorias ->
        montarItensCalendario(transacoes, cartoes, categorias)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorias = repository.listarCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val afazeres = repository.listarAfazeres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _eventosGoogle = MutableStateFlow<List<EventoGoogleAgenda>>(emptyList())
    val eventosGoogle: StateFlow<List<EventoGoogleAgenda>> = _eventosGoogle

    private val _diasComEventosGoogle = MutableStateFlow<Set<LocalDate>>(emptySet())
    /** Dias do mês exibido que têm evento no Google Agenda (para o pontinho do calendário). */
    val diasComEventosGoogle: StateFlow<Set<LocalDate>> = _diasComEventosGoogle

    /**
     * E-mail da conta Google conectada no app (a mesma usada no backup do Drive,
     * guardada em "email_utilizador"). Só os calendários dessa conta são lidos e gravados;
     * sem conta conectada, nada é lido.
     */
    private fun contaConectada(): String? =
        getApplication<Application>()
            .getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
            .getString("email_utilizador", null)
            ?.takeIf { it.isNotBlank() }

    private var jobEventos: Job? = null
    private var jobDias: Job? = null
    private var dataCarregada: LocalDate? = null
    private var mesCarregado: YearMonth? = null

    /** Carrega os eventos do Google Agenda do dia, fora da thread principal. */
    fun carregarEventosGoogle(data: LocalDate) {
        dataCarregada = data
        // Cancela a busca anterior para um dia antigo não sobrescrever o resultado do dia atual.
        jobEventos?.cancel()
        jobEventos = viewModelScope.launch {
            _eventosGoogle.value = withContext(Dispatchers.IO) {
                CalendarService.buscarEventosDoDia(getApplication(), data, contaConectada())
            }
        }
    }

    /** Carrega quais dias do mês têm eventos no Google Agenda, fora da thread principal. */
    fun carregarDiasComEventos(mes: YearMonth) {
        mesCarregado = mes
        jobDias?.cancel()
        jobDias = viewModelScope.launch {
            _diasComEventosGoogle.value = withContext(Dispatchers.IO) {
                CalendarService.buscarDiasComEventos(getApplication(), mes, contaConectada())
            }
        }
    }

    /** Recarrega o dia e o mês que estão na tela (depois de criar, editar ou excluir um evento). */
    private fun recarregarEventos() {
        dataCarregada?.let { carregarEventosGoogle(it) }
        mesCarregado?.let { carregarDiasComEventos(it) }
    }

    /**
     * Cria um item na agenda.
     * - Com sincronização: grava só no Google Agenda (o evento aparece no app pela leitura do calendário),
     *   no [hora] escolhido ou como evento de [diaInteiro]. Se a gravação falhar, o item é salvo
     *   localmente para não perder nada.
     * - Sem sincronização: grava só como afazer local (que não tem horário).
     * [aoConcluir] recebe null quando não houve tentativa de sincronização.
     */
    fun inserirAfazerESincronizar(
        data: LocalDate,
        titulo: String,
        sincronizarGoogle: Boolean,
        hora: LocalTime = LocalTime.of(9, 0),
        diaInteiro: Boolean = false,
        horaFim: LocalTime? = null,
        aoConcluir: (ResultadoCalendario?) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (!sincronizarGoogle) {
                repository.salvarAfazer(AfazerEntity(data = data, titulo = titulo))
                aoConcluir(null)
                return@launch
            }

            val resultado = withContext(Dispatchers.IO) {
                CalendarService.adicionarEvento(
                    context = getApplication(),
                    titulo = titulo,
                    descricao = CalendarService.DESCRICAO_EVENTO_APP,
                    data = data,
                    contaEmail = contaConectada(),
                    hora = hora,
                    diaInteiro = diaInteiro,
                    horaFim = horaFim
                )
            }
            if (resultado == ResultadoCalendario.SUCESSO) {
                recarregarEventos()
            } else {
                repository.salvarAfazer(AfazerEntity(data = data, titulo = titulo))
            }
            aoConcluir(resultado)
        }
    }

    fun atualizarEvento(
        evento: EventoGoogleAgenda,
        titulo: String,
        descricao: String?,
        data: LocalDate,
        hora: LocalTime,
        horaFim: LocalTime,
        diaInteiro: Boolean,
        aoConcluir: (ResultadoCalendario) -> Unit = {}
    ) {
        viewModelScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                CalendarService.atualizarEvento(getApplication(), evento, titulo, descricao, data, hora, diaInteiro, horaFim)
            }
            if (resultado == ResultadoCalendario.SUCESSO) recarregarEventos()
            aoConcluir(resultado)
        }
    }

    fun excluirEvento(
        evento: EventoGoogleAgenda,
        aoConcluir: (ResultadoCalendario) -> Unit = {}
    ) {
        viewModelScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                CalendarService.excluirEvento(getApplication(), evento)
            }
            if (resultado == ResultadoCalendario.SUCESSO) recarregarEventos()
            aoConcluir(resultado)
        }
    }

    fun atualizarAfazer(afazer: AfazerEntity, titulo: String, data: LocalDate) {
        viewModelScope.launch {
            repository.atualizarAfazer(afazer.copy(titulo = titulo, data = data))
        }
    }

    fun alternarConcluido(afazer: AfazerEntity) {
        viewModelScope.launch {
            repository.atualizarAfazer(afazer.copy(concluido = !afazer.concluido))
        }
    }

    fun excluirAfazer(afazer: AfazerEntity) {
        viewModelScope.launch {
            repository.excluirAfazer(afazer)
        }
    }
}