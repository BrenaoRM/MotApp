package com.example.financacelular.ui

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ia.GoldenIa
import com.example.financacelular.ia.Mensagem
import com.example.financacelular.ia.llm.AssistenteLlm
import com.example.financacelular.ia.llm.ContratoLlm
import com.example.financacelular.ia.llm.DadosDaConversa
import com.example.financacelular.ia.llm.MotorDeTexto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.EmptyCoroutineContext

class ConversaDoAssistenteTest {
    private val contrato = ContratoLlm(GoldenIa.asset("llm/contrato.json").readText(Charsets.UTF_8))
    private val novembro = YearMonth.of(2026, 11)
    private val categorias = listOf(
        Categoria(1, "Alimentação", TipoTransacao.DESPESA),
        Categoria(2, "Lazer", TipoTransacao.DESPESA),
        Categoria(4, "Salário", TipoTransacao.RECEITA),
    )
    private val transacoes = listOf(
        Transacao(id = 1, valorCentavos = 80_000, data = novembro.atDay(10), categoriaId = 1, tipo = TipoTransacao.DESPESA),
        Transacao(id = 2, valorCentavos = 30_000, data = novembro.atDay(12), categoriaId = 2, tipo = TipoTransacao.DESPESA),
        Transacao(id = 3, valorCentavos = 300_000, data = novembro.atDay(5), categoriaId = 4, tipo = TipoTransacao.RECEITA),
    )
    private val dados = DadosDaConversa(
        transacoes, categorias, emptyList(), emptyList(), emptyList(), emptyList(), novembro, LocalDate.of(2026, 11, 26),
    )

    private val plano = """{"chamadas":[{"f":"total_mes","mes":"atual"},{"f":"gasto_categoria","mes":"atual","categoria":"Alimentação"}]}"""
    private val fraseTotal = "Em novembro você gastou R$ 1.100 no total."
    private val fraseAlimentacao = "Com Alimentação foram R$ 800."

    private class MotorFalso(val plano: String, val resposta: String = "", val portao: CompletableDeferred<Unit>? = null) : MotorDeTexto {
        var geracoes = 0
        override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, prefixo: String, aoPedaco: (String) -> Unit): String {
            geracoes++
            if (gramatica != null) return plano
            portao?.await()
            resposta.chunked(3).forEach(aoPedaco)
            return resposta
        }

