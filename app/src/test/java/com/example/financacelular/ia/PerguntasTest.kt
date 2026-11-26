package com.example.financacelular.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.sqrt

class PerguntasTest {
    private val hoje = LocalDate.of(2026, 11, 26)
    private val global = YearMonth.of(2026, 11)

    /** 2-d: logit 0 = (3, 0), logit 1 = (0, 3); sem centroides (porta de similaridade desligada). */
    private val cfg = ConfigIntencoes(
        nomes = listOf("TOTAL_DO_MES", "RANKING_CATEGORIAS"),
        pesos = listOf(floatArrayOf(3f, 0f), floatArrayOf(0f, 3f)),
        vieses = floatArrayOf(0f, 0f),
        limiar = 0.6f,
        similaridadeCategoria = 0.6f,
    )

    private val mercado = 1L
    private val restaurantes = 2L
    private val nomes = mapOf(mercado to "Mercado", restaurantes to "Restaurantes")

    /** Vetor unitário com cosseno [c] em relação a (1, 0). */
    private fun comCosseno(c: Float) = floatArrayOf(c, sqrt(1 - c * c))

    private fun entender(
        texto: String,
        vetor: FloatArray = floatArrayOf(1f, 0f),
        categorias: List<Pair<Long, FloatArray>> = emptyList(),
        config: ConfigIntencoes = cfg,
    ) = Perguntas.entender(texto, vetor, categorias, nomes, global, hoje, config)

    @Test
    fun extrairMesSemDataOuEsteMesUsaOMesGlobal() {
        assertEquals(global, Perguntas.extrairMes("quanto gastei", global, hoje))
        assertEquals(global, Perguntas.extrairMes("quanto gastei este mês", global, hoje))
        assertEquals(YearMonth.of(2026, 8), Perguntas.extrairMes("quanto gastei este mês", YearMonth.of(2026, 8), hoje))
    }

    @Test
    fun extrairMesPassadoEMesGlobalMenosUm() {
        assertEquals(YearMonth.of(2026, 10), Perguntas.extrairMes("Quanto gastei no mês passado?", global, hoje))
        assertEquals(YearMonth.of(2026, 9), Perguntas.extrairMes("quanto gastei no mes passado", YearMonth.of(2026, 10), hoje))
    }

    @Test
    fun extrairMesPeloNomeDoMes() {
        assertEquals(YearMonth.of(2026, 10), Perguntas.extrairMes("quanto gastei em outubro", global, hoje))
        assertEquals(YearMonth.of(2026, 11), Perguntas.extrairMes("quanto gastei em Novembro", global, hoje))
        assertEquals(YearMonth.of(2026, 3), Perguntas.extrairMes("gastos de março", global, hoje))
        assertEquals(YearMonth.of(2026, 3), Perguntas.extrairMes("gastos de marco", global, hoje))
    }

    @Test
    fun extrairMesFuturoNoAnoVaiParaOAnoPassado() {
        assertEquals(YearMonth.of(2025, 12), Perguntas.extrairMes("quanto gastei em dezembro", global, hoje))
    }

    @Test
    fun extrairMesComAnoExplicito() {
        assertEquals(YearMonth.of(2025, 10), Perguntas.extrairMes("quanto gastei em outubro de 2025", global, hoje))
        assertEquals(YearMonth.of(2027, 1), Perguntas.extrairMes("janeiro 2027", global, hoje))
    }

    @Test
    fun categoriaPorNomeExato() {
        val p = entender(
            "quanto gastei com mercado",
            categorias = listOf(mercado to floatArrayOf(0f, 1f), restaurantes to floatArrayOf(0f, -1f)),
        )
        assertEquals(mercado, p.categoriaId)
    }

    @Test
    fun categoriaPorNomeIgnoraAcentoECaixa() {
        val p = Perguntas.entender(
            "QUANTO GASTEI COM FARMÁCIA", floatArrayOf(1f, 0f), listOf(7L to floatArrayOf(0f, 1f)),
            mapOf(7L to "Farmácia"), global, hoje, cfg,
        )
        assertEquals(7L, p.categoriaId)
    }

    @Test
    fun categoriaPorNomeNaoCasaPedacoDePalavra() {
        val p = Perguntas.entender(
            "quanto gastei com carros", floatArrayOf(1f, 0f), listOf(7L to floatArrayOf(0f, 1f)),
            mapOf(7L to "Carro"), global, hoje, cfg,
        )
        assertNull(p.categoriaId)
    }

    @Test
    fun categoriaPorSimilaridade() {
        val achou = entender("quanto gastei com comida", categorias = listOf(restaurantes to comCosseno(0.7f)))
        assertEquals(restaurantes, achou.categoriaId)
        val longe = entender("quanto gastei com comida", categorias = listOf(restaurantes to comCosseno(0.4f)))
        assertNull(longe.categoriaId)
    }

