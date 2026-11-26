package com.example.financacelular.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizadorTest {
    private val tokenizador = GoldenIa.tokenizador()
    private val casos = GoldenIa.array("golden_tokens.json")

    @Test
    fun normalizacaoIgualAoPython() {
        assertEquals(200, casos.length())
        for (i in 0 until casos.length()) {
            val c = casos.getJSONObject(i)
            assertEquals(
                "bruto=${c.getString("bruto")}",
                c.getString("normalizado"),
                NormalizacaoTexto.paraModelo(c.getString("bruto")),
            )
        }
    }

    @Test
    fun idsIguaisAoPython() {
        for (i in 0 until casos.length()) {
            val c = casos.getJSONObject(i)
            val esperado = c.getJSONArray("ids").let { a -> List(a.length()) { a.getInt(it) } }
            assertEquals(
                "normalizado=${c.getString("normalizado")}",
                esperado,
                tokenizador.codificar(c.getString("normalizado"), 64).toList(),
            )
        }
    }

    @Test
    fun truncaEmMaxTokens() {
        val texto = (1..500).joinToString(" ") { "palavra$it" }
        val ids = tokenizador.codificar(texto, 64)
        assertEquals(64, ids.size)
        assertEquals(0, ids.first())
        assertEquals(2, ids.last())
    }

    @Test
    fun textoVazioViraSoEspeciais() {
        assertEquals(listOf(0, 2), tokenizador.codificar("", 64).toList())
        assertTrue(NormalizacaoTexto.paraModelo(null).isEmpty())
    }
}
