package com.example.financacelular.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorrenteNoCartaoTest {

    // ---- anoMesDaCobrancaRecorrente ----

    @Test
    fun cobranca_antes_do_fechamento_fica_no_mes_da_data() {
        assertEquals("2026-11", anoMesDaCobrancaRecorrente(LocalDate.of(2026, 11, 4), 5))
    }

    @Test
    fun cobranca_depois_do_fechamento_vai_para_a_fatura_seguinte() {
        assertEquals("2026-12", anoMesDaCobrancaRecorrente(LocalDate.of(2026, 11, 10), 5))
        assertEquals("2026-12", anoMesDaCobrancaRecorrente(LocalDate.of(2026, 11, 5), 5))
    }

    @Test
    fun dia_limitado_em_fevereiro() {
        // Assinatura dia 31, fechamento 31: em fevereiro cai no dia 28, que é o fechamento -> março
        assertEquals("2027-03", anoMesDaCobrancaRecorrente(LocalDate.of(2027, 2, 28), 31))
        // Janeiro (dia 31 = fechamento) também vai para fevereiro: as duas datas são de meses diferentes
        assertEquals("2027-02", anoMesDaCobrancaRecorrente(LocalDate.of(2027, 1, 31), 31))
    }

    @Test
    fun sem_cartao_usa_o_mes_da_data() {
        assertEquals("2026-11", anoMesDaCobrancaRecorrente(LocalDate.of(2026, 11, 30), null))
    }

    // ---- recorrentesNoCartaoComAnoMesErrado ----

    private val hoje = LocalDate.of(2026, 11, 24)
    private val fechamentos = mapOf(1L to 5)

    private fun cobranca(id: Long, data: LocalDate, anoMes: String, recorrenteId: Long? = 7L, cartaoId: Long? = 1L) =
        Transacao(
            id = id, valor = 10.0, data = data, categoriaId = 1L, tipo = TipoTransacao.DESPESA,
            formaPagamento = FormaPagamento.CARTAO_CREDITO, cartaoId = cartaoId, anoMes = anoMes,
            recorrenteId = recorrenteId
        )

    @Test
    fun futura_com_anoMes_do_calendario_e_corrigida() {
        val t = cobranca(1, LocalDate.of(2026, 12, 10), "2026-12")
        val r = recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, emptySet(), hoje)
        assertEquals(listOf(t.copy(anoMes = "2027-01")), r)
    }

    @Test
    fun ja_certa_nao_muda_e_reparo_e_idempotente() {
        val t = cobranca(1, LocalDate.of(2026, 12, 10), "2026-12")
        val corrigidas = recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, emptySet(), hoje)
        assertTrue(recorrentesNoCartaoComAnoMesErrado(corrigidas, fechamentos, emptySet(), hoje).isEmpty())
    }

    @Test
    fun passada_nao_muda() {
        val t = cobranca(1, LocalDate.of(2026, 11, 10), "2026-11")
        assertTrue(recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, emptySet(), hoje).isEmpty())
    }

    @Test
    fun hoje_conta_como_futura() {
        val t = cobranca(1, hoje, "2026-11")
        assertEquals("2026-12", recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, emptySet(), hoje).single().anoMes)
    }

    @Test
    fun fatura_atual_ou_nova_paga_nao_muda() {
        val t = cobranca(1, LocalDate.of(2026, 12, 10), "2026-12")
        val atualPaga = setOf(FaturaChave(1L, "2026-12"))
        val novaPaga = setOf(FaturaChave(1L, "2027-01"))
        assertTrue(recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, atualPaga, hoje).isEmpty())
        assertTrue(recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, novaPaga, hoje).isEmpty())
    }

    @Test
    fun fatura_paga_de_outro_cartao_nao_bloqueia() {
        val t = cobranca(1, LocalDate.of(2026, 12, 10), "2026-12")
        val outroCartao = setOf(FaturaChave(2L, "2026-12"))
        assertEquals(1, recorrentesNoCartaoComAnoMesErrado(listOf(t), fechamentos, outroCartao, hoje).size)
    }

    @Test
    fun avulsa_sem_cartao_ou_cartao_desconhecido_nao_mudam() {
        val avulsa = cobranca(1, LocalDate.of(2026, 12, 10), "2026-12", recorrenteId = null)
        val debito = cobranca(2, LocalDate.of(2026, 12, 10), "2026-12", cartaoId = null)
        val cartaoSumido = cobranca(3, LocalDate.of(2026, 12, 10), "2026-12", cartaoId = 9L)
        assertTrue(
            recorrentesNoCartaoComAnoMesErrado(listOf(avulsa, debito, cartaoSumido), fechamentos, emptySet(), hoje).isEmpty()
        )
    }

    @Test
    fun mudanca_de_fechamento_volta_para_o_mes_da_data() {
        // Estava na fatura de janeiro (fechamento 5); o cartão passou a fechar dia 20
        val t = cobranca(1, LocalDate.of(2026, 12, 10), "2027-01")
        val r = recorrentesNoCartaoComAnoMesErrado(listOf(t), mapOf(1L to 20), emptySet(), hoje)
        assertEquals("2026-12", r.single().anoMes)
    }
}
