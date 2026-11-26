package com.example.financacelular.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Confere o modelo int8 no ONNX Runtime desktop (x86) contra os vetores do Python; os de arm64 ficam no androidTest. */
class VetorizadorJvmTest {
    @Test
    fun vetoresIguaisAoPython() {
        val config = GoldenIa.config()
        val vetorizador = VetorizadorOnnx.criar(GoldenIa.asset("modelo.onnx").readBytes(), GoldenIa.tokenizador(), config)
        vetorizador.use {
            val casos = GoldenIa.array("golden_vetores.json")
            val textos = List(casos.length()) { casos.getJSONObject(it).getString("texto") }
            val obtidos = it.vetorizar(textos)
            var menor = 1.0
            for (i in textos.indices) {
                val a = casos.getJSONObject(i).getJSONArray("vetor")
                assertEquals(config.dimensao, obtidos[i].size)
                val cos = (0 until a.length()).sumOf { k -> a.getDouble(k) * obtidos[i][k] }
                assertEquals(1.0, sqrt(obtidos[i].sumOf { x -> (x * x).toDouble() }), 1e-4)
                menor = minOf(menor, cos)
            }
            println("COSSENO_MINIMO_JVM=$menor")
            assertTrue("cosseno mínimo $menor", menor >= 0.999)
        }
    }
}
