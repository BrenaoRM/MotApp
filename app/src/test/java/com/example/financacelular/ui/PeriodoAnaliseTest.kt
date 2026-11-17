package com.example.financacelular.ui

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodoAnaliseTest {

    @Test
    fun mesFechadoAndaDeMesEmMes() {
        val novembro = Periodo.mes(YearMonth.of(2026, 11))
        assertEquals(Periodo.mes(YearMonth.of(2026, 12)), novembro.deslocar(1))
        assertEquals(Periodo.mes(YearMonth.of(2026, 10)), novembro.deslocar(-1))
    }

    @Test
    fun virarOAnoFunciona() {
        assertEquals(Periodo.mes(YearMonth.of(2027, 1)), Periodo.mes(YearMonth.of(2026, 12)).deslocar(1))
    }

    @Test
    fun tresMesesFechadosAndamTresMeses() {
        val trimestre = Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30))
        assertEquals(Periodo(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 28)), trimestre.deslocar(1))
        assertEquals(Periodo(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)), trimestre.deslocar(-1))
    }

    @Test
    fun intervaloLivreAndaPeloNumeroDeDias() {
        val livre = Periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 15)) // 11 dias
        assertFalse(livre.mesesFechados)
        assertEquals(Periodo(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 26)), livre.deslocar(1))
        assertEquals(Periodo(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 4)), livre.deslocar(-1))
    }

    @Test
    fun rotulos() {
        assertEquals("Novembro 2026", Periodo.mes(YearMonth.of(2026, 11)).rotulo())
        assertEquals("05/11 – 15/11/26", Periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 15)).rotulo())
        assertTrue(Periodo.mes(YearMonth.of(2026, 2)).ehMesUnico)
        assertEquals(28, Periodo.mes(YearMonth.of(2026, 2)).dias)
    }

    @Test
    fun rotulosCurtosDoCabecalho() {
        val novembro = Periodo.mes(YearMonth.of(2026, 11))
        assertEquals("Novembro", novembro.tituloCurto())
        assertEquals("2026", novembro.subtituloCurto())

        val trimestre = Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30))
        assertEquals("3 meses", trimestre.tituloCurto())
        assertEquals("set–nov 2026", trimestre.subtituloCurto())

        val dozeMeses = Periodo(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 11, 30))
        assertEquals("12 meses", dozeMeses.tituloCurto())
        assertEquals("dez 2025 – nov 2026", dozeMeses.subtituloCurto())

        val livre = Periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 15))
        assertEquals("05–15 nov", livre.tituloCurto())
        assertEquals("2026", livre.subtituloCurto())

        val entreMeses = Periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 3))
        assertEquals("05/11 – 03/12", entreMeses.tituloCurto())
        assertEquals("15 nov", Periodo(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)).tituloCurto())
    }
}
