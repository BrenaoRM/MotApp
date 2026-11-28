package com.example.financacelular.ia.llm

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.data.idDaCategoriaFatura
import com.example.financacelular.ia.Atipico
import com.example.financacelular.ia.Intencao
import com.example.financacelular.ia.Pergunta
import com.example.financacelular.ia.Perguntas
import com.example.financacelular.ia.Recomendacao
import com.example.financacelular.ia.RespondedorDePerguntas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Tudo o que as ferramentas precisam para calcular os fatos de uma pergunta. */
data class DadosDaConversa(
    val transacoes: List<Transacao>,
    val categorias: List<Categoria>,
    val recorrentes: List<DespesaRecorrente>,
    val orcamentos: List<Orcamento>,
    val recomendacoes: List<Recomendacao>,
    val atipicos: List<Atipico>,
    val mesAtual: YearMonth,
    val hoje: LocalDate,
)

sealed interface EventoResposta {
    /** Já verificada; a tela acrescenta. */
    data class Frase(val texto: String) : EventoResposta

    data class Fim(val planoJson: String, val sugestoes: List<String>) : EventoResposta

    /** Nada foi mostrado ainda: a tela responde pelo sistema atual. */
    data object UsarSistemaAtual : EventoResposta
}

/**
 * Pipeline da IA avançada: o LLM planeja as chamadas (JSON com gramática), o app calcula os [Fato]s e o LLM escreve.
 * Cada frase só sai depois de passar no [Verificador]; a primeira reprovada corta a geração e os fatos ainda não citados
 * entram com a frase pronta. Se nada foi mostrado e algo falhou (plano inválido, erro, [tempoLimiteMs] esgotado),
 * sai [EventoResposta.UsarSistemaAtual].
 *
 * O flow é frio: cancelar o collect cancela o `motor.gerar` em andamento, e nenhum evento sai depois.
 */
class AssistenteLlm(private val motor: MotorDeTexto, private val contrato: ContratoLlm, private val tempoLimiteMs: Long) {

    /** O que a geração (coroutine filha) passa para quem emite os eventos. */
    private sealed interface Interno {
        class Plano(val json: String, val chamadas: List<Chamada>, val fatos: List<Fato>) : Interno
        class Pedaco(val texto: String) : Interno
        data object Terminou : Interno
    }

    fun responder(pergunta: String, historico: List<Pair<String, String>>, dados: DadosDaConversa): Flow<EventoResposta> = flow {
        coroutineScope {
            val canal = Channel<Interno>(Channel.UNLIMITED)
            val geracao = launch {
                try {
                    withTimeoutOrNull(tempoLimiteMs) { gerar(pergunta, historico, dados, canal) }
                } finally {
                    canal.close() // fechar sem Terminou = falha ou tempo esgotado
                }
            }

            var plano: Interno.Plano? = null
            var terminou = false
            var interrompida = false
            val bruto = StringBuilder()
            var consumido = 0 // quanto do texto visível já virou frase
            var periodo: String? = null
            val aprovadas = ArrayList<String>()
            val emitidas = HashSet<String>() // frases já mostradas, normalizadas

            leitura@ for (m in canal) {
                when (m) {
                    is Interno.Plano -> {
                        plano = m
                        if (m.chamadas.isEmpty()) break@leitura
                    }
                    is Interno.Pedaco -> {
                        val fatos = plano?.fatos ?: continue@leitura
                        bruto.append(m.texto)
                        val visivel = semPensamento(bruto.toString()) ?: continue@leitura
                        while (true) {
                            val (frase, fim) = proximaFrase(visivel, consumido) ?: break
                            consumido = fim
                            val (ok, novo) = Verificador.verificarFrase(frase, fatos, periodo)
                            if (!ok) {
                                interrompida = true
                                break@leitura
                            }
                            periodo = novo
                            if (!emitidas.add(normalizada(frase))) continue // repetida: não conta para o limite
                            aprovadas += frase
                            emit(EventoResposta.Frase(frase))
                            if (aprovadas.size >= maxFrases(fatos)) {
                                interrompida = true
                                break@leitura
                            }
                        }
                    }
                    Interno.Terminou -> terminou = true
                }
            }
            geracao.cancel()

            val p = plano
            when {
                p == null -> emit(EventoResposta.UsarSistemaAtual)
                p.chamadas.isEmpty() -> {
                    emit(EventoResposta.Frase(contrato.textoRecusa))
                    emit(EventoResposta.Fim(p.json, Perguntas.SUGESTOES_INICIAIS))
                }
                aprovadas.isEmpty() && !terminou && !interrompida -> emit(EventoResposta.UsarSistemaAtual)
                else -> {
                    if (terminou && !interrompida) {
                        // A última frase só passa com .!? no fim: o motor não diz se parou no limite de tokens, e
                        // frase sem pontuação pode ter sido cortada no meio (o fato dela entra com a frase pronta).
                        val resto = semPensamento(bruto.toString())?.substring(consumido)?.trim()
                        if (!resto.isNullOrEmpty() && resto.last() in ".!?" && aprovadas.size < maxFrases(p.fatos) &&
                            Verificador.verificarFrase(resto, p.fatos, periodo).first && emitidas.add(normalizada(resto))
                        ) {
                            aprovadas += resto
                            emit(EventoResposta.Frase(resto))
                        }
                    }
                    for (f in fatosNaoCitados(aprovadas, p.fatos)) if (emitidas.add(normalizada(f.frase))) emit(EventoResposta.Frase(f.frase))
                    val sugestoes = RespondedorDePerguntas.SUGESTOES_DEPOIS[contrato.intencao(p.chamadas.first().ferramenta)]
                        ?: Perguntas.SUGESTOES_INICIAIS
                    emit(EventoResposta.Fim(p.json, sugestoes))
                }
            }
        }
    }

