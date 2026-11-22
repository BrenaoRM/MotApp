package com.example.financacelular.ui

import com.example.financacelular.data.InvestimentoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResgateInvestimentoTest {

    private fun aporte(id: Long, valor: Double, dia: Int) =
        InvestimentoEntity(id = id, nome = "Ativo", categoria = "Renda Fixa", valorInvestido = valor, data = LocalDate.of(2026, 11, dia))

    @Test
    fun resgateTotalComSomaImpreciaApagaTudo() {
        val aportes = listOf(aporte(1, 0.10, 1), aporte(2, 0.70, 2))
        val total = aportes.sumOf { it.valorInvestido }
        assertEquals(emCentavos(total), emCentavos(0.80))
        val acoes = planejarResgate(aportes, 0.80)
        assertEquals(listOf(AcaoResgate.Apagar(aportes[1]), AcaoResgate.Apagar(aportes[0])), acoes)
    }

    @Test
    fun resgateDeTrintaCentavosApagaTudoSemSobra() {
        val aportes = listOf(aporte(1, 0.1, 1), aporte(2, 0.2, 2))
        val acoes = planejarResgate(aportes, 0.30)
        assertEquals(2, acoes.size)
        assertEquals(true, acoes.all { it is AcaoResgate.Apagar })
    }

    @Test
    fun resgateParcialAtualizaOAporteMaisRecente() {
        val aportes = listOf(aporte(1, 100.0, 1), aporte(2, 50.0, 5))
        val acoes = planejarResgate(aportes, 20.0)
        assertEquals(listOf<AcaoResgate>(AcaoResgate.Atualizar(aportes[1], 30.0)), acoes)
    }

    @Test
    fun consomeDoMaisRecenteParaOMaisAntigo() {
        val aportes = listOf(aporte(1, 100.0, 1), aporte(3, 10.0, 9), aporte(2, 50.0, 5))
        val acoes = planejarResgate(aportes, 70.0)
        assertEquals(
            listOf(
                AcaoResgate.Apagar(aportes[1]),
                AcaoResgate.Apagar(aportes[2]),
                AcaoResgate.Atualizar(aportes[0], 90.0)
            ),
            acoes
        )
    }
}
