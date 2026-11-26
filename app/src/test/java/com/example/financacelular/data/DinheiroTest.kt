package com.example.financacelular.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DinheiroTest {

    @Test
    fun somaDeMuitosCentavosEExata() {
        val valores = List(1000) { reaisEmCentavos(0.1) }
        assertEquals(100.0, valores.somaEmReais { it }, 0.0)
        // Em Double a mesma soma escapa do centavo exato: era o "-R$ 0,00" que aparecia no saldo
        assertEquals(false, List(1000) { 0.1 }.sum() == 100.0)
    }

    @Test
    fun conversaoArredondaParaOCentavoMaisProximo() {
        assertEquals(3334L, reaisEmCentavos(33.34))
        assertEquals(123456L, reaisEmCentavos(1234.56))
        assertEquals(1001L, reaisEmCentavos(10.006))
        assertEquals(12.34, centavosEmReais(1234L), 0.0)
    }
}
