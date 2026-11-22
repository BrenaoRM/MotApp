package com.example.financacelular.ui

import com.example.financacelular.data.ResultadoCalendario
import org.junit.Assert.assertEquals
import org.junit.Test

class MensagensAgendaTest {

    @Test
    fun afazerSalvoSemAgenda() {
        assertEquals("Afazer salvo" to false, avisoAfazerSalvo(null))
    }

    @Test
    fun afazerSalvoNaAgenda() {
        assertEquals("Salvo no Google Agenda" to false, avisoAfazerSalvo(ResultadoCalendario.SUCESSO))
    }

    @Test
    fun afazerSemPermissao() {
        assertEquals("Salvo só no app: sem acesso à Agenda" to true, avisoAfazerSalvo(ResultadoCalendario.SEM_PERMISSAO))
    }

    @Test
    fun afazerSemCalendario() {
        assertEquals("Salvo só no app: conecte sua conta Google" to true, avisoAfazerSalvo(ResultadoCalendario.SEM_CALENDARIO))
    }

    @Test
    fun afazerErro() {
        assertEquals("Salvo só no app: falha na Agenda" to true, avisoAfazerSalvo(ResultadoCalendario.ERRO))
    }

    @Test
    fun edicaoSucessoUsaTextoDado() {
        assertEquals("Evento excluído" to false, mensagemEdicao(ResultadoCalendario.SUCESSO, "Evento excluído"))
    }

    @Test
    fun edicaoSemPermissao() {
        assertEquals("Sem acesso à Agenda" to true, mensagemEdicao(ResultadoCalendario.SEM_PERMISSAO, "x"))
    }

    @Test
    fun edicaoSemCalendario() {
        assertEquals("Nenhum calendário conectado" to true, mensagemEdicao(ResultadoCalendario.SEM_CALENDARIO, "x"))
    }

    @Test
    fun edicaoErro() {
        assertEquals("Falha ao alterar na Agenda" to true, mensagemEdicao(ResultadoCalendario.ERRO, "x"))
    }
}
