package com.example.financacelular.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DividirEmParcelasTest {

    private fun centavos(parcelas: List<Double>) = parcelas.sumOf { Math.round(it * 100) }

    @Test
    fun cem_em_tres_primeira_parcela_absorve_o_resto() {
        val p = dividirEmParcelas(100.0, 3)
        assertEquals(listOf(33.34, 33.33, 33.33), p)
        assertEquals(10_000L, centavos(p))
    }

    @Test
    fun divisao_exata_gera_parcelas_iguais() {
        assertEquals(listOf(25.0, 25.0, 25.0, 25.0), dividirEmParcelas(100.0, 4))
    }

    @Test
    fun uma_parcela_e_o_proprio_total() {
        assertEquals(listOf(99.99), dividirEmParcelas(99.99, 1))
    }

    @Test
    fun um_centavo_em_duas_parcelas_fica_na_primeira() {
        assertEquals(listOf(0.01, 0.0), dividirEmParcelas(0.01, 2))
    }
}
