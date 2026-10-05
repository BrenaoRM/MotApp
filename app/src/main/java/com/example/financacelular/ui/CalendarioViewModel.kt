package com.example.financacelular.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AfazerEntity
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.CalendarService
import com.example.financacelular.data.EventoGoogleAgenda
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.ResultadoCalendario
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class ItemCalendario(
    val transacao: Transacao,
    val ehFaturaNaoPaga: Boolean = false
)

class CalendarioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FinancaRepository = FinancaRepository.getInstance(AppDatabase.getInstance(application))

    val transacoesCalendario: StateFlow<List<ItemCalendario>> = repository.listarTransacoes()
        .map { lista ->
            lista.map { transacao -> ItemCalendario(transacao = transacao, ehFaturaNaoPaga = false) }
        }
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