    /** Roda na coroutine filha: plano, fatos e o texto em pedaços. Erro (fora cancelamento) só fecha o canal. */
    private suspend fun gerar(pergunta: String, historico: List<Pair<String, String>>, dados: DadosDaConversa, canal: Channel<Interno>) {
        try {
            val faturaId = idDaCategoriaFatura(dados.categorias)
            val despesas = dados.categorias.filter { it.tipo == TipoTransacao.DESPESA && it.id != faturaId }
            val nomes = despesas.map { it.nome }
            val idsParaNomes = despesas.associate { it.id to it.nome }
            suspend fun planejar(hist: List<Pair<String, String>>): Pair<String, List<Chamada>?> {
                val j = motor.gerar(
                    contrato.promptPlano(pergunta, dados.mesAtual, nomes, hist),
                    contrato.gramaticaPlano(dados.mesAtual, nomes), MAX_TOKENS_PLANO, contrato.prefixoPlano,
                ) {}.trim()
                return j to contrato.lerPlano(j, dados.mesAtual, idsParaNomes)
            }
            // Só pergunta com cara de continuação recebe o histórico: com ele, o modelo trata qualquer pergunta como
            // continuação e cola fatos da conversa anterior numa pergunta fora do escopo
            val (json, lido) = planejar(if (ehContinuacao(pergunta)) historico else emptyList())
            val chamadas = lido ?: return
            // lista vazia porque todas as chamadas foram descartadas não é recusa: sistema atual
            if (chamadas.isEmpty() && JSONObject(json).getJSONArray("chamadas").length() > 0) return
            val fatos = chamadas.distinct().map { c ->
                RespondedorDePerguntas.fato(
                    Pergunta(contrato.intencao(c.ferramenta), 1f, c.mes ?: dados.mesAtual, c.categoriaId),
                    dados.transacoes, dados.categorias, dados.recorrentes, dados.orcamentos, dados.recomendacoes,
                    dados.atipicos, dados.hoje,
                )
            }
            canal.send(Interno.Plano(json, chamadas, fatos))
            if (chamadas.isEmpty()) return
            // aoPedaco roda na thread do motor: trySend no canal sem limite nunca bloqueia
            // dois fatos sem itens com a mesma frase (ex.: dois meses sem dados) entram uma vez só no prompt
            val fatosDoPrompt = fatos.filterIndexed { n, f -> f.itens.isNotEmpty() || fatos.indexOfFirst { it.itens.isEmpty() && it.frase == f.frase } == n }
            motor.gerar(contrato.promptResposta(pergunta, fatosDoPrompt), null, MAX_TOKENS_RESPOSTA, contrato.prefixoResposta) { canal.trySend(Interno.Pedaco(it)) }
            canal.send(Interno.Terminou)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // cai no sistema atual (nada mostrado) ou nas frases prontas
        }
    }

