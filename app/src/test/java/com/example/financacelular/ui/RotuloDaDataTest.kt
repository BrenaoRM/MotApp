package com.example.financacelular.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RotuloDaDataTest {
    private val hoje = LocalDate.of(2026, 11, 28)

    @Test
    fun `cartao do gasto mostra a data dita`() {
        assertEquals("hoje", rotuloDaData(hoje, hoje))
        assertEquals("ontem", rotuloDaData(hoje.minusDays(1), hoje))
        assertEquals("05/11", rotuloDaData(LocalDate.of(2026, 11, 5), hoje))
        assertEquals("24/12/2025", rotuloDaData(LocalDate.of(2025, 12, 24), hoje))
    }
}
