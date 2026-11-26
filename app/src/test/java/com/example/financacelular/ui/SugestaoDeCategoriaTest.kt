package com.example.financacelular.ui

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.ia.Probabilidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SugestaoDeCategoriaTest {
    private val a = Categoria(id = 1, nome = "A", tipo = TipoTransacao.DESPESA)
    private val b = Categoria(id = 2, nome = "B", tipo = TipoTransacao.DESPESA)
    private val c = Categoria(id = 3, nome = "C", tipo = TipoTransacao.DESPESA)
    private val todas = listOf(a, b, c)

    @Test
    fun memoriaVenceIa() {
        assertEquals(a, escolherSugestao(a, Probabilidade(2, 0.9f), todas, null))
    }

    @Test
    fun iaPreencheQuandoNaoHaMemoria() {
        assertEquals(b, escolherSugestao(null, Probabilidade(2, 0.9f), todas, null))
    }

    @Test
    fun naoTrocaEscolhaDaPessoa() {
        assertEquals(c, escolherSugestao(a, Probabilidade(2, 0.9f), todas, c))
        assertEquals(c, escolherSugestao(null, Probabilidade(2, 0.9f), todas, c))
    }

    @Test
    fun iaApontandoCategoriaInexistenteEhIgnorada() {
        assertNull(escolherSugestao(null, Probabilidade(99, 0.9f), todas, null))
    }
}
