package com.example.financacelular.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EstabelecimentoCategoriaTest {
    @Test
    fun ignoraCaixaAcentoEEspacos() {
        assertEquals("padaria sao jose", chaveDoEstabelecimento("  PADARIA   São José "))
    }

    @Test
    fun nomesDiferentesContinuamDiferentes() {
        assertNotEquals(chaveDoEstabelecimento("Mercado Ze"), chaveDoEstabelecimento("Mercado Zeca"))
    }

    @Test
    fun nomeVazioViraChaveVazia() {
        assertEquals("", chaveDoEstabelecimento("   "))
    }
}
