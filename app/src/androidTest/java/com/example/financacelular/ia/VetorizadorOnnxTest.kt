package com.example.financacelular.ia

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class VetorizadorOnnxTest {
    private lateinit var context: Context
    private lateinit var config: ConfigIa
    private var vetorizador: VetorizadorOnnx? = null

    @Before
    fun abrir() {
        context = ApplicationProvider.getApplicationContext()
        config = ConfigIa.deJson(context.assets.open("ia/modelo_ia.json").use { it.readBytes().toString(Charsets.UTF_8) })
        vetorizador = VetorizadorOnnx.abrir(context, config)
    }

    @After
    fun fechar() {
        vetorizador?.close()
    }

    /** Os golden vêm de `src/test/resources` (ver `sourceSets` do androidTest em build.gradle.kts). */
    private fun golden(): JSONArray {
        val url = checkNotNull(javaClass.classLoader!!.getResource("ia/golden_vetores.json"))
        return JSONArray(url.readText(Charsets.UTF_8))
    }

    private fun modelo(): VetorizadorOnnx {
        assertNotNull("modelo não abriu (ABI sem arm64?)", vetorizador)
        return vetorizador!!
    }

    @Test
    fun vetoresIguaisAoPython() {
        val casos = golden()
        val textos = List(casos.length()) { casos.getJSONObject(it).getString("texto") }
        val obtidos = modelo().vetorizar(textos)
        var menor = 1.0
        for (i in textos.indices) {
            val a = casos.getJSONObject(i).getJSONArray("vetor")
            val cos = (0 until a.length()).sumOf { k -> a.getDouble(k) * obtidos[i][k] }
            menor = minOf(menor, cos)
        }
        Log.i("IA", "cosseno minimo contra o Python: $menor")
        // 0,99 e não 0,999: os golden são do x86 e os kernels int8 do arm64 arredondam diferente (mínimo medido
        // ~0,99); nessa faixa os vizinhos mais próximos do kNN não mudam na prática. No JVM a barra é 0,999.
        assertTrue("cosseno mínimo $menor", menor >= 0.99)
    }

    @Test
    fun vetorTemNormaUm() {
        for (vetor in modelo().vetorizar(listOf("padaria pão quente", "uber trip", ""))) {
            assertEquals(config.dimensao, vetor.size)
            val norma = sqrt(vetor.sumOf { (it * it).toDouble() })
            assertTrue("norma $norma", abs(norma - 1.0) < 1e-4)
        }
    }

    @Test
    fun latenciaAceitavel() {
        val v = modelo()
        val casos = golden()
        val textos = List(100) { casos.getJSONObject(it % casos.length()).getString("texto") + " " + it }
        v.vetorizar(textos.take(5)) // aquecimento
        val t0 = System.nanoTime()
        textos.forEach { v.vetorizar(listOf(it)) }
        val mediaMs = (System.nanoTime() - t0) / 1e6 / textos.size
        val t1 = System.nanoTime()
        v.vetorizar(textos.take(32))
        val loteMs = (System.nanoTime() - t1) / 1e6
        Log.i("IA", "latencia: ${"%.1f".format(mediaMs)} ms por descricao, lote de 32 em ${"%.1f".format(loteMs)} ms")
        assertTrue("média $mediaMs ms", mediaMs <= 30.0)
        assertTrue("lote $loteMs ms", loteMs <= 400.0)
    }

    @Test
    fun vetorizadorIndisponivelDevolveNull() {
        assertNull(VetorizadorOnnx.abrir(context, config, caminhoModelo = "ia/nao_existe.onnx"))
    }
}
