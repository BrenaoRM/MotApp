package com.example.financacelular.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartaoDoBancoTest {

    private val cartoes = listOf(1L to "Mercado Pago", 2L to "Inter Black", 3L to "Nubank")

    @Test
    fun escolheOCartaoQueContemONomeDoBanco() {
        assertEquals(2L, cartaoDoBanco(cartoes, "Inter"))
    }

    @Test
    fun aceitaBancoQueContemONomeDoCartao() {
        assertEquals(3L, cartaoDoBanco(cartoes, "Nubank S.A."))
    }

    @Test
    fun ignoraMaiusculasEAcentos() {
        assertEquals(4L, cartaoDoBanco(listOf(1L to "Mercado Pago", 4L to "itaú"), "Itau"))
    }

    @Test
    fun semCorrespondenciaDevolveNull() {
        assertNull(cartaoDoBanco(cartoes, "Bradesco"))
    }

    @Test
    fun bancoVazioDevolveNull() {
        assertNull(cartaoDoBanco(cartoes, ""))
    }
}
