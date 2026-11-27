package com.example.financacelular.ia.llm

import com.example.financacelular.ia.GoldenIa
import com.example.financacelular.ia.Intencao
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mesmos casos do ia/llm/test_verificador.py: os dois verificadores precisam concordar. */
class VerificadorTest {
    private val casos = GoldenIa.recurso("llm/casos_verificador.jsonl").lines().filter { it.isNotBlank() }.map { JSONObject(it) }

    private fun fatos(caso: JSONObject): List<Fato> {
        val a = caso.getJSONArray("fatos")
        return (0 until a.length()).map { n ->
            val f = a.getJSONObject(n)
            val itens = f.getJSONArray("itens")
            Fato(
                Intencao.valueOf(f.getString("ferramenta")), f.textoOuNulo("periodo"), f.getString("frase"),
                (0 until itens.length()).map {
                    val i = itens.getJSONObject(it)
                    ItemDoFato(i.textoOuNulo("rotulo"), TipoValor.valueOf(i.getString("tipo").uppercase()), i.getString("valor"))
                },
            )
        }
    }

    private fun JSONObject.textoOuNulo(chave: String): String? = if (isNull(chave)) null else getString(chave)

    @Test fun casosCompartilhados() {
        assertTrue(casos.size >= 25)
        val falhas = casos.mapNotNull { c ->
            val esperado = c.getBoolean("aprovada") to c.textoOuNulo("periodo_depois")
            val obtido = Verificador.verificarFrase(c.getString("frase"), fatos(c), c.textoOuNulo("periodo_vigente"))
            if (obtido == esperado) null else "${c.getString("nota")}: esperado $esperado, obtido $obtido"
        }
        assertTrue(falhas.joinToString("\n"), falhas.isEmpty())
    }

    @Test fun frases() {
        assertEquals(emptyList<String>(), Verificador.frases(""))
        assertEquals(emptyList<String>(), Verificador.frases("  \n "))
        assertEquals(listOf("Sobraram R$ 1.200."), Verificador.frases("Sobraram R$ 1.200."))
        assertEquals(
            listOf("Em novembro você gastou R$ 1.200.", "Depois vem Casa (R$ 300)."),
            Verificador.frases("Em novembro você gastou R$ 1.200. Depois vem Casa (R$ 300)."),
        )
        assertEquals(listOf("Olá!", "Tudo bem?", "Sim"), Verificador.frases("Olá!\nTudo bem?  Sim"))
        assertEquals(listOf("Total: R$ 1.200,50 no mês"), Verificador.frases("Total: R$ 1.200,50 no mês"))
    }

    @Test fun fatosCitados() {
        val fatos = fatos(casos.first { it.getString("nota") == "mês trocado" }) // ranking de novembro, total de outubro
        assertEquals(setOf(1), Verificador.fatosCitados(listOf("Em outubro você gastou R$ 7.315."), fatos))
        assertEquals(setOf(0, 1), Verificador.fatosCitados(listOf("Em novembro Casa levou R$ 1.946.", "Em outubro, R$ 7.315."), fatos))
        assertEquals(emptySet<Int>(), Verificador.fatosCitados(listOf("Você está indo bem."), fatos))
    }
}
