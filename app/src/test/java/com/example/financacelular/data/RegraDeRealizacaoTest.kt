package com.example.financacelular.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegraDeRealizacaoTest {

    private val hoje = LocalDate.of(2026, 11, 24)

    private fun debito(data: LocalDate, recorrenteId: Long? = null) = Transacao(
        valor = 10.0, data = data, categoriaId = 5L, tipo = TipoTransacao.DESPESA,
        formaPagamento = FormaPagamento.DEBITO, recorrenteId = recorrenteId
    )

    private fun credito(
        data: LocalDate,
        cartaoId: Long? = 1L,
        anoMes: String? = data.toString().take(7),
        numeroParcela: Int = 1,
        totalParcelas: Int = 1,
        recorrenteId: Long? = null
    ) = Transacao(
        valor = 10.0, data = data, categoriaId = 5L, tipo = TipoTransacao.DESPESA,
        formaPagamento = FormaPagamento.CARTAO_CREDITO, cartaoId = cartaoId, anoMes = anoMes,
        numeroParcela = numeroParcela, totalParcelas = totalParcelas, recorrenteId = recorrenteId
    )

    private fun pagamento(cartaoId: Long, anoMes: String, data: LocalDate = hoje) = Transacao(
        valor = 100.0, data = data, categoriaId = 5L, tipo = TipoTransacao.DESPESA,
        descricao = descricaoPagamentoFatura(cartaoId, anoMes)
    )

    // ---------- Débito / dinheiro: depende só da data ----------

    @Test
    fun debito_passado_e_realizado() {
        assertEquals(SituacaoLancamento.REALIZADO, debito(hoje.minusDays(3)).situacao(emptySet(), hoje))
    }

    @Test
    fun debito_de_hoje_e_realizado() {
        assertEquals(SituacaoLancamento.REALIZADO, debito(hoje).situacao(emptySet(), hoje))
    }

    @Test
    fun debito_futuro_e_futuro() {
        assertEquals(SituacaoLancamento.FUTURO, debito(hoje.plusDays(1)).situacao(emptySet(), hoje))
    }

    @Test
    fun dinheiro_futuro_e_futuro() {
        val t = debito(hoje.plusDays(5)).copy(formaPagamento = FormaPagamento.DINHEIRO)
        assertEquals(SituacaoLancamento.FUTURO, t.situacao(emptySet(), hoje))
    }

    // ---------- Crédito com cartão: depende só da fatura ----------

    @Test
    fun credito_com_fatura_paga_e_realizado() {
        val pagas = setOf(FaturaChave(1L, "2026-11"))
        assertEquals(SituacaoLancamento.REALIZADO, credito(hoje.minusDays(2)).situacao(pagas, hoje))
    }

    @Test
    fun credito_com_fatura_nao_paga_fica_na_fatura_em_aberto() {
        val pagas = setOf(FaturaChave(1L, "2026-10"), FaturaChave(2L, "2026-11"))
        assertEquals(SituacaoLancamento.NA_FATURA_EM_ABERTO, credito(hoje.minusDays(2)).situacao(pagas, hoje))
    }

    @Test
    fun credito_com_data_futura_e_fatura_paga_e_realizado() {
        val pagas = setOf(FaturaChave(1L, "2026-11"))
        assertEquals(SituacaoLancamento.REALIZADO, credito(hoje.plusDays(3)).situacao(pagas, hoje))
    }

    @Test
    fun fatura_paga_lida_do_pagamento_gravado() {
        val compra = credito(hoje.minusDays(2), cartaoId = 2L)
        val pagas = conjuntoFaturasPagas(listOf(compra, pagamento(2L, "2026-11")))
        assertEquals(SituacaoLancamento.REALIZADO, compra.situacao(pagas, hoje))
    }

    @Test
    fun credito_sem_cartao_segue_a_data() {
        assertEquals(SituacaoLancamento.REALIZADO, credito(hoje.minusDays(1), cartaoId = null).situacao(emptySet(), hoje))
        assertEquals(SituacaoLancamento.FUTURO, credito(hoje.plusDays(1), cartaoId = null).situacao(emptySet(), hoje))
    }

    // ---------- Pagamento de fatura ----------

    @Test
    fun pagamento_de_fatura_nunca_conta_como_gasto() {
        val passado = pagamento(1L, "2026-10", data = hoje.minusDays(10))
        val futuro = pagamento(2L, "2026-12", data = hoje.plusDays(10))
        assertEquals(SituacaoLancamento.PAGAMENTO_DE_FATURA, passado.situacao(emptySet(), hoje))
        assertEquals(SituacaoLancamento.PAGAMENTO_DE_FATURA, futuro.situacao(emptySet(), hoje))
        assertFalse(passado.estaRealizada(emptySet(), hoje))
        assertFalse(futuro.estaPendente(emptySet(), hoje))
    }

    // ---------- Parcelas e recorrências ----------

    @Test
    fun parcela_futura_fica_na_fatura_em_aberto() {
        val parcela = credito(LocalDate.of(2027, 1, 10), numeroParcela = 3, totalParcelas = 5)
        val pagas = setOf(FaturaChave(1L, "2026-11"))
        assertEquals(SituacaoLancamento.NA_FATURA_EM_ABERTO, parcela.situacao(pagas, hoje))
    }

    @Test
    fun cobranca_de_recorrencia_futura() {
        assertEquals(SituacaoLancamento.FUTURO, debito(hoje.plusDays(6), recorrenteId = 7L).situacao(emptySet(), hoje))
        val noCartao = credito(hoje.plusDays(6), recorrenteId = 7L)
        assertEquals(SituacaoLancamento.NA_FATURA_EM_ABERTO, noCartao.situacao(emptySet(), hoje))
    }

    // ---------- Virada de mês ----------

    @Test
    fun virada_de_mes_debito() {
        val primeiroDeDezembro = debito(LocalDate.of(2026, 12, 1))
        assertEquals(SituacaoLancamento.FUTURO, primeiroDeDezembro.situacao(emptySet(), LocalDate.of(2026, 11, 30)))
        assertEquals(SituacaoLancamento.REALIZADO, primeiroDeDezembro.situacao(emptySet(), LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun virada_de_mes_compra_depois_do_fechamento_cai_na_fatura_seguinte() {
        // Compra em 28/11 depois do fechamento: anoMes gravado é dezembro
        val compra = credito(LocalDate.of(2026, 11, 28), anoMes = "2026-12")
        val diaDepois = LocalDate.of(2026, 12, 1)
        assertEquals(SituacaoLancamento.NA_FATURA_EM_ABERTO, compra.situacao(setOf(FaturaChave(1L, "2026-11")), diaDepois))
        assertEquals(SituacaoLancamento.REALIZADO, compra.situacao(setOf(FaturaChave(1L, "2026-12")), diaDepois))
    }

    @Test
    fun credito_sem_ano_mes_usa_o_mes_da_data() {
        val compra = credito(LocalDate.of(2026, 11, 30), anoMes = null)
        assertEquals(SituacaoLancamento.REALIZADO, compra.situacao(setOf(FaturaChave(1L, "2026-11")), hoje))
        assertEquals(SituacaoLancamento.NA_FATURA_EM_ABERTO, compra.situacao(setOf(FaturaChave(1L, "2026-12")), hoje))
    }

    // ---------- Atalhos ----------

    @Test
    fun realizada_e_pendente_sao_complementares_fora_o_pagamento() {
        val pagas = setOf(FaturaChave(1L, "2026-11"))
        val lancamentos = listOf(
            debito(hoje), debito(hoje.plusDays(1)),
            credito(hoje), credito(hoje, cartaoId = 3L), credito(hoje.plusDays(1), cartaoId = null)
        )
        for (t in lancamentos) {
            assertTrue(t.estaRealizada(pagas, hoje) != t.estaPendente(pagas, hoje))
        }
    }

    @Test
    fun categoria_da_fatura_so_troca_compra_no_cartao() {
        assertEquals(99L, credito(hoje).comCategoriaDaFatura(99L).categoriaId)
        assertEquals(5L, credito(hoje, cartaoId = null).comCategoriaDaFatura(99L).categoriaId)
        assertEquals(5L, debito(hoje).comCategoriaDaFatura(99L).categoriaId)
    }

    @Test
    fun id_da_categoria_fatura_ignora_maiusculas() {
        val categorias = listOf(
            Categoria(id = 1L, nome = "Mercado", tipo = TipoTransacao.DESPESA),
            Categoria(id = 8L, nome = "FATURA", tipo = TipoTransacao.DESPESA)
        )
        assertEquals(8L, idDaCategoriaFatura(categorias))
        assertNull(idDaCategoriaFatura(categorias.take(1)))
    }
}
