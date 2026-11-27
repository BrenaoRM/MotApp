package com.example.financacelular.ia.llm

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ia.GoldenIa
import com.example.financacelular.ia.Perguntas
import com.example.financacelular.ia.RespondedorDePerguntas
import com.example.financacelular.ia.llm.EventoResposta.Fim
import com.example.financacelular.ia.llm.EventoResposta.Frase
import com.example.financacelular.ia.llm.EventoResposta.UsarSistemaAtual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AssistenteLlmTest {
    private val contrato = ContratoLlm(GoldenIa.asset("llm/contrato.json").readText(Charsets.UTF_8))
    private val hoje = LocalDate.of(2026, 11, 26)
    private val novembro = YearMonth.of(2026, 11)

    private val categorias = listOf(
        Categoria(1, "Alimentação", TipoTransacao.DESPESA),
        Categoria(2, "Lazer", TipoTransacao.DESPESA),
        Categoria(3, "Fatura", TipoTransacao.DESPESA),
        Categoria(4, "Salário", TipoTransacao.RECEITA),
    )
    private val transacoes = listOf(
        Transacao(id = 1, valorCentavos = 80_000, data = novembro.atDay(10), categoriaId = 1, tipo = TipoTransacao.DESPESA),
        Transacao(id = 2, valorCentavos = 30_000, data = novembro.atDay(12), categoriaId = 2, tipo = TipoTransacao.DESPESA),
        Transacao(id = 3, valorCentavos = 300_000, data = novembro.atDay(5), categoriaId = 4, tipo = TipoTransacao.RECEITA),
    )
    private val dados = DadosDaConversa(transacoes, categorias, emptyList(), emptyList(), emptyList(), emptyList(), novembro, hoje)

    private val planoComposto =
        """{"chamadas":[{"f":"total_mes","mes":"atual"},{"f":"gasto_categoria","mes":"atual","categoria":"Alimentação"},{"f":"receitas","mes":"atual"}]}"""
    private val pergunta = "Quanto gastei esse mês, quanto foi com Alimentação e quanto recebi?"

    private val fraseTotal = "Em novembro você gastou R$ 1.100 no total."
    private val fraseAlimentacao = "Com Alimentação foram R$ 800."
    private val fraseReceita = "Você recebeu R$ 3.000 em novembro."

    /** Frases prontas dos 3 fatos do plano composto, calculadas pelo app. */
    private val prontas = listOf(
        "Em novembro você gastou R$ 1.100.",
        "Em novembro você gastou R$ 800 com Alimentação (73% do total).",
        "Em novembro você recebeu R$ 3.000.",
    )

    /** Respostas roteirizadas: o plano para o prompt com gramática, o texto em pedaços para o da resposta. */
    private class MotorFalso(
        val plano: String,
        val resposta: String = "",
        val tamanhoDoPedaco: Int = 3,
        val esperaAntes: Map<Int, Long> = emptyMap(), // pedaço -> atraso antes dele, em ms (tempo virtual)
        val travaNoFim: Boolean = false,
    ) : MotorDeTexto {
        val prompts = ArrayList<String>()
        var cancelados = 0
        var terminados = 0

        override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, aoPedaco: (String) -> Unit): String {
            prompts += prompt
            if (gramatica != null) return plano
            try {
                resposta.chunked(tamanhoDoPedaco).forEachIndexed { i, p ->
                    esperaAntes[i]?.let { delay(it) }
                    aoPedaco(p)
                }
                if (travaNoFim) awaitCancellation()
                terminados++
                return resposta
            } catch (e: CancellationException) {
                cancelados++
                throw e
            }
        }

        override fun liberar() = Unit
    }

    private fun TestScope.coletar(motor: MotorDeTexto, tempoLimiteMs: Long = 30_000, p: String = pergunta): List<EventoResposta> {
        val eventos = ArrayList<EventoResposta>()
        launch { AssistenteLlm(motor, contrato, tempoLimiteMs).responder(p, emptyList(), dados).toList(eventos) }
        advanceUntilIdle()
        return eventos
    }

    private val sugestoesTotal = RespondedorDePerguntas.SUGESTOES_DEPOIS.getValue(contrato.intencao("total_mes"))

    @Test
    fun `pergunta composta vira 3 chamadas e as frases aprovadas saem na ordem`() = runTest {
        val motor = MotorFalso(planoComposto, "$fraseTotal $fraseAlimentacao $fraseReceita")
        val eventos = coletar(motor)
        assertEquals(
            listOf(Frase(fraseTotal), Frase(fraseAlimentacao), Frase(fraseReceita), Fim(planoComposto, sugestoesTotal)),
            eventos,
        )
        // os 3 fatos foram calculados e estão no prompt da resposta
        prontas.forEach { assertTrue(it, motor.prompts[1].contains(it)) }
    }

    @Test
    fun `categorias do plano sao as de despesa sem Fatura`() = runTest {
        val motor = MotorFalso(planoComposto, fraseTotal)
        coletar(motor)
        val linha = motor.prompts[0].lines().first { it.startsWith("Categorias: ") }
        assertEquals("Categorias: Alimentação, Lazer", linha)
    }

    @Test
    fun `frase com valor trocado corta a geracao e completa com as frases prontas`() = runTest {
        val motor = MotorFalso(planoComposto, "$fraseTotal Com Alimentação foram R$ 900. $fraseReceita", travaNoFim = true)
        val eventos = coletar(motor)
        assertEquals(listOf(Frase(fraseTotal), Frase(prontas[1]), Frase(prontas[2]), Fim(planoComposto, sugestoesTotal)), eventos)
        assertEquals(1, motor.cancelados)
    }

    @Test
    fun `JSON invalido usa o sistema atual`() = runTest {
        val motor = MotorFalso("{\"chamadas\":[{\"f\":")
        assertEquals(listOf(UsarSistemaAtual), coletar(motor))
        assertEquals(1, motor.prompts.size)
    }

    @Test
    fun `plano vazio responde com a recusa`() = runTest {
        val vazio = """{"chamadas":[]}"""
        val motor = MotorFalso(vazio)
        assertEquals(listOf(Frase(contrato.textoRecusa), Fim(vazio, Perguntas.SUGESTOES_INICIAIS)), coletar(motor, p = "Qual a capital da França?"))
        assertEquals(1, motor.prompts.size)
    }

    @Test
    fun `cancelar o collect no meio cancela o motor e nenhum evento sai depois`() = runTest {
        val motor = MotorFalso(planoComposto, "$fraseTotal $fraseAlimentacao $fraseReceita", esperaAntes = mapOf(20 to 5_000L))
        val eventos = ArrayList<EventoResposta>()
        val job = launch { AssistenteLlm(motor, contrato, 30_000).responder(pergunta, emptyList(), dados).toList(eventos) }
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(listOf(Frase(fraseTotal)), eventos)
        job.cancel()
        job.join()
        assertEquals(1, motor.cancelados)
        assertEquals(0, motor.terminados)
        advanceUntilIdle()
        assertEquals(listOf(Frase(fraseTotal)), eventos)
    }

    @Test
    fun `first no collect tambem cancela o motor`() = runTest {
        val motor = MotorFalso(planoComposto, "$fraseTotal $fraseAlimentacao", travaNoFim = true)
        val primeiro = AssistenteLlm(motor, contrato, 30_000).responder(pergunta, emptyList(), dados).first()
        assertEquals(Frase(fraseTotal), primeiro)
        assertEquals(1, motor.cancelados)
    }

    @Test
    fun `mes futuro sem lancamentos passa a frase sem numero inventado`() = runTest {
        val plano = """{"chamadas":[{"f":"total_mes","mes":"2027-12"}]}"""
        val nada = "Não encontrei gastos em dezembro de 2027."
        val sugestoes = sugestoesTotal

        assertEquals(listOf(Frase(nada), Fim(plano, sugestoes)), coletar(MotorFalso(plano, nada), p = "Quanto gastei em dezembro de 2027?"))

        // o modelo inventa um valor: a frase é reprovada e entra a frase pronta
        val inventado = coletar(MotorFalso(plano, "Em dezembro de 2027 você gastou R$ 500."), p = "Quanto gastei em dezembro de 2027?")
        assertEquals(listOf(Frase(nada), Fim(plano, sugestoes)), inventado)
        val semAno = inventado.filterIsInstance<Frase>().joinToString(" ") { it.texto.replace("2027", "") }
        assertFalse(semAno, semAno.any { it.isDigit() })
    }

    @Test
    fun `tempo esgotado depois de 1 frase completa com as frases prontas`() = runTest {
        val motor = MotorFalso(
            planoComposto, "$fraseTotal $fraseAlimentacao $fraseReceita",
            esperaAntes = mapOf(0 to 1_000L, 16 to 20_000L),
        )
        val eventos = coletar(motor, tempoLimiteMs = 10_000)
        assertEquals(listOf(Frase(fraseTotal), Frase(prontas[1]), Frase(prontas[2]), Fim(planoComposto, sugestoesTotal)), eventos)
        assertEquals(1, motor.cancelados)
    }

    @Test
    fun `tempo esgotado antes da primeira frase usa o sistema atual`() = runTest {
        val motor = MotorFalso(planoComposto, "$fraseTotal $fraseAlimentacao", esperaAntes = mapOf(5 to 20_000L))
        assertEquals(listOf(UsarSistemaAtual), coletar(motor, tempoLimiteMs = 10_000))
        assertEquals(1, motor.cancelados)
    }

    @Test
    fun `erro no motor antes da primeira frase usa o sistema atual`() = runTest {
        val motor = object : MotorDeTexto {
            override suspend fun gerar(prompt: String, gramatica: String?, maxTokens: Int, aoPedaco: (String) -> Unit): String =
                if (gramatica != null) planoComposto else throw IllegalStateException("contexto estourado")
            override fun liberar() = Unit
        }
        assertEquals(listOf(UsarSistemaAtual), coletar(motor))
    }

    @Test
    fun `ultima frase sem pontuacao no fim tambem passa`() = runTest {
        val semPonto = fraseReceita.removeSuffix(".")
        val eventos = coletar(MotorFalso(planoComposto, "$fraseTotal $fraseAlimentacao $semPonto"))
        assertEquals(listOf(Frase(fraseTotal), Frase(fraseAlimentacao), Frase(semPonto), Fim(planoComposto, sugestoesTotal)), eventos)
    }

    @Test
    fun `think no comeco e removido`() = runTest {
        val eventos = coletar(MotorFalso(planoComposto, "<think>\nR$ 999. Pensando.\n</think>\n\n$fraseTotal $fraseAlimentacao $fraseReceita"))
        assertEquals(listOf(Frase(fraseTotal), Frase(fraseAlimentacao), Frase(fraseReceita), Fim(planoComposto, sugestoesTotal)), eventos)
    }

    @Test
    fun `think sem fim nao produz frase e entram as prontas`() = runTest {
        val eventos = coletar(MotorFalso(planoComposto, "<think>\nR$ 999. Pensando. Mais."))
        assertEquals(prontas.map { Frase(it) } + Fim(planoComposto, sugestoesTotal), eventos)
    }

    @Test
    fun `segmentacao nao corta valor nem abreviacao`() {
        assertNull(AssistenteLlm.proximaFrase("Você gastou R$ 1.234,56.", 0))
        assertEquals("Você gastou R$ 1.234,56." to 24, AssistenteLlm.proximaFrase("Você gastou R$ 1.234,56. Depois", 0))
        assertEquals(
            "Foram aprox. R$ 300 com Lazer." to 30,
            AssistenteLlm.proximaFrase("Foram aprox. R$ 300 com Lazer. E mais", 0),
        )
        assertEquals("Linha um" to 9, AssistenteLlm.proximaFrase("Linha um\nLinha dois", 0))
        assertNull(AssistenteLlm.semPensamento("  <thi"))
        assertEquals("Oi.", AssistenteLlm.semPensamento("<think>x</think>Oi."))
    }
}