    companion object {
        private const val MAX_TOKENS_PLANO = 160
        private const val MAX_TOKENS_RESPOSTA = 256

        /** "No máximo 4 frases curtas e 1 conselho se houver fato de economia" (sistema_resposta; igual ao gerar_respostas.py). */
        private const val MAX_FRASES = 4

        private val ABREVIACOES = setOf("sr", "sra", "dr", "dra", "prof", "aprox", "obs")

        private val PREFIXOS_CONTINUACAO = listOf("ah", "beleza", "ok", "valeu", "show", "boa", "certo", "entao", "opa")
        private val INICIOS_CONTINUACAO = listOf(
            "e ", "agora ", "mesma coisa", "o mesmo", "mesmo ", "troca ", "como foi ", "como ficou ", "me mostra ", "pode ver ", "vê ", "ve ",
        )
        private val ANAFORAS = setOf("isso", "disso", "nisso", "nesse", "nessa", "desse", "dessa", "dele", "dela", "mesmo", "mesma")

        /**
         * A pergunta parece continuar a anterior ("e em outubro?", "e com Lazer?", "quanto foi isso em setembro?")? Só então o
         * histórico vai para o plano. Muito curta (até 2 palavras), começando como continuação ou com anáfora.
         */
        private val ACENTOS = Regex("""\p{Mn}+""")

        private fun semAcento(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(ACENTOS, "")

        internal fun ehContinuacao(pergunta: String): Boolean {
            var t = semAcento(pergunta.lowercase()).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("""\s+"""), " ").trim()
            for (p in PREFIXOS_CONTINUACAO) if (t.startsWith("$p ")) { t = t.removePrefix("$p ").trim(); break }
            val palavras = t.split(' ').filter { it.isNotEmpty() }
            if (palavras.isEmpty()) return false
            if (palavras.size <= 2) return true
            if (INICIOS_CONTINUACAO.any { "$t ".startsWith(semAcento(it)) }) return true
            return palavras.any { it in ANAFORAS }
        }

        /** Para comparar frases repetidas: sem espaços nas pontas, em minúsculas e sem a pontuação final. */
        internal fun normalizada(frase: String) = frase.trim().lowercase().trimEnd('.', '!', '?', ' ')

        private fun maxFrases(fatos: List<Fato>): Int =
            MAX_FRASES + if (fatos.any { it.ferramenta == Intencao.COMO_ECONOMIZAR && it.itens.isNotEmpty() }) 1 else 0

        /**
         * Tira um `<think>…</think>` do começo. null = ainda sem texto visível (pensamento sem fim, ou o começo
         * ainda pode ser `<think>`).
         */
        internal fun semPensamento(texto: String): String? {
            val s = texto.trimStart()
            if (s.length < ABRE.length && ABRE.startsWith(s)) return null
            if (!s.startsWith(ABRE)) return texto
            val fim = s.indexOf(FECHA)
            return if (fim < 0) null else s.substring(fim + FECHA.length)
        }

        private const val ABRE = "<think>"
        private const val FECHA = "</think>"

        /**
         * A próxima frase completa de [texto] a partir de [inicio], e onde ela termina; null se ainda não há uma.
         * Mesmo corte do [Verificador.frases] (.!? seguido de espaço, ou quebra de linha), mas só quando o espaço já
         * chegou: "R$ 1.234,56" nunca corta, e abreviação ("aprox. R$ 300") não fecha frase.
         */
        internal fun proximaFrase(texto: String, inicio: Int): Pair<String, Int>? {
            var comeco = inicio
            var i = inicio
            while (i < texto.length) {
                val c = texto[i]
                val corte = when {
                    c == '\n' || c == '\r' -> i
                    (c == '.' || c == '!' || c == '?') && i + 1 < texto.length && texto[i + 1].isWhitespace() &&
                        !(c == '.' && abreviacao(texto, comeco, i)) -> i + 1
                    else -> -1
                }
                if (corte >= 0) {
                    val frase = texto.substring(comeco, corte).trim()
                    val fim = i + 1
                    if (frase.isNotEmpty()) return frase to fim
                    comeco = fim
                }
                i++
            }
            return null
        }

        private fun abreviacao(texto: String, comeco: Int, ponto: Int): Boolean {
            var j = ponto
            while (j > comeco && texto[j - 1].isLetter()) j--
            return texto.substring(j, ponto).lowercase() in ABREVIACOES
        }

        /** Fatos que nenhuma frase aprovada citou, na ordem do plano. Fato sem itens conta como citado se a frase dele saiu igual. */
        private fun fatosNaoCitados(aprovadas: List<String>, fatos: List<Fato>): List<Fato> {
            val citados = Verificador.fatosCitados(aprovadas, fatos)
            val ditas = aprovadas.map { normalizada(it) }.toSet()
            return fatos.filterIndexed { n, f ->
                n !in citados && !(f.itens.isEmpty() && Verificador.frases(f.frase).all { normalizada(it) in ditas })
            }
        }
    }
}
