package com.example.financacelular.ia

import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AtalhosTest {
    private val hoje = LocalDate.of(2026, 11, 28)
    private val dados = listOf(
        Transacao(id = 1, valorCentavos = 200_000, data = hoje, categoriaId = 1, tipo = TipoTransacao.RECEITA),
        Transacao(id = 2, valorCentavos = 300_000, data = hoje, categoriaId = 2, tipo = TipoTransacao.DESPESA),
    )

    private fun r(texto: String) = Atalhos.responder(texto, dados, emptyList(), YearMonth.of(2026, 11), hoje)

    @Test
    fun `cumprimento tem resposta`() {
        listOf("oi", "Oi tudo bem com você?", "olá, bom dia!").forEach { assertTrue(it, r(it)!!.texto.startsWith("Oi!")) }
    }

    @Test
    fun `perguntas sobre os dados seguem para o assistente`() {
        listOf(
            "oi, quanto gastei este mês?", "Com o que eu mais gastei em comida e qual dia?", "como posso economizar",
            "quantos % da comida foi no mercado", "quanto custou o botijão",
        ).forEach { assertNull(it, r(it)) }
    }

    @Test
    fun `assunto fora de financas e recusado`() {
        listOf("qual a capital da frança?", "como se faz um bolo de cenoura", "escreve um poema sobre o mar").forEach {
            assertTrue(it, r(it)!!.texto.startsWith("Só consigo ajudar"))
        }
    }

    @Test
    fun `registra gasto com e sem valor`() {
        assertEquals(GastoPedido("Cachorro quente", null), r("Adiciona um gasto de cachorro quente")!!.gasto)
        assertEquals(GastoPedido("Cachorro quente", 1250), r("adiciona um gasto de cachorro quente de R$ 12,50")!!.gasto)
        assertEquals(GastoPedido("Mercado", 120_000), r("anota 1.200 reais no mercado")!!.gasto)
        assertEquals(GastoPedido("2 pastéis", 1800), r("lança 2 pastéis por 18 reais")!!.gasto)
    }

    @Test
    fun `porcentagem compara gasto e renda`() {
        val esperado = "Em novembro você gastou 150% do que recebeu: 50% a mais do que recebeu."
        assertEquals(esperado, r("quantos % eu gasto a mais do que eu ganho nesse mês?")!!.texto)
        assertEquals(esperado, r("e isso é quantos em porcentagem")!!.texto)
    }

    @Test
    fun `investimento com conta negativa manda ajustar antes`() {
        assertTrue(r("quanto devo investir todo mês?")!!.texto.contains("Antes de investir"))
    }
}
