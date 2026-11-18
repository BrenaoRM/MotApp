package com.example.financacelular.deteccao

import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisoDeTransacaoTest {

    @Test
    fun ida_e_volta_de_despesa_com_descricao() {
        val d = TransacaoDetectada(6.0, TipoTransacao.DESPESA, "MP *MAISSABOR", FormaPagamento.DEBITO)
        assertEquals(d, extrasParaDetectada(d.paraExtras()))
    }

    @Test
    fun ida_e_volta_de_receita_sem_descricao() {
        val d = TransacaoDetectada(50.0, TipoTransacao.RECEITA, null, FormaPagamento.DEBITO)
        assertEquals(d, extrasParaDetectada(d.paraExtras()))
    }

    @Test
    fun extras_vazios_devolvem_null() {
        assertNull(extrasParaDetectada(emptyMap()))
    }

    @Test
    fun tipo_invalido_devolve_null() {
        val extras = TransacaoDetectada(6.0, TipoTransacao.DESPESA, null, FormaPagamento.DEBITO)
            .paraExtras() + ("det_tipo" to "XYZ")
        assertNull(extrasParaDetectada(extras))
    }

    @Test
    fun forma_invalida_devolve_null() {
        val extras = TransacaoDetectada(6.0, TipoTransacao.DESPESA, null, FormaPagamento.DEBITO)
            .paraExtras() + ("det_forma" to "XYZ")
        assertNull(extrasParaDetectada(extras))
    }
}
