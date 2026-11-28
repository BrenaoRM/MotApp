package com.example.financacelular.ui

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import com.example.financacelular.ia.GastoPedido
import com.example.financacelular.ia.IaFinanceira
import com.example.financacelular.ia.Mensagem
import com.example.financacelular.ia.Pergunta
import com.example.financacelular.ia.Perguntas
import com.example.financacelular.ia.RespondedorDePerguntas
import com.example.financacelular.ia.llm.AssistenteLlm
import com.example.financacelular.ia.llm.ContratoLlm
import com.example.financacelular.ia.llm.DadosDaConversa
import com.example.financacelular.ia.llm.EstadoModelo
import com.example.financacelular.ia.llm.GerenciadorDeModelo
import com.example.financacelular.ia.llm.ModelosIa
import com.example.financacelular.ia.llm.MotorLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

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

    private val conversaDoAssistente = ConversaDoAssistente(
        scope = viewModelScope,
        llm = ::assistenteLlm,
        dados = ::dadosDaConversa,
        sistemaAtual = ::responder,
        textoNaoEntendi = TEXTO_NAO_ENTENDI,
    )
    val conversa: StateFlow<List<Mensagem>> = conversaDoAssistente.conversa
    val pensando: StateFlow<Boolean> = conversaDoAssistente.pensando

    fun perguntar(texto: String) = conversaDoAssistente.perguntar(texto)

    /** Categoria para um gasto anotado no chat: a usada antes nessa descrição ou, sem ela, a da IA. */
    suspend fun categoriaDoGasto(descricao: String): Categoria? {
        if (descricao.isBlank()) return null
        IaFinanceira.iniciar(getApplication())
        val memoria = repository.sugerirCategoria(descricao, TipoTransacao.DESPESA)
        return escolherSugestao(memoria, IaFinanceira.sugerir(descricao, TipoTransacao.DESPESA), categorias.value, null)
    }

    /** Grava o gasto confirmado no chat, uma vez só. */
    fun registrarGasto(indice: Int, transacao: Transacao) {
        val gravado = GastoPedido(transacao.descricao.orEmpty(), transacao.valorCentavos)
        if (!conversaDoAssistente.marcarRegistrado(indice, gravado)) return
        // Como na Nova Transação: sem descrição, vale o nome da categoria
        val nome = categorias.value.firstOrNull { it.id == transacao.categoriaId }?.nome
        val mes = String.format(Locale.ROOT, "%04d-%02d", transacao.data.year, transacao.data.monthValue)
        viewModelScope.launch {
            repository.salvarTransacao(transacao.copy(descricao = transacao.descricao ?: nome, anoMes = transacao.anoMes ?: mes))
        }
    }

    // ---------------------------------------------------------------- IA avançada (LLM local)

    private val travaDoMotor = Mutex()
    private var motor: MotorLlm? = null
    private var caminhoDoMotor: String? = null
    private var caminhoQueFalhou: String? = null
    private val contratoLlm by lazy { ContratoLlm.doAsset(getApplication()) }
    private val fechamento = Any()
    @Volatile private var encerrado = false

    private val aoFaltarMemoria = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                viewModelScope.launch(Dispatchers.Default) { travaDoMotor.withLock { liberarMotor() } }
            }
        }
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        @Deprecated("Sem uso", ReplaceWith("Unit"))
        override fun onLowMemory() = Unit
    }

    init {
        // O estado do download nasce Desativado a cada processo: reconstrói do WorkManager e do disco
        GerenciadorDeModelo.restaurar(application)
        application.registerComponentCallbacks(aoFaltarMemoria)
        // Desativar, baixar de novo ou trocar de modo dá outra chance ao motor que tinha falhado (ex.: falta de memória passageira)
        viewModelScope.launch {
            GerenciadorDeModelo.estado.collect { if (it !is EstadoModelo.Pronto) travaDoMotor.withLock { caminhoQueFalhou = null } }
        }
    }

    private fun liberarMotor() {
        motor?.liberar()
        motor = null
        caminhoDoMotor = null
    }

    /** O pipeline do modelo ativo, carregando o motor na primeira pergunta; null = sem modelo pronto ou o motor não abriu. */
    private suspend fun assistenteLlm(): AssistenteLlm? = withContext(Dispatchers.Default) {
        travaDoMotor.withLock {
            val app = getApplication<Application>()
            val caminho = GerenciadorDeModelo.caminhoPronto(app)
            if (caminho == null || encerrado) {
                liberarMotor()
                return@withLock null
            }
            if (caminhoDoMotor != caminho) liberarMotor()
            if (caminho == caminhoQueFalhou) return@withLock null
            val atual = motor ?: try {
                MotorLlm.carregar(caminho)
            } catch (e: Throwable) {
                null // UnsatisfiedLinkError, OutOfMemoryError…: cai no sistema atual
            }
            if (atual == null) {
                caminhoQueFalhou = caminho
                return@withLock null
            }
            // Checar e guardar juntos, sob o mesmo lock do onCleared: o motor carregado nunca fica sem dono
            synchronized(fechamento) {
                if (encerrado) {
                    atual.liberar()
                    return@withLock null
                }
                motor = atual
                caminhoDoMotor = caminho
            }
            val modelo = ModelosIa.disponiveis.firstOrNull { GerenciadorDeModelo.caminhoPronto(GerenciadorDeModelo.pasta(app), it) == caminho }
            AssistenteLlm(atual, contratoLlm, modelo?.tempoLimiteMs ?: TEMPO_LIMITE_PADRAO_MS)
        }
    }

    private fun dadosDaConversa(): DadosDaConversa? {
        val (e, analise) = ultima ?: return null
        val pronto = analise.estado as? EstadoAssistente.Pronto
        return DadosDaConversa(
            e.transacoes, e.categorias, e.recorrentes, e.orcamentos,
            pronto?.recomendacoes.orEmpty(), analise.atipicos, e.mes, LocalDate.now(),
        )
    }

    override fun onCleared() {
        getApplication<Application>().unregisterComponentCallbacks(aoFaltarMemoria)
        synchronized(fechamento) {
            encerrado = true
            motor?.liberar()
            motor = null
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
        const val TEMPO_LIMITE_PADRAO_MS = 60_000L
    }
}
