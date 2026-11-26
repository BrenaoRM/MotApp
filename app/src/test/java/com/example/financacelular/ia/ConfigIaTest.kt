package com.example.financacelular.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lê o `modelo_ia.json` real dos assets (o org.json de verdade está no classpath dos testes JVM). */
class ConfigIaTest {
    @Test
    fun leModeloIaReal() {
        val cfg = GoldenIa.config()
        assertEquals(9, cfg.generico.pesos.size)
        cfg.generico.pesos.forEach { assertEquals(cfg.dimensao, it.size) }
        assertEquals(9, cfg.generico.vetoresNomes.size)
        cfg.generico.vetoresNomes.forEach { assertEquals(cfg.dimensao, it.size) }
        assertEquals(9, cfg.generico.vieses.size)
        assertEquals(9, cfg.generico.nomes.size)
        assertTrue(cfg.classificador.limiarSugestao in 0f..1f)
        assertEquals(0.9f, cfg.generico.confiancaLexico)
        assertTrue(cfg.lexico.isNotEmpty())
        assertTrue(cfg.lexico.values.all { it in cfg.generico.nomes })
        assertTrue(cfg.atipicos.minimoGastos > 0)
    }
}
