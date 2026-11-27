package com.example.financacelular.ia.llm

import com.example.financacelular.ia.GoldenIa
import com.example.financacelular.ia.Intencao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ContratoLlmTest {
    private val contrato = ContratoLlm(GoldenIa.asset("llm/contrato.json").readText(Charsets.UTF_8))
    private val nov = YearMonth.of(2026, 11)
    private val categorias = listOf("Alimentação", "Lazer", "Pet \"Rex\"")
    private val fatos = listOf(
        Fato(
            Intencao.RANKING_CATEGORIAS, "novembro", "Em novembro, Alimentação foi a categoria com mais gastos: R$ 1.200.",
            listOf(ItemDoFato("Alimentação", TipoValor.REAIS, "1.200"), ItemDoFato("Lazer", TipoValor.REAIS, "350")),
        ),
        Fato(
            Intencao.COMPARAR_MES_ANTERIOR, null, "Os gastos subiram 12% em relação a outubro.",
            listOf(ItemDoFato(null, TipoValor.PORCENTO, "12")),
        ),
    )
    private val mapa = mapOf(1L to "Alimentação", 2L to "Lazer", 3L to "Pet \"Rex\"")

    @Test fun promptPlanoGolden() {
        val h = listOf("Quanto gastei em Lazer?" to "{\"chamadas\":[{\"f\":\"gasto_categoria\",\"mes\":\"atual\",\"categoria\":\"Lazer\"}]}")
        assertEquals(
            GoldenIa.recurso("llm/prompt_plano.txt"),
            contrato.promptPlano("Onde gastei mais este mês e no anterior?", nov, categorias, h),
        )
    }

    @Test fun promptRespostaGolden() =
        assertEquals(GoldenIa.recurso("llm/prompt_resposta.txt"), contrato.promptResposta("Onde gastei mais?", fatos))

    @Test fun gramaticaGolden() =
        assertEquals(GoldenIa.recurso("llm/gramatica_plano.gbnf"), contrato.gramaticaPlano(nov, categorias))

    @Test fun gramaticaSemCategoriasGolden() {
        val g = contrato.gramaticaPlano(nov, emptyList())
        assertEquals(GoldenIa.recurso("llm/gramatica_plano_sem_categorias.gbnf"), g)
        assertTrue("categoria ::=" !in g && "gasto-categoria" !in g)
    }

    @Test fun gramaticaNomesEspeciais() {
        val g = contrato.gramaticaPlano(nov, listOf("Bar\\Café", "🍔 Lanche", "A\nB"))
        val esperado = "categoria ::= \"\\\"Bar\\\\\\\\Café\\\"\" | \"\\\"🍔 Lanche\\\"\" | \"\\\"A\\\\nB\\\"\""
        assertTrue(g, esperado in g)
    }

    @Test fun historicoUsaSoOsDoisUltimosPares() {
        val h = (1..3).map { "p$it" to "r$it" }
        val t = contrato.promptPlano("q", nov, categorias, h)
        assertTrue("p1" !in t && "p2" in t && "p3" in t)
    }

    @Test fun lerPlanoResolveMeses() {
        val r = contrato.lerPlano(
            "{\"chamadas\":[{\"f\":\"total_mes\",\"mes\":\"anterior\"},{\"f\":\"ranking\",\"mes\":\"2026-08\"}]}", nov, mapa,
        )
        assertEquals(listOf(Chamada("total_mes", YearMonth.of(2026, 10), null), Chamada("ranking", YearMonth.of(2026, 8), null)), r)
    }

    @Test fun lerPlanoResolveCategoriaComAspas() {
        val r = contrato.lerPlano("{\"chamadas\":[{\"f\":\"gasto_categoria\",\"mes\":\"atual\",\"categoria\":\"Pet \\\"Rex\\\"\"}]}", nov, mapa)
        assertEquals(listOf(Chamada("gasto_categoria", nov, 3L)), r)
    }

    @Test fun categoriaInexistenteDescartaSoAquela() {
        val r = contrato.lerPlano(
            "{\"chamadas\":[{\"f\":\"gasto_categoria\",\"mes\":\"atual\",\"categoria\":\"Nada\"},{\"f\":\"total_mes\",\"mes\":\"atual\"}]}", nov, mapa,
        )
        assertEquals(listOf(Chamada("total_mes", nov, null)), r)
    }

    @Test fun categoriaObrigatoriaAusenteDescarta() =
        assertEquals(emptyList<Chamada>(), contrato.lerPlano("{\"chamadas\":[{\"f\":\"gasto_categoria\",\"mes\":\"atual\"}]}", nov, mapa))

    @Test fun listaVaziaEJsonInvalido() {
        assertEquals(emptyList<Chamada>(), contrato.lerPlano("{\"chamadas\":[]}", nov, mapa))
        assertNull(contrato.lerPlano("lixo", nov, mapa))
    }

    @Test fun intencaoPorFerramenta() {
        assertEquals(Intencao.GASTO_EM_CATEGORIA, contrato.intencao("gasto_categoria"))
        assertEquals("Só consigo falar sobre os seus gastos e receitas.", contrato.textoRecusa)
    }
}
