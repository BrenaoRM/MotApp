package com.example.financacelular.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.CartaoEntity
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.idDaCategoriaFatura
import com.example.financacelular.ia.AlertaDispensado
import com.example.financacelular.ia.AnaliseCompleta
import com.example.financacelular.ia.AnaliseDoAssistente
import com.example.financacelular.ia.EstadoAssistente
import com.example.financacelular.ia.IaFinanceira
import com.example.financacelular.ia.Mensagem
import com.example.financacelular.ia.Pergunta
import com.example.financacelular.ia.Perguntas
import com.example.financacelular.ia.RespondedorDePerguntas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Aba Assistente: análise (atípicos + recomendações) e conversa. Todo cálculo de IA roda em `Dispatchers.Default`.
 * A conversa fica só em memória: o texto das perguntas não é gravado em lugar nenhum (nem no cache de vetores).
 */
class AssistenteViewModel(application: Application) : AndroidViewModel(application) {
    private val banco = AppDatabase.getInstance(application)
    private val repository = FinancaRepository.getInstance(banco)
    private val alertas = banco.alertaDispensadoDao()

    private class Entradas(
        val transacoes: List<Transacao>,
        val categorias: List<Categoria>,
        val recorrentes: List<DespesaRecorrente>,
        val orcamentos: List<Orcamento>,
        val dispensados: Set<Long>,
        val mes: YearMonth,
    )

    /** Entradas e análise da mesma rodada, trocadas juntas para o chat nunca ler um par misturado. */
    @Volatile private var ultima: Pair<Entradas, AnaliseCompleta>? = null
    private var genericasCache: Pair<List<Pair<Long, String>>, Map<Long, String>>? = null

    private val categoriasCompartilhadas = repository.listarCategorias().distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val categorias: StateFlow<List<Categoria>> = categoriasCompartilhadas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cartoes: StateFlow<List<CartaoEntity>> = repository.listarTodosCartoes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val estado: StateFlow<EstadoAssistente> = combine(
        repository.listarTransacoes().distinctUntilChanged(),
        categoriasCompartilhadas,
        repository.listarRecorrentes().distinctUntilChanged(),
        repository.listarHistoricoOrcamentos("9999-12").distinctUntilChanged(),
        alertas.listarIds().distinctUntilChanged(),
        MesGlobal.mes,
    ) { valores ->
        @Suppress("UNCHECKED_CAST")
        Entradas(
            valores[0] as List<Transacao>, valores[1] as List<Categoria>, valores[2] as List<DespesaRecorrente>,
            valores[3] as List<Orcamento>, (valores[4] as List<Long>).toSet(), valores[5] as YearMonth,
        )
    }.mapLatest { e ->
        IaFinanceira.iniciar(getApplication())
        // Depois de iniciar, config() só espera a leitura do asset: null aqui é falha definitiva, não demora
        val cfg = IaFinanceira.config() ?: return@mapLatest EstadoAssistente.Indisponivel
        val genericas = genericasDe(e.categorias)
        val resultado = withContext(Dispatchers.Default) {
            AnaliseDoAssistente.analisar(
                e.transacoes, e.categorias, e.recorrentes, e.orcamentos, e.dispensados, e.mes, cfg, genericas, LocalDate.now(),
            )
        }
        ultima = e to resultado
        resultado.estado
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EstadoAssistente.Carregando)

    private suspend fun genericasDe(categorias: List<Categoria>): Map<Long, String> {
        val faturaId = idDaCategoriaFatura(categorias)
        val despesas = categorias.filter { it.tipo == TipoTransacao.DESPESA && it.id != faturaId }
        val assinatura = despesas.map { it.id to it.nome }
        genericasCache?.takeIf { it.first == assinatura }?.let { return it.second }
        return withContext(Dispatchers.Default) { IaFinanceira.genericasDasCategorias(despesas) }.also { genericasCache = assinatura to it }
    }

    /** "Está certo": o alerta não volta (fica gravado no banco). */
    fun dispensar(transacaoId: Long) {
        viewModelScope.launch(Dispatchers.IO) { alertas.dispensar(AlertaDispensado(transacaoId, System.currentTimeMillis())) }
    }

    fun transacao(id: Long): Transacao? = ultima?.first?.transacoes?.firstOrNull { it.id == id }

    fun atualizar(transacao: Transacao) {
        if (transacao.id < 0) return
        viewModelScope.launch { repository.atualizarTransacao(transacao) }
    }

    fun excluir(transacao: Transacao) {
        if (transacao.id < 0) return
        viewModelScope.launch { repository.excluirTransacao(transacao) }
    }

    // ---------------------------------------------------------------- perguntas

    private val _conversa = MutableStateFlow<List<Mensagem>>(emptyList())
    val conversa: StateFlow<List<Mensagem>> = _conversa
    private val _sugestoes = MutableStateFlow(Perguntas.SUGESTOES_INICIAIS)
    val sugestoes: StateFlow<List<String>> = _sugestoes
    private val _pensando = MutableStateFlow(false)
    val pensando: StateFlow<Boolean> = _pensando
    private var respondendo: Job? = null

    fun perguntar(texto: String) {
        val pergunta = texto.trim()
        if (pergunta.isEmpty() || _pensando.value) return
        _conversa.value = _conversa.value + Mensagem(true, pergunta)
        _pensando.value = true
        respondendo = viewModelScope.launch {
            try {
                val resposta = responder(pergunta)
                _conversa.value = _conversa.value + Mensagem(false, resposta.first)
                _sugestoes.value = resposta.second
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _conversa.value = _conversa.value + Mensagem(false, TEXTO_NAO_ENTENDI)
            } finally {
                _pensando.value = false
            }
        }
    }

    private suspend fun responder(texto: String): Pair<String, List<String>> {
        IaFinanceira.iniciar(getApplication())
        val cfg = IaFinanceira.config()
        val vetor = if (cfg == null) null else IaFinanceira.vetorizarPergunta(texto)
        if (cfg == null || vetor == null) return TEXTO_INDISPONIVEL to Perguntas.SUGESTOES_INICIAIS
        val (e, analiseAtual) = ultima ?: return TEXTO_NAO_ENTENDI to Perguntas.SUGESTOES_INICIAIS
        val faturaId = idDaCategoriaFatura(e.categorias)
        val despesas = e.categorias.filter { it.tipo == TipoTransacao.DESPESA && it.id != faturaId }
        val vetoresCategorias = IaFinanceira.vetoresDasCategorias(despesas)
        val hoje = LocalDate.now()
        val entendida: Pergunta = withContext(Dispatchers.Default) {
            Perguntas.entender(texto, vetor, vetoresCategorias, despesas.associate { it.id to it.nome }, e.mes, hoje, cfg.intencoes)
        }
        val pronto = analiseAtual.estado as? EstadoAssistente.Pronto
        val r = RespondedorDePerguntas.responderEmSegundoPlano(
            entendida, e.transacoes, e.categorias, e.recorrentes, e.orcamentos,
            pronto?.recomendacoes.orEmpty(), analiseAtual.atipicos, hoje,
        )
        return r.texto to r.sugestoes
    }

    private companion object {
        const val TEXTO_INDISPONIVEL = "As perguntas não estão disponíveis neste aparelho."
        const val TEXTO_NAO_ENTENDI = "Ainda não sei responder isso."
    }
}
