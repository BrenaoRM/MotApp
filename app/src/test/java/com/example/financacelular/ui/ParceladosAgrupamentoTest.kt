package com.example.financacelular.ui

import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParceladosAgrupamentoTest {

    private fun parcela(numero: Int, total: Int, valor: Double, mes: Int, id: Long) = Transacao(
        id = id,
        valor = valor,
        data = LocalDate.of(2026, mes, 10),
        categoriaId = 1L,
        tipo = TipoTransacao.DESPESA,
        descricao = "Compra ($numero/$total)",
        formaPagamento = FormaPagamento.CARTAO_CREDITO,
        cartaoId = 1L,
        anoMes = "2026-%02d".format(mes),
        numeroParcela = numero,
        totalParcelas = total,
        grupoParcelamentoId = "grupo"
    )

    /** Pagamento da fatura do cartão padrão (id 1) no mês dado. */
    private fun pagamentoDaFatura(mes: Int) = Transacao(
        id = 1000L + mes,
        valor = 1.0,
        data = LocalDate.of(2026, mes, 20),
        categoriaId = 1L,
        tipo = TipoTransacao.DESPESA,
        descricao = "Pagamento de Fatura - 2026-%02d".format(mes)
    )

    @Test
    fun restanteUsaASomaRealDasParcelasPendentes() {
        // 100,00 em 3x: o resto dos centavos fica na 1ª parcela
        val parcelas = listOf(
            parcela(1, 3, 33.34, 1, 1),
            parcela(2, 3, 33.33, 2, 2),
            parcela(3, 3, 33.33, 3, 3)
        )
        val compra = agruparComprasParceladas(parcelas + pagamentoDaFatura(1)).single()

        assertEquals(1, compra.parcelasPagas)
        assertEquals(2, compra.parcelasRestantes)
        assertEquals(66.66, compra.valorRestante, 0.001)
        assertEquals(33.34, compra.valorPago, 0.001)
        assertEquals(100.00, compra.valorTotal, 0.001)
        assertFalse(compra.quitada)
        assertFalse(compra.encerrada)
    }

    @Test
    fun todasPagasFicaQuitada() {
        val parcelas = listOf(
            parcela(1, 3, 33.34, 1, 1),
            parcela(2, 3, 33.33, 2, 2),
            parcela(3, 3, 33.33, 3, 3)
        )
        val pagamentos = listOf(1, 2, 3).map { pagamentoDaFatura(it) }
        val compra = agruparComprasParceladas(parcelas + pagamentos).single()

        assertEquals(3, compra.parcelasPagas)
        assertEquals(0, compra.parcelasRestantes)
        assertEquals(0.0, compra.valorRestante, 0.001)
        assertEquals(100.00, compra.valorPago, 0.001)
        assertTrue(compra.quitada)
        assertFalse(compra.encerrada)
    }

    @Test
    fun canceladaDepoisDeTresDeDezNaoEstaQuitada() {
        // As parcelas pendentes foram apagadas: só restam as 3 pagas
        val parcelas = (1..3).map { parcela(it, 10, 10.0, it, it.toLong()) }
        val pagamentos = (1..3).map { pagamentoDaFatura(it) }
        val compra = agruparComprasParceladas(parcelas + pagamentos).single()

        assertEquals(10, compra.totalParcelas)
        assertEquals(3, compra.parcelasPagas)
        assertEquals(0, compra.parcelasRestantes)
        assertTrue(compra.transacoesPendentes.isEmpty())
        assertEquals(0.0, compra.valorRestante, 0.001)
        assertFalse(compra.quitada)
        assertTrue(compra.encerrada)
    }
}
