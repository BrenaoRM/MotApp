package com.example.financacelular.ia

import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Random
import kotlin.math.exp

class DetectorDeAtipicosTest {

    private val cfg = ConfigAtipicos(0.04f, 200, 256, 42, 20, 3000, 3f, 3, 0.2f, 6)
    private val inicio = LocalDate.of(2026, 6, 1)

    private fun gasto(id: Long, dia: Int, centavos: Long, chave: String, categoria: Long = 1) =
        GastoParaAnalise(id, inicio.plusDays(dia.toLong()), centavos, categoria, chave)

    /** 95 gastos do dia a dia (R$ 15 a R$ 60, 5 lugares) e 5 fora do padrão, um deles abaixo do valor mínimo. */
    private fun cemGastos(): List<GastoParaAnalise> {
        val r = Random(11)
        val lugares = listOf("padaria" to 1500L, "mercado" to 6000L, "posto" to 5000L, "farmacia" to 3500L, "acai" to 2000L)
        val normais = (0 until 95).map { i ->
            val (chave, tipico) = lugares[i % 5]
            gasto(i + 1L, i * 150 / 95, (tipico * exp(0.15 * r.nextGaussian())).toLong(), chave, (i % 5) + 1L)
        }
        return normais + listOf(
            gasto(96, 20, 60000, "padaria", 1),
            gasto(97, 50, 180000, "joalheria", 3),
            gasto(98, 80, 90000, "mercado", 2),
            gasto(99, 100, 5, "acai", 5),
            gasto(100, 120, 250000, "posto", 3),
        )
    }

    @Test
    fun menosDe20GastosNaoMarcaNada() {
        val poucos = cemGastos().take(18) + listOf(gasto(200, 30, 500000, "joalheria"))
        assertEquals(19, poucos.size)
        assertTrue(DetectorDeAtipicos.detectar(poucos, cfg, emptySet()).isEmpty())
    }

    @Test
    fun filtraOQueNaoEhHabito() {
        val fatura = 9L
        val d = LocalDate.of(2026, 3, 10)
        fun t(id: Long, valor: Long, categoria: Long = 1, tipo: TipoTransacao = TipoTransacao.DESPESA, descricao: String? = "Padaria Pão") =
            Transacao(id = id, valorCentavos = valor, data = d, categoriaId = categoria, tipo = tipo, descricao = descricao)
        val parcelas = (1..10).map { n ->
            Transacao(
                id = 100L + n, valorCentavos = 10000, data = LocalDate.of(2026, 3, 15).plusMonths(n - 1L), categoriaId = 2,
                tipo = TipoTransacao.DESPESA, descricao = "Loja X", formaPagamento = FormaPagamento.CARTAO_CREDITO,
                cartaoId = 1, numeroParcela = n, totalParcelas = 10, grupoParcelamentoId = "g1",
                dataCompra = LocalDate.of(2026, 3, 5),
            )
        }
        val transacoes = listOf(
            t(1, 500000, categoria = 7, tipo = TipoTransacao.RECEITA, descricao = "Salário"),
            t(2, 300000, descricao = "Pagamento fatura").copy(pagaFaturaDoCartaoId = 1),
            t(3, 120000, categoria = fatura, descricao = "Ajuste"),
            t(4, 3990, descricao = "Netflix").copy(recorrenteId = 5),
            t(5, 2500),
            t(6, 4000, descricao = "Mercado"),
            t(7, 4100, descricao = null),
            t(8, 4200, descricao = "  "),
        ) + parcelas.shuffled(Random(1))

        val gastos = DetectorDeAtipicos.gastosParaAnalise(transacoes, fatura, mapOf(1L to "Mercado", 2L to "Compras"))
            .associateBy { it.transacaoId }
        assertEquals(setOf(5L, 6L, 7L, 8L, 101L), gastos.keys)
        val compra = gastos.getValue(101)
        assertEquals(100000L, compra.valorCentavos)
        assertEquals(LocalDate.of(2026, 3, 5), compra.data)
        assertEquals("loja x", compra.chave)
        // Chave: descrição normalizada; sem descrição ou igual ao nome da categoria → "#cat<id>"
        assertEquals("padaria pao", gastos.getValue(5).chave)
        assertEquals("#cat1", gastos.getValue(6).chave)
        assertEquals("#cat1", gastos.getValue(7).chave)
        assertEquals("#cat1", gastos.getValue(8).chave)
    }

    @Test
    fun marcaContaminacao() {
        val gastos = cemGastos()
        val pontuados = DetectorDeAtipicos.pontuar(gastos, cfg)
        val topo = pontuados.sortedByDescending { it.second }.take(4)
        val atipicos = DetectorDeAtipicos.detectar(gastos, cfg, emptySet())
        assertTrue(atipicos.size in 1..4)
        assertTrue(atipicos.all { it.valorCentavos >= 3000 })
        val ids = topo.map { it.first.gasto.transacaoId }
        assertTrue("$ids vs ${atipicos.map { it.transacaoId }}", atipicos.all { it.transacaoId in ids })
        // O de R$ 0,05 ocupa uma das 4 vagas e sai pelo valor mínimo: sobram 3, sem promover o 5º
        assertTrue(99L in ids)
        assertEquals(setOf(97L, 98L, 100L), atipicos.map { it.transacaoId }.toSet())
        assertEquals(atipicos.sortedByDescending { it.pontuacao }, atipicos)
    }

