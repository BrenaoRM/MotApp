package com.example.financacelular.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadoAvisoTest {

    @Test
    fun duracaoSucessoE3000() {
        assertEquals(3000L, duracaoDoAviso(erro = false))
    }

    @Test
    fun duracaoErroE5000() {
        assertEquals(5000L, duracaoDoAviso(erro = true))
    }

    @Test
    fun mostrarDeNovoTrocaId() {
        val estado = EstadoAviso()
        estado.mostrar("Fatura paga", erro = false)
        val primeiro = estado.atual!!
        estado.mostrar("Falha ao enviar o backup", erro = true)
        val segundo = estado.atual!!
        assertEquals("Falha ao enviar o backup", segundo.texto)
        assertEquals(true, segundo.erro)
        assertNotEquals(primeiro.id, segundo.id)
    }

    @Test
    fun mesmoTextoDuasVezesTrocaId() {
        val estado = EstadoAviso()
        estado.mostrar("Transação salva", erro = false)
        val primeiro = estado.atual!!.id
        estado.mostrar("Transação salva", erro = false)
        assertNotEquals(primeiro, estado.atual!!.id)
    }

    @Test
    fun fecharIdAntigoNaoApagaAtual() {
        val estado = EstadoAviso()
        estado.mostrar("A", erro = false)
        val idA = estado.atual!!.id
        estado.mostrar("B", erro = false)
        estado.fechar(idA)
        assertEquals("B", estado.atual!!.texto)
    }

    @Test
    fun fecharIdAtualLimpa() {
        val estado = EstadoAviso()
        estado.mostrar("A", erro = false)
        estado.fechar(estado.atual!!.id)
        assertNull(estado.atual)
    }

    @Test
    fun mensageiroSemErroUsaSucesso() {
        val recebidos = mutableListOf<Pair<String, Boolean>>()
        val mensagem = Mensageiro { texto, erro -> recebidos += texto to erro }
        mensagem("Cartão salvo")
        mensagem("Informe o valor", erro = true)
        assertEquals(listOf("Cartão salvo" to false, "Informe o valor" to true), recebidos)
    }
}
