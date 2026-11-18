package com.example.financacelular.deteccao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BancosMonitoradosTest {

    @Test
    fun pacotes_nao_se_repetem() {
        val pacotes = BancosMonitorados.todos.map { it.pacote }
        assertEquals(pacotes.size, pacotes.toSet().size)
    }

    @Test
    fun porPacote_acha_o_inter() {
        assertEquals("Inter", BancosMonitorados.porPacote("br.com.intermedium")?.nome)
    }

    @Test
    fun porPacote_desconhecido_devolve_null() {
        assertNull(BancosMonitorados.porPacote("com.exemplo.jogo"))
    }
}