    @Test
    fun entendeAIntencaoDeMaiorProbabilidade() {
        val a = entender("quanto gastei", vetor = floatArrayOf(1f, 0f))
        assertEquals(Intencao.TOTAL_DO_MES, a.intencao)
        val b = entender("onde gasto mais", vetor = floatArrayOf(0f, 1f))
        assertEquals(Intencao.RANKING_CATEGORIAS, b.intencao)
        assertEquals(global, b.mes)
    }

    @Test
    fun perguntaForaDoEscopoNaoResponde() {
        // logits (0,3 ; 0,3): probabilidade 0,5, abaixo do limiar 0,6
        val p = entender("qual a capital da frança", vetor = floatArrayOf(0.1f, 0.1f))
        assertEquals(Intencao.DESCONHECIDA, p.intencao)
        assertEquals(0.5f, p.confianca, 1e-4f)
    }

    @Test
    fun longeDeTodosOsCentroidesNaoResponde() {
        val comPorta = cfg.copy(
            centroides = listOf(floatArrayOf(0f, 1f), floatArrayOf(0f, -1f)), similaridadeMinima = 0.5f,
        )
        // confiança alta (0,95) na intenção 0, mas o vetor é ortogonal aos dois centroides
        val p = entender("me conta uma piada", vetor = floatArrayOf(1f, 0f), config = comPorta)
        assertEquals(Intencao.DESCONHECIDA, p.intencao)
        val perto = entender("quanto gastei", vetor = floatArrayOf(0.2f, 0.98f), config = comPorta.copy(similaridadeMinima = 0.5f))
        assertEquals(Intencao.RANKING_CATEGORIAS, perto.intencao)
    }

    @Test
    fun vetorDeOutroTamanhoNaoQuebra() {
        val p = entender("quanto gastei", vetor = FloatArray(5))
        assertEquals(Intencao.DESCONHECIDA, p.intencao)
    }

    @Test
    fun compararUsaOMesGlobalMesmoDizendoMesPassado() {
        val comparar = cfg.copy(nomes = listOf("COMPARAR_MES_ANTERIOR", "RANKING_CATEGORIAS"))
        val p = entender("gastei mais que no mês passado", config = comparar)
        assertEquals(Intencao.COMPARAR_MES_ANTERIOR, p.intencao)
        assertEquals(global, p.mes)
        val explicito = entender("compara com outubro", config = comparar)
        assertEquals(YearMonth.of(2026, 10), explicito.mes)
    }

    @Test
    fun configSemIntencoesRecusaTudo() {
        val p = entender("quanto gastei", config = ConfigIntencoes.VAZIA)
        assertEquals(Intencao.DESCONHECIDA, p.intencao)
    }

    @Test
    fun nomeDeIntencaoDesconhecidoNoJsonViraDesconhecida() {
        val p = entender("quanto gastei", config = cfg.copy(nomes = listOf("OUTRA_COISA", "RANKING_CATEGORIAS")))
        assertEquals(Intencao.DESCONHECIDA, p.intencao)
    }

    /** Modelo real (ONNX no JVM) + `intencoes` do `modelo_ia.json` + `teste.csv`: o mesmo número do pytest, em Kotlin. */
    @Test
    fun modeloRealEntendeOTesteCsv() {
        val config = GoldenIa.config()
        assertEquals(12, config.intencoes.nomes.size)
        assertTrue(config.intencoes.nomes.all { n -> Intencao.entries.any { it.name == n } })
        val linhas = java.io.File("../ia/intencoes/teste.csv").readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
            .map { it.substringBefore(',') to it.substringAfter(',').replace("{cat}", "Mercado") }
        val vetorizador = VetorizadorOnnx.criar(GoldenIa.asset("modelo.onnx").readBytes(), GoldenIa.tokenizador(), config)
        val previstas = vetorizador.use { v ->
            val vetores = v.vetorizar(linhas.map { NormalizacaoTexto.paraModelo(it.second) })
            linhas.indices.map { i ->
                Perguntas.entender(linhas[i].second, vetores[i], emptyList(), emptyMap(), global, hoje, config.intencoes).intencao
            }
        }
        val conhecidas = linhas.indices.filter { linhas[it].first != "DESCONHECIDA" }
        val fora = linhas.indices.filter { linhas[it].first == "DESCONHECIDA" }
        val acuracia = conhecidas.count { previstas[it].name == linhas[it].first }.toDouble() / conhecidas.size
        val recusa = fora.count { previstas[it] == Intencao.DESCONHECIDA }.toDouble() / fora.size
        println("INTENCOES_KOTLIN acuracia=$acuracia recusaForaDoEscopo=$recusa")
        assertTrue("acurácia $acuracia", acuracia >= 0.85)
        assertTrue("recusa $recusa", recusa >= 0.80)
    }
}
