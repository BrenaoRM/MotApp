package com.example.financacelular.deteccao

import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DuplicatasTest {

    private var agora = 1_000_000L
    private val filtro = FiltroDeRepeticao(agora = { agora }, janelaMs = 120_000)

    @Test
    fun mesma_chave_dentro_da_janela_e_repetida() {
        assertFalse(filtro.jaVista("a"))
        agora += 60_000
        assertTrue(filtro.jaVista("a"))
    }

    @Test
    fun mesma_chave_depois_da_janela_nao_e_repetida() {
        assertFalse(filtro.jaVista("a"))
        agora += 121_000
        assertFalse(filtro.jaVista("a"))
    }

    @Test
    fun chaves_diferentes_nao_se_repetem() {
        assertFalse(filtro.jaVista("a"))
        assertFalse(filtro.jaVista("b"))
    }

    private val hoje = LocalDate.of(2026, 11, 18)
    private val detectada = TransacaoDetectada(6.0, TipoTransacao.DESPESA, "MP *MAISSABOR", FormaPagamento.DEBITO)
    private val lancada = Transacao(
        valor = 6.0, data = hoje, categoriaId = 1L, tipo = TipoTransacao.DESPESA, descricao = "maissabor"
    )

    @Test
    fun ja_lancada_mesmo_valor_dia_e_descricao_parecida() {
        assertTrue(jaLancada(detectada, hoje, listOf(lancada)))
    }

    @Test
    fun valor_diferente_nao_e_lancada() {
        assertFalse(jaLancada(detectada, hoje, listOf(lancada.copy(valor = 7.0))))
    }

    @Test
    fun outro_dia_nao_e_lancada() {
        assertFalse(jaLancada(detectada, hoje, listOf(lancada.copy(data = hoje.minusDays(1)))))
    }

    @Test
    fun tipo_diferente_nao_e_lancada() {
        assertFalse(jaLancada(detectada, hoje, listOf(lancada.copy(tipo = TipoTransacao.RECEITA))))
    }

    @Test
    fun descricao_diferente_nao_e_lancada() {
        assertFalse(jaLancada(detectada, hoje, listOf(lancada.copy(descricao = "padaria"))))
    }

    @Test
    fun ambas_sem_descricao_e_lancada() {
        assertTrue(jaLancada(detectada.copy(descricao = null), hoje, listOf(lancada.copy(descricao = null))))
    }
}
