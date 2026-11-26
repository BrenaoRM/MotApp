package com.example.financacelular.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.sqrt

class AtributosDeGastoTest {

    private val cfg = ConfigAtipicos(0.04f, 200, 256, 42, 20, 3000, 3f, 3, 0.2f, 6)

    private fun gasto(id: Long, dia: String, reais: Long, chave: String, categoria: Long = 1) =
        GastoParaAnalise(id, LocalDate.parse(dia), reais * 100, categoria, chave)

    private fun List<AtributosGasto>.de(id: Long) = single { it.gasto.transacaoId == id }

    @Test
    fun zRobustoComPisoDeMad() {
        val gastos = (1L..5L).map { gasto(it, "2026-10-0${it + 1}", 50, "padaria") } +
            gasto(6, "2026-10-20", 500, "padaria")
        val a = AtributosDeGasto.calcular(gastos, cfg).de(6)
        assertEquals((ln(501.0) - ln(51.0)) / (1.4826 * 0.2), a.zChave, 1e-6)
        assertTrue(a.chaveConhecida)
        assertEquals(5000L, a.valorReferenciaCentavos)
    }

    @Test
    fun referenciaSoComTresOuMais() {
        val gastos = listOf(
            gasto(1, "2026-10-01", 40, "padaria"), gasto(2, "2026-10-08", 45, "padaria"),
            gasto(3, "2026-10-10", 30, "mercado"), gasto(4, "2026-10-15", 90, "acougue"),
        )
        val atributos = AtributosDeGasto.calcular(gastos, cfg)
        val a = atributos.de(1)
        assertFalse(a.chaveConhecida)
        assertEquals(a.zCategoria, a.zChave, 0.0)
        // categoria com 4 gastos (≥ 3): a referência é a mediana da categoria em log, (ln 41 + ln 46) / 2
        assertEquals(Math.round((sqrt(41.0 * 46.0) - 1) * 100), a.valorReferenciaCentavos)
    }

    @Test
    fun duplicadoEmTresDias() {
        val dentro = AtributosDeGasto.calcular(
            listOf(gasto(1, "2026-11-01", 80, "posto"), gasto(2, "2026-11-03", 80, "posto")), cfg,
        )
        assertFalse(dentro.de(1).duplicado)
        assertTrue(dentro.de(2).duplicado)
        val fora = AtributosDeGasto.calcular(
            listOf(gasto(1, "2026-11-01", 80, "posto"), gasto(2, "2026-11-05", 80, "posto")), cfg,
        )
        assertFalse(fora.de(1).duplicado)
        assertFalse(fora.de(2).duplicado)
    }

    @Test
    fun intervaloPadrao30Dias() {
        val atributos = AtributosDeGasto.calcular(
            listOf(gasto(1, "2026-11-01", 80, "posto"), gasto(2, "2026-11-11", 30, "padaria")), cfg,
        )
        assertEquals(ln(31.0), atributos.de(1).logIntervaloCategoria, 1e-12)
        assertEquals(ln(11.0), atributos.de(2).logIntervaloCategoria, 1e-12)
    }

    @Test
    fun vetorNaOrdemDoFinia() {
        val gastos = listOf(gasto(1, "2026-11-01", 80, "posto"), gasto(2, "2026-11-01", 80, "posto"))
        val a = AtributosDeGasto.calcular(gastos, cfg).de(2)
        val v = a.vetor()
        assertEquals(listOf(a.logValor, a.zChave, a.zCategoria, 1.0, a.logIntervaloCategoria, 2.0), v.toList())
    }

    /** Mesmos atributos que o `build_features` do FinIA (golden_isolation.json, chave = `merchant_key`). */
    @Test
    fun igualAoBuildFeaturesDoFinia() {
        val usuarios = GoldenIa.array("golden_isolation.json")
        for (u in 0 until usuarios.length()) {
            val linhas = usuarios.getJSONObject(u).getJSONArray("gastos")
            val gastos = (0 until linhas.length()).map { i ->
                val o = linhas.getJSONObject(i)
                GastoParaAnalise(
                    o.getLong("id"), LocalDate.parse(o.getString("data")), o.getLong("valorCentavos"),
                    o.getLong("categoriaId"), o.getString("chave"),
                )
            }
            val atributos = AtributosDeGasto.calcular(gastos, cfg).associateBy { it.gasto.transacaoId }
            val duplicadosPython = mutableListOf<Double>()
            val intervalosPython = mutableListOf<Double>()
            for (i in 0 until linhas.length()) {
                val o = linhas.getJSONObject(i)
                val esperado = o.getJSONArray("atributos")
                val a = atributos.getValue(o.getLong("id"))
                // Atributos que não dependem da ordem entre gastos do mesmo dia: iguais linha a linha
                assertEquals(esperado.getDouble(0), a.logValor, 1e-9)
                assertEquals(esperado.getDouble(1), a.zChave, 1e-9)
                assertEquals(esperado.getDouble(2), a.zCategoria, 1e-9)
                assertEquals(esperado.getDouble(5), a.noMesmoDia.toDouble(), 0.0)
                duplicadosPython += esperado.getDouble(3)
                intervalosPython += esperado.getDouble(4)
            }
            // O pandas ordena empates de data sem garantia de estabilidade: duplicado e intervalo batem como conjunto
            val v = atributos.values.map { it.vetor() }
            assertEquals(duplicadosPython.sum(), v.sumOf { it[3] }, 0.0)
            val intervalosKotlin = v.map { it[4] }.sorted()
            intervalosPython.sorted().forEachIndexed { i, e -> assertEquals(e, intervalosKotlin[i], 1e-9) }
        }
    }
}
