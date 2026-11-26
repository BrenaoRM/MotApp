package com.example.financacelular.ia

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/** Classificador com o modelo real no aparelho: mesmas sugestões que com os vetores do Python, e tempo de treino. */
@RunWith(AndroidJUnit4::class)
class ClassificadorNoAparelhoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config = ConfigIa.deJson(context.assets.open("ia/modelo_ia.json").use { it.readBytes().toString(Charsets.UTF_8) })
    private val nomes = listOf("Mercado", "Transporte", "Restaurantes", "Saúde", "Lazer", "Casa", "Pet", "Trabalho")

    private fun golden(): JSONArray =
        JSONArray(checkNotNull(javaClass.classLoader!!.getResource("ia/golden_vetores.json")).readText(Charsets.UTF_8))

    @Test
    fun sugestoesIguaisAsDosVetoresDoPython() {
        val vetorizador = VetorizadorOnnx.abrir(context, config)
        assertNotNull(vetorizador)
        vetorizador!!.use { v ->
            val casos = golden()
            val textos = List(casos.length()) { casos.getJSONObject(it).getString("texto") }
            val python = List(casos.length()) { i ->
                val a = casos.getJSONObject(i).getJSONArray("vetor")
                FloatArray(a.length()) { a.getDouble(it).toFloat() }
            }
            val aparelho = v.vetorizar(textos)
            val vetoresNomes = v.vetorizar(nomes.map(NormalizacaoTexto::paraModelo))
            val categorias = nomes.mapIndexed { i, n -> CategoriaParaIa(i + 1L, n, vetoresNomes[i]) }
            // alguns rótulos da pessoa, para o modelo pessoal também contar
            val exemplos = textos.indices.filter { it % 3 == 0 }.map { ExemploRotulado(python[it], (it % nomes.size) + 1L) }
            val modelo = ClassificadorDeCategoria(config).treinar(exemplos, categorias)
            var iguais = 0
            textos.indices.forEach { i ->
                val a = modelo.sugerir(aparelho[i], textos[i])?.categoriaId
                val p = modelo.sugerir(python[i], textos[i])?.categoriaId
                val ta = modelo.prever(aparelho[i]).first().categoriaId
                val tp = modelo.prever(python[i]).first().categoriaId
                if (a == p && ta == tp) iguais++ else Log.i("IA", "diferente: '${textos[i]}' aparelho=$a/$ta python=$p/$tp")
            }
            Log.i("IA", "classificador: $iguais de ${textos.size} iguais aos vetores do Python")
            assertTrue("$iguais de ${textos.size}", iguais >= textos.size - 2)
        }
    }

    @Test
    fun tempoDeTreino() {
        val r = java.util.Random(1)
        val categorias = List(15) { c ->
            CategoriaParaIa(c + 1L, "C$c", unit(FloatArray(config.dimensao) { r.nextGaussian().toFloat() }))
        }
        val exemplos = List(500) { i ->
            val base = categorias[i % 15].vetorNome
            ExemploRotulado(unit(FloatArray(base.size) { base[it] + 0.5f * r.nextGaussian().toFloat() / 20f }), (i % 15) + 1L)
        }
        val t0 = System.nanoTime()
        ClassificadorDeCategoria(config).treinar(exemplos, categorias)
        val ms = (System.nanoTime() - t0) / 1e6
        Log.i("IA", "treino: 500 exemplos distintos, 15 categorias, ${config.classificador.epocas} épocas em ${"%.0f".format(ms)} ms")
        assertTrue("$ms ms", ms < 15_000)
    }

    private fun unit(v: FloatArray): FloatArray {
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }
}