        override fun liberar() = Unit
    }

    private fun conversa(scope: CoroutineScope, motor: MotorDeTexto?) = ConversaDoAssistente(
        scope = scope,
        llm = { motor?.let { AssistenteLlm(it, contrato, 30_000) } },
        dados = { dados },
        sistemaAtual = { "do sistema atual" to listOf("outra") },
        textoNaoEntendi = "não entendi",
        contexto = EmptyCoroutineContext,
    )

    private suspend fun aguardar(c: ConversaDoAssistente) {
        c.pensando.first { !it }
    }

    @Test
    fun `as frases sao anexadas a mesma mensagem e pensando fica ate o fim`() = runTest {
        val portao = CompletableDeferred<Unit>()
        val c = conversa(backgroundScope, MotorFalso(plano, "$fraseTotal $fraseAlimentacao", portao))
        c.perguntar("Quanto gastei, e com Alimentação?")
        advanceUntilIdle()
        assertTrue(c.pensando.value)
        assertEquals(listOf(Mensagem(true, "Quanto gastei, e com Alimentação?")), c.conversa.value)
        portao.complete(Unit)
        c.pensando.first { !it }
        assertEquals(2, c.conversa.value.size)
        assertEquals(Mensagem(false, "$fraseTotal $fraseAlimentacao"), c.conversa.value[1])
        assertFalse(c.pensando.value)
    }

    @Test
    fun `plano invalido usa o sistema atual`() = runTest {
        val c = conversa(backgroundScope, MotorFalso("{\"chamadas\":[{\"f\":"))
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(listOf(Mensagem(true, "Quanto gastei?"), Mensagem(false, "do sistema atual")), c.conversa.value)
        assertEquals(listOf("outra"), c.sugestoes.value)
        assertFalse(c.pensando.value)
    }

    @Test
    fun `sem IA avancada pronta usa o sistema atual`() = runTest {
        val c = conversa(backgroundScope, null)
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(Mensagem(false, "do sistema atual"), c.conversa.value.last())
    }

    @Test
    fun `Error do motor antes de mostrar algo usa o sistema atual`() = runTest {
        val motor = object : MotorDeTexto {
            override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, prefixo: String, aoPedaco: (String) -> Unit): String =
                throw UnsatisfiedLinkError("sem libmotor_llm")

            override fun liberar() = Unit
        }
        val c = conversa(backgroundScope, motor)
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(Mensagem(false, "do sistema atual"), c.conversa.value.last())
        assertFalse(c.pensando.value)
    }

    @Test
    fun `pergunta durante a geracao e ignorada`() = runTest {
        val portao = CompletableDeferred<Unit>()
        val motor = MotorFalso(plano, fraseTotal, portao)
        val c = conversa(backgroundScope, motor)
        c.perguntar("Quanto gastei?")
        advanceUntilIdle()
        c.perguntar("E o Lazer?")
        advanceUntilIdle()
        assertEquals(1, c.conversa.value.size)
        portao.complete(Unit)
        c.pensando.first { !it }
        assertEquals(2, c.conversa.value.size)
        assertEquals(2, motor.geracoes) // plano e resposta de uma só pergunta
    }

    @Test
    fun `o historico guarda so as 2 ultimas trocas`() = runTest {
        val c = conversa(backgroundScope, MotorFalso(plano, fraseTotal))
        repeat(3) {
            c.perguntar("pergunta $it")
            aguardar(c)
        }
        assertEquals(listOf("pergunta 1", "pergunta 2"), c.historicoParaTeste().map { it.first })
    }

    private fun conversaCom(scope: CoroutineScope, llm: suspend () -> AssistenteLlm?, dados: () -> DadosDaConversa? = { this.dados }) =
        ConversaDoAssistente(
            scope = scope,
            llm = llm,
            dados = dados,
            sistemaAtual = { "do sistema atual" to listOf("outra") },
            textoNaoEntendi = "não entendi",
            contexto = EmptyCoroutineContext,
        )

    @Test
    fun `excecao ao obter o pipeline usa o sistema atual`() = runTest {
        val c = conversaCom(backgroundScope, { throw java.io.IOException("disco") })
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(Mensagem(false, "do sistema atual"), c.conversa.value.last())
    }

    @Test
    fun `Error ao obter os dados usa o sistema atual`() = runTest {
        val c = conversaCom(backgroundScope, { AssistenteLlm(MotorFalso(plano, fraseTotal), contrato, 30_000) }, { throw UnsatisfiedLinkError("x") })
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(Mensagem(false, "do sistema atual"), c.conversa.value.last())
        assertEquals(2, c.conversa.value.size)
    }

    @Test
    fun `motor sem texto ainda deixa uma resposta na conversa`() = runTest {
        // o AssistenteLlm completa com as frases prontas; o Fim sem frase (guarda na ConversaDoAssistente) nao da para forcar com a classe real
        val vazio = object : MotorDeTexto {
            override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, prefixo: String, aoPedaco: (String) -> Unit): String = ""
            override fun liberar() = Unit
        }
        val c = conversaCom(backgroundScope, { AssistenteLlm(vazio, contrato, 30_000) })
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(2, c.conversa.value.size)
        assertFalse(c.conversa.value.last().deUsuario)
        assertTrue(c.conversa.value.last().texto.isNotBlank())
    }

    @Test
    fun `erro depois de uma frase mantem a resposta parcial`() = runTest {
        val motor = object : MotorDeTexto {
            override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, prefixo: String, aoPedaco: (String) -> Unit): String {
                if (gramatica != null) return plano
                "$fraseTotal ".chunked(3).forEach(aoPedaco)
                throw IllegalStateException("falha no meio")
            }

            override fun liberar() = Unit
        }
        val c = conversa(backgroundScope, motor)
        c.perguntar("Quanto gastei?")
        aguardar(c)
        assertEquals(2, c.conversa.value.size)
        assertTrue(c.conversa.value.last().texto.startsWith(fraseTotal))
    }
}
