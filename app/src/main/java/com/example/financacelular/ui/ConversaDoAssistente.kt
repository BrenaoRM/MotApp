package com.example.financacelular.ui

import com.example.financacelular.ia.Atalhos
import com.example.financacelular.ia.GastoPedido
import com.example.financacelular.ia.Mensagem
import com.example.financacelular.ia.Perguntas
import com.example.financacelular.ia.llm.AssistenteLlm
import com.example.financacelular.ia.llm.DadosDaConversa
import com.example.financacelular.ia.llm.EventoResposta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Conversa da aba Assistente. Com a IA avançada pronta, as frases verificadas do [AssistenteLlm] vão sendo anexadas à mesma
 * mensagem de resposta; sem ela (ou se falhar antes de mostrar algo), responde o [sistemaAtual]. Só uma pergunta por vez.
 * O histórico (pergunta, plano) das 2 últimas trocas fica só em memória.
 */
class ConversaDoAssistente(
    private val scope: CoroutineScope,
    /** null = IA avançada indisponível agora (sem modelo, sem suporte ou falha ao carregar). */
    private val llm: suspend () -> AssistenteLlm?,
    private val dados: () -> DadosDaConversa?,
    private val sistemaAtual: suspend (String) -> Pair<String, List<String>>,
    private val textoNaoEntendi: String,
    private val contexto: CoroutineContext = Dispatchers.Default,
) {
    private val _conversa = MutableStateFlow<List<Mensagem>>(emptyList())
    val conversa: StateFlow<List<Mensagem>> = _conversa
    private val _sugestoes = MutableStateFlow(Perguntas.SUGESTOES_INICIAIS)
    val sugestoes: StateFlow<List<String>> = _sugestoes
    private val _pensando = MutableStateFlow(false)
    val pensando: StateFlow<Boolean> = _pensando
    private val historico = ArrayList<Pair<String, String>>()

    internal fun historicoParaTeste(): List<Pair<String, String>> = historico.toList()

    fun perguntar(texto: String) {
        val pergunta = texto.trim()
        if (pergunta.isEmpty() || _pensando.value) return
        _conversa.value = _conversa.value + Mensagem(true, pergunta)
        _pensando.value = true
        scope.launch {
            try {
                responder(pergunta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                colocar(textoNaoEntendi, anexar = false)
            } finally {
                _pensando.value = false
            }
        }
    }

    /** Marca o cartão da mensagem [indice] como gravado; false = não há gasto ali ou ele já foi gravado. */
    fun marcarRegistrado(indice: Int, gravado: GastoPedido): Boolean {
        val m = _conversa.value.getOrNull(indice)
        if (m?.gasto == null || m.registrado) return false
        _conversa.value = _conversa.value.toMutableList().also { it[indice] = m.copy(gasto = gravado, registrado = true) }
        return true
    }

    private suspend fun responder(pergunta: String) {
        // Falha ao ler os dados não derruba a pergunta: segue o caminho normal, que trata o erro
        val atalho = runCatching { dados()?.let { Atalhos.responder(pergunta, it.transacoes, it.categorias, it.mesAtual, it.hoje) } }.getOrNull()
        if (atalho != null) {
            _conversa.value = _conversa.value + Mensagem(false, atalho.texto, atalho.gasto)
            return
        }
        var mostrou = false
        try {
            val assistente = llm()
            val d = dados()
            if (assistente != null && d != null) {
                assistente.responder(pergunta, historico.toList(), d).flowOn(contexto).collect { evento ->
                    when (evento) {
                        is EventoResposta.Frase -> {
                            colocar(evento.texto, anexar = mostrou)
                            mostrou = true
                        }
                        is EventoResposta.Fim -> if (mostrou) {
                            historico += pergunta to evento.planoJson
                            while (historico.size > 2) historico.removeAt(0)
                            _sugestoes.value = evento.sugestoes
                        }
                        EventoResposta.UsarSistemaAtual -> Unit
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Exception ou Error (UnsatisfiedLinkError, OutOfMemoryError, E/S): o que já saiu fica; senão responde o sistema atual
        }
        if (mostrou) return
        val (texto, sugestoes) = sistemaAtual(pergunta)
        colocar(texto, anexar = false)
        _sugestoes.value = sugestoes
    }

    /** [anexar] = a resposta desta pergunta já existe (é a última mensagem): a frase entra no fim dela. */
    private fun colocar(texto: String, anexar: Boolean) {
        val lista = _conversa.value
        _conversa.value =
            if (anexar) lista.dropLast(1) + Mensagem(false, lista.last().texto + " " + texto) else lista + Mensagem(false, texto)
    }
}
