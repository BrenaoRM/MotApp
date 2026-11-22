package com.example.financacelular.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AnoMesDaFaturaTest {

    @Test
    fun dia_antes_do_fechamento_fica_no_mes_da_compra() {
        assertEquals("2025-03", anoMesDaCompraNoCartao(LocalDate.of(2025, 3, 9), 10))
    }

    @Test
    fun dia_igual_ao_fechamento_vai_para_o_mes_seguinte() {
        assertEquals("2025-04", anoMesDaCompraNoCartao(LocalDate.of(2025, 3, 10), 10))
    }

    @Test
    fun fechamento_31_em_fevereiro_fecha_no_ultimo_dia() {
        assertEquals("2025-02", anoMesDaCompraNoCartao(LocalDate.of(2025, 2, 27), 31))
        assertEquals("2025-03", anoMesDaCompraNoCartao(LocalDate.of(2025, 2, 28), 31))
    }

    @Test
    fun dezembro_apos_fechamento_vira_janeiro_do_ano_seguinte() {
        assertEquals("2026-01", anoMesDaCompraNoCartao(LocalDate.of(2025, 12, 20), 15))
    }
}
