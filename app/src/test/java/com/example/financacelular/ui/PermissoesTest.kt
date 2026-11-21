package com.example.financacelular.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissoesTest {

    @Test
    fun liberadaGanhaDeTudo() {
        assertEquals(EstadoPermissao.LIBERADA, estadoDePedido(liberada = true, jaPedida = true, podePedirDeNovo = false))
    }

    @Test
    fun nuncaPedidaFicaPendenteMesmoSemExplicacaoDoSistema() {
        // Antes do primeiro pedido o Android também devolve "não mostrar explicação"
        assertEquals(EstadoPermissao.PENDENTE, estadoDePedido(liberada = false, jaPedida = false, podePedirDeNovo = false))
    }

    @Test
    fun negadaUmaVezAindaPodePedirDeNovo() {
        assertEquals(EstadoPermissao.PENDENTE, estadoDePedido(liberada = false, jaPedida = true, podePedirDeNovo = true))
    }

    @Test
    fun pedidaENegadaSemNovoPedidoFicaBloqueada() {
        assertEquals(EstadoPermissao.BLOQUEADA, estadoDePedido(liberada = false, jaPedida = true, podePedirDeNovo = false))
    }

    @Test
    fun cadaPermissaoPertenceAUmGrupoEOrdemDosGruposEFixa() {
        assertEquals(
            listOf(GrupoPermissao.AVISOS, GrupoPermissao.DETECCAO, GrupoPermissao.AGENDA, GrupoPermissao.ATUALIZACOES),
            GrupoPermissao.entries
        )
        assertEquals(6, Permissao.entries.size)
        Permissao.entries.forEach { assertEquals(true, it.grupo in GrupoPermissao.entries) }
    }
}
