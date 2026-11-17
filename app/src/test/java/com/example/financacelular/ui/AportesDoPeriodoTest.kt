package com.example.financacelular.ui

import com.example.financacelular.data.InvestimentoEntity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AportesDoPeriodoTest {

    private fun aporte(categoria: String, valor: Double, ano: Int, mes: Int, dia: Int) =
        InvestimentoEntity(nome = "Ativo $categoria", categoria = categoria, valorInvestido = valor, data = LocalDate.of(ano, mes, dia))

    private val novembro = Periodo.mes(YearMonth.of(2026, 11))

    @Test
    fun granularidadeSegueOTamanhoDoPeriodo() {
        assertEquals(GranularidadeAportes.SEMANA, granularidadeDe(novembro))
        assertEquals(GranularidadeAportes.DIA, granularidadeDe(Periodo(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 15))))
        assertEquals(GranularidadeAportes.MES, granularidadeDe(Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30))))
        assertEquals(GranularidadeAportes.MES, granularidadeDe(Periodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
        // 20 dias soltos: não cabem por dia, e ainda são menos de um mês
        assertEquals(GranularidadeAportes.SEMANA, granularidadeDe(Periodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20))))
    }

    @Test
    fun mesTemSemanasDeSeteDiasEAUltimaMaisCurta() {
        val r = calcularAportes(emptyList(), novembro)
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), r.barras.map { it.rotulo })
        assertEquals(listOf("1–7", "8–14", "15–21", "22–28", "29–30"), r.barras.map { it.subrotulo })
    }

    @Test
    fun somaDasBarrasIgualAoTotalEOsTiposSaemDoMaiorParaOMenor() {
        val lista = listOf(
            aporte("Renda Fixa", 1000.0, 2026, 11, 3),
            aporte("Renda Fixa", 500.0, 2026, 11, 20),
            aporte("Ações", 700.0, 2026, 11, 9),
            aporte("FIIs", 300.0, 2026, 10, 30) // fora do período
        )
        val r = calcularAportes(lista, novembro)
        assertEquals(2200.0, r.total, 0.001)
        assertEquals(r.total, r.barras.sumOf { it.total }, 0.001)
        assertEquals(listOf("Renda Fixa", "Ações"), r.tipos.map { it.nome })
        assertEquals(1000.0, r.barras[0].valores[0], 0.001) // dia 3 cai na S1
        assertEquals(700.0, r.barras[1].valores[1], 0.001) // dia 9 cai na S2
    }

    @Test
    fun maisDeQuatroTiposJuntamOsMenoresEmOutros() {
        val lista = listOf(
            aporte("A", 500.0, 2026, 11, 1), aporte("B", 400.0, 2026, 11, 2), aporte("C", 300.0, 2026, 11, 3),
            aporte("D", 200.0, 2026, 11, 4), aporte("E", 100.0, 2026, 11, 5)
        )
        val r = calcularAportes(lista, novembro)
        assertEquals(listOf("A", "B", "C", "Outros"), r.tipos.map { it.nome })
        assertTrue(r.tipos.last().ehOutros)
        assertEquals(listOf("D", "E"), r.tipos.last().categorias)
        assertEquals(300.0, r.tipos.last().total, 0.001)
        assertEquals(r.total, r.barras.sumOf { it.total }, 0.001)
    }

    @Test
    fun tresMesesTemUmaBarraPorMes() {
        val trimestre = Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30))
        val lista = listOf(aporte("Renda Fixa", 100.0, 2026, 9, 15), aporte("Renda Fixa", 300.0, 2026, 11, 2))
        val r = calcularAportes(lista, trimestre)
        assertEquals(listOf("Set", "Out", "Nov"), r.barras.map { it.rotulo })
        assertEquals(listOf(100.0, 0.0, 300.0), r.barras.map { it.total })
    }

    @Test
    fun quinzeDiasTemUmaBarraPorDiaERotulosAlternados() {
        val periodo = Periodo(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 17))
        val r = calcularAportes(listOf(aporte("Ações", 50.0, 2026, 11, 17)), periodo)
        assertEquals(15, r.barras.size)
        assertEquals(50.0, r.barras.last().total, 0.001)
        assertTrue(r.barras.first().mostrarRotulo)
        assertFalse(r.barras[1].mostrarRotulo)
        assertTrue(r.barras.last().mostrarRotulo)
    }

    @Test
    fun variacaoCompararComOPeriodoAnteriorDoMesmoTamanho() {
        val lista = listOf(aporte("Renda Fixa", 1000.0, 2026, 10, 10), aporte("Renda Fixa", 1390.0, 2026, 11, 10))
        val r = calcularAportes(lista, novembro)
        assertEquals(39, r.variacaoPercentual)
        assertEquals("out", r.rotuloAnterior)
        val tresMeses = calcularAportes(lista, Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30)))
        assertEquals("período anterior", tresMeses.rotuloAnterior)
    }

    @Test
    fun semAportesNoPeriodoOuNoAnteriorNaoHaVariacao() {
        val soNovembro = calcularAportes(listOf(aporte("Ações", 10.0, 2026, 11, 1)), novembro)
        assertNull(soNovembro.variacaoPercentual)
        val vazio = calcularAportes(emptyList(), novembro)
        assertFalse(vazio.temAportes)
        assertNull(vazio.variacaoPercentual)
    }
}
