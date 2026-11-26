package com.example.financacelular.ia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class IsolationForestTest {

    @Test
    fun cDeN() {
        assertEquals(1.0, IsolationForest.c(2), 0.0)
        assertEquals(10.2448, IsolationForest.c(256), 1e-3)
    }

    @Test
    fun outlierTemMaiorPontuacao() {
        val r = Random(7)
        val x = List(300) { doubleArrayOf(r.nextGaussian(), r.nextGaussian()) } + listOf(doubleArrayOf(8.0, 8.0))
        val floresta = IsolationForest(200, 256, 42).apply { treinar(x) }
        val pontuacoes = x.map { floresta.pontuacao(it) }
        assertEquals(x.lastIndex, pontuacoes.indices.maxBy { pontuacoes[it] })
        assertTrue("s(8,8) = ${pontuacoes.last()}", pontuacoes.last() > 0.7)
    }

    @Test
    fun deterministicoComSemente() {
        val r = Random(3)
        val x = List(200) { DoubleArray(6) { r.nextGaussian() } }
        val a = IsolationForest(100, 256, 42).apply { treinar(x) }
        val b = IsolationForest(100, 256, 42).apply { treinar(x) }
        assertArrayEquals(x.map { a.pontuacao(it) }.toDoubleArray(), x.map { b.pontuacao(it) }.toDoubleArray(), 0.0)
    }

    /** Mesmos atributos (já calculados no Python) → a ordem das pontuações bate com a do sklearn. */
    @Test
    fun parecidoComSklearn() {
        val cfg = GoldenIa.config().atipicos
        val usuarios = GoldenIa.array("golden_isolation.json")
        assertEquals(3, usuarios.length())
        for (u in 0 until usuarios.length()) {
            val o = usuarios.getJSONObject(u)
            val linhas = o.getJSONArray("gastos")
            val x = (0 until linhas.length()).map { i ->
                val a = linhas.getJSONObject(i).getJSONArray("atributos")
                DoubleArray(a.length()) { a.getDouble(it) }
            }
            val sklearn = (0 until linhas.length()).map { linhas.getJSONObject(it).getDouble("sklearn") }
            val floresta = IsolationForest(cfg.arvores, cfg.amostra, cfg.semente.toLong()).apply { treinar(x) }
            val rho = spearman(x.map { floresta.pontuacao(it) }, sklearn)
            println("Spearman ${o.getString("usuario")} (n=${x.size}): $rho")
            assertTrue("Spearman ${o.getString("usuario")} = $rho", rho >= 0.9)
        }
    }

    companion object {
        /** Correlação de Pearson entre os postos (empates recebem o posto médio). */
        fun spearman(a: List<Double>, b: List<Double>): Double {
            val ra = postos(a)
            val rb = postos(b)
            val ma = ra.average()
            val mb = rb.average()
            var num = 0.0
            var da = 0.0
            var db = 0.0
            for (i in ra.indices) {
                num += (ra[i] - ma) * (rb[i] - mb)
                da += (ra[i] - ma) * (ra[i] - ma)
                db += (rb[i] - mb) * (rb[i] - mb)
            }
            return num / Math.sqrt(da * db)
        }

        private fun postos(v: List<Double>): DoubleArray {
            val ordem = v.indices.sortedBy { v[it] }
            val p = DoubleArray(v.size)
            var i = 0
            while (i < ordem.size) {
                var j = i
                while (j + 1 < ordem.size && v[ordem[j + 1]] == v[ordem[i]]) j++
                for (k in i..j) p[ordem[k]] = (i + j) / 2.0 + 1
                i = j + 1
            }
            return p
        }
    }
}