    @Test
    fun motivoPorPrioridade() {
        val g = gasto(1, 0, 50000, "x")
        fun a(dup: Boolean = false, zChave: Double = 0.0, zCat: Double = 0.0, conhecida: Boolean = true, ref: Long? = 4000) =
            AtributosGasto(g, 6.2, zChave, zCat, dup, 3.4, 1, ref, conhecida)
        val z = 3.0
        assertEquals(MotivoAtipico.DUPLICADO to null, DetectorDeAtipicos.motivo(a(dup = true, zChave = 9.0, zCat = 9.0), z))
        assertEquals(MotivoAtipico.VALOR_ALTO_NO_LOCAL to 4000L, DetectorDeAtipicos.motivo(a(zChave = 3.5, zCat = 9.0), z))
        assertEquals(MotivoAtipico.VALOR_ALTO_NA_CATEGORIA to 4000L,
            DetectorDeAtipicos.motivo(a(zChave = 9.0, zCat = 3.5, conhecida = false), z))
        // Local conhecido e normal nele, mas caro para a categoria: a referência do local não descreve o motivo
        assertEquals(MotivoAtipico.VALOR_ALTO_NA_CATEGORIA to null, DetectorDeAtipicos.motivo(a(zChave = 1.0, zCat = 3.5), z))
        assertEquals(MotivoAtipico.LOCAL_NOVO_CARO to 4000L, DetectorDeAtipicos.motivo(a(zChave = 2.0, zCat = 2.0, conhecida = false), z))
        assertNull(DetectorDeAtipicos.motivo(a(zChave = 2.0, zCat = 2.0), z))
        // O limite é estrito (> 3)
        assertNull(DetectorDeAtipicos.motivo(a(zChave = 3.0, zCat = 3.0), z))
    }

    @Test
    fun respeitaDispensados() {
        val gastos = cemGastos()
        val todos = DetectorDeAtipicos.detectar(gastos, cfg, emptySet())
        val dispensado = todos.first().transacaoId
        val depois = DetectorDeAtipicos.detectar(gastos, cfg, setOf(dispensado))
        assertFalse(depois.any { it.transacaoId == dispensado })
        assertEquals(todos.drop(1), depois)
    }

    @Test
    fun usaSoOsUltimosMesesDoHistorico() {
        val gastos = cemGastos()
        // Um gasto enorme há 7 meses (antes da janela de 6 meses) não entra na análise
        val antigo = GastoParaAnalise(500, inicio.minusMonths(3), 900000, 3, "joalheria")
        val atipicos = DetectorDeAtipicos.detectar(gastos + antigo, cfg, emptySet())
        assertFalse(atipicos.any { it.transacaoId == 500L })
        assertEquals(DetectorDeAtipicos.detectar(gastos, cfg, emptySet()), atipicos)
    }

    /**
     * Para `ia/avaliar_atipicos.py`: lê `build/ia/gastos_avaliacao.json` (gerado pelo script) e grava
     * `build/ia/pontuacoes.json` com as pontuações do Kotlin. Só roda com `-Dia.exportar=true`.
     */
    @Test
    fun exportaPontuacoesParaAvaliacao() {
        assumeTrue(System.getProperty("ia.exportar") == "true")
        val entrada = JSONArray(File("build/ia/gastos_avaliacao.json").readText(Charsets.UTF_8))
        val saida = JSONArray()
        for (u in 0 until entrada.length()) {
            val usuario = entrada.getJSONObject(u)
            val linhas = usuario.getJSONArray("gastos")
            val variantes = JSONObject()
            for (variante in listOf("chaveApp", "chaveFinia")) {
                val gastos = (0 until linhas.length()).map { i ->
                    val o = linhas.getJSONObject(i)
                    GastoParaAnalise(
                        o.getLong("id"), LocalDate.parse(o.getString("data")), o.getLong("valorCentavos"),
                        o.getLong("categoriaId"), o.getString(variante),
                    )
                }
                val atipicos = DetectorDeAtipicos.detectar(gastos, cfg, emptySet())
                variantes.put(variante, JSONObject()
                    .put("pontuacoes", JSONArray().apply {
                        DetectorDeAtipicos.pontuar(gastos, cfg).forEach { (a, s) ->
                            put(JSONObject().put("id", a.gasto.transacaoId).put("pontuacao", s)
                                .put("atributos", JSONArray(a.vetor().toList())))
                        }
                    })
                    .put("atipicos", JSONArray().apply {
                        atipicos.forEach { put(JSONObject().put("id", it.transacaoId).put("motivo", it.motivo.name)) }
                    }))
            }
            saida.put(JSONObject().put("usuario", usuario.getString("usuario")).put("variantes", variantes))
        }
        File("build/ia/pontuacoes.json").writeText(saida.toString(), Charsets.UTF_8)
    }
}
