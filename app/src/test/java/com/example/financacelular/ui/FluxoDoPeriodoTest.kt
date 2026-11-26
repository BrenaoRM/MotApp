package com.example.financacelular.ui

import com.example.financacelular.data.reaisEmCentavos
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxoDoPeriodoTest {

    private fun lancamento(tipo: TipoTransacao, valor: Double, ano: Int, mes: Int, dia: Int) =
        Transacao(valorCentavos = reaisEmCentavos(valor), data = LocalDate.of(ano, mes, dia), categoriaId = 1L, tipo = tipo)

    private fun ganho(valor: Double, dia: Int, mes: Int = 11) = lancamento(TipoTransacao.RECEITA, valor, 2026, mes, dia)
    private fun gasto(valor: Double, dia: Int, mes: Int = 11) = lancamento(TipoTransacao.DESPESA, valor, 2026, mes, dia)

    private val novembro = Periodo.mes(YearMonth.of(2026, 11))

    @Test
    fun mesTemBarrasPorSemanaESomaDasBarrasIgualAoTotal() {
        val lista = listOf(ganho(5000.0, 5), gasto(200.0, 3), gasto(300.0, 9), gasto(100.0, 30))
        val r = calcularFluxo(lista, emptyList(), novembro)
        assertEquals(GranularidadeAportes.SEMANA, r.granularidade)
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), r.barras.map { it.rotulo })
        assertEquals(5000.0, r.ganhos, 0.001)
        assertEquals(600.0, r.gastos, 0.001)
        assertEquals(4400.0, r.saldo, 0.001)
        assertEquals(r.ganhos, r.barras.sumOf { it.ganhos }, 0.001)
        assertEquals(r.gastos, r.barras.sumOf { it.gastos }, 0.001)
        assertEquals(200.0, r.barras[0].gastos, 0.001) // dia 3
        assertEquals(5000.0, r.barras[0].ganhos, 0.001) // dia 5
        assertEquals(300.0, r.barras[1].gastos, 0.001) // dia 9
        assertEquals(100.0, r.barras[4].gastos, 0.001) // dia 30
    }

    @Test
    fun periodoCurtoViraUmaBarraPorDia() {
        val periodo = Periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 9))
        val r = calcularFluxo(listOf(gasto(50.0, 6), ganho(10.0, 9)), emptyList(), periodo)
        assertEquals(GranularidadeAportes.DIA, r.granularidade)
        assertEquals(listOf("5", "6", "7", "8", "9"), r.barras.map { it.rotulo })
        assertEquals(50.0, r.barras[1].gastos, 0.001)
        assertEquals(10.0, r.barras[4].ganhos, 0.001)
    }

    @Test
    fun periodoLongoViraUmaBarraPorMes() {
        val periodo = Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30))
        val lista = listOf(ganho(100.0, 10, 9), gasto(40.0, 10, 10), gasto(60.0, 10, 11))
        val r = calcularFluxo(lista, emptyList(), periodo)
        assertEquals(GranularidadeAportes.MES, r.granularidade)
        assertEquals(listOf("Set", "Out", "Nov"), r.barras.map { it.rotulo })
        assertEquals(100.0, r.barras[0].saldo, 0.001)
        assertEquals(-40.0, r.barras[1].saldo, 0.001)
        assertEquals(-60.0, r.barras[2].saldo, 0.001)
    }

    @Test
    fun dataForaDoPeriodoEntraNaBarraMaisProxima() {
        // Compra no crédito feita em outubro, na fatura de novembro: não se perde do total
        val r = calcularFluxo(listOf(gasto(80.0, 28, 10), gasto(20.0, 2, 12)), emptyList(), novembro)
        assertEquals(100.0, r.gastos, 0.001)
        assertEquals(80.0, r.barras.first().gastos, 0.001)
        assertEquals(20.0, r.barras.last().gastos, 0.001)
        assertEquals(r.gastos, r.barras.sumOf { it.gastos }, 0.001)
    }

    @Test
    fun periodoAnteriorAlimentaAVariacaoERotulo() {
        val atual = listOf(ganho(1000.0, 5), gasto(400.0, 6))
        val anterior = listOf(ganho(900.0, 5, 10), gasto(500.0, 6, 10))
        val r = calcularFluxo(atual, anterior, novembro)
        assertEquals(600.0, r.saldo, 0.001)
        assertEquals(400.0, r.saldoAnterior, 0.001)
        assertEquals("out", r.rotuloAnterior)
        assertTrue(r.temBase)

        val tresMeses = calcularFluxo(emptyList(), emptyList(), Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30)))
        assertEquals("período anterior", tresMeses.rotuloAnterior)
    }

    @Test
    fun semMovimentoNemBase() {
        val r = calcularFluxo(emptyList(), emptyList(), novembro)
        assertFalse(r.temMovimento)
        assertFalse(r.temBase)
        assertEquals(5, r.barras.size)
    }

    @Test
    fun percentualPoupadoSomeSemGanhosEFicaNegativoQuandoGastaMais() {
        assertEquals(18, percentualPoupado(1000.0, 820.0))
        assertEquals(-50, percentualPoupado(1000.0, 1500.0))
        assertNull(percentualPoupado(0.0, 100.0))
    }
}
