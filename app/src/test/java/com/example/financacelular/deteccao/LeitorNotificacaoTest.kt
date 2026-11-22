package com.example.financacelular.deteccao

import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.TipoTransacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeitorNotificacaoTest {

    @Test
    fun inter_compra_no_debito() {
        val lida = lerNotificacao("Olá Breno, você acaba de comprar no débito no MP *MAISSABOR o valor de R$ 6,00.")
        assertEquals(
            TransacaoDetectada(6.0, TipoTransacao.DESPESA, "MP *MAISSABOR", FormaPagamento.DEBITO),
            lida
        )
    }

    // Hipótese: a variante de crédito do Inter ainda não foi vista em notificação real.
    @Test
    fun inter_compra_no_credito() {
        val lida = lerNotificacao("Olá Breno, você acaba de comprar no crédito no MP *MAISSABOR o valor de R$ 6,00.")
        assertEquals(FormaPagamento.CARTAO_CREDITO, lida?.formaPagamento)
    }

    @Test
    fun valor_com_milhar() {
        assertEquals(1234.56, lerNotificacao("Compra aprovada de R$ 1.234,56 em LOJA X")?.valor ?: 0.0, 0.001)
    }

    @Test
    fun valor_sem_espaco_e_sem_centavos() {
        val lida = lerNotificacao("Você enviou R$6 para Maria via Pix")
        assertEquals(6.0, lida?.valor ?: 0.0, 0.001)
        assertEquals(TipoTransacao.DESPESA, lida?.tipo)
    }

    @Test
    fun pix_recebido() {
        val lida = lerNotificacao("Você recebeu um Pix de R$ 50,00 de João")
        assertEquals(TipoTransacao.RECEITA, lida?.tipo)
        assertEquals(50.0, lida?.valor ?: 0.0, 0.001)
        assertEquals("João", lida?.descricao)
    }

    @Test
    fun dois_valores_vale_o_do_valor_de() {
        val lida = lerNotificacao("Compra no débito, o valor de R$ 6,00. Saldo de R$ 100,00")
        assertEquals(6.0, lida?.valor ?: 0.0, 0.001)
    }

    @Test
    fun valor_com_espaco_nao_separavel_depois_do_cifrao() {
        val lida = lerNotificacao("Compra aprovada no débito: R$ 45,90 em PADARIA")
        assertEquals(45.9, lida?.valor ?: 0.0, 0.001)
        assertEquals(TipoTransacao.DESPESA, lida?.tipo)
    }

    @Test
    fun inter_com_espaco_nao_separavel_entre_palavras() {
        val lida = lerNotificacao("Olá Breno, você acaba de comprar no débito no MP *MAISSABOR o valor de R$ 6,00.")
        assertEquals(
            TransacaoDetectada(6.0, TipoTransacao.DESPESA, "MP *MAISSABOR", FormaPagamento.DEBITO),
            lida
        )
    }

    @Test
    fun promocao_ignorada() {
        assertNull(lerNotificacao("Ganhe até R$ 50 de cashback no Inter Shop"))
    }

    @Test
    fun valor_zero_ignorado() {
        assertNull(lerNotificacao("Compra no débito o valor de R$ 0,00"))
    }

    @Test
    fun sem_valor_ignorado() {
        assertNull(lerNotificacao("Seu extrato do mês está disponível"))
    }

    @Test
    fun chave_ignora_maiusculas_e_espacos_mas_nao_o_pacote() {
        assertEquals(chaveDeRepeticao("p", "A  B"), chaveDeRepeticao("p", "a b"))
        assertNotEquals(chaveDeRepeticao("p", "a b"), chaveDeRepeticao("q", "a b"))
    }

    @Test
    fun valorParaCampo_usa_virgula_e_nao_usa_milhar() {
        val base = TransacaoDetectada(0.0, TipoTransacao.DESPESA, null, FormaPagamento.DEBITO)
        assertEquals("1234,56", base.copy(valor = 1234.56).valorParaCampo())
        assertEquals("6,00", base.copy(valor = 6.0).valorParaCampo())
    }

    @Test
    fun limite_de_credito_no_texto_nao_marca_compra_no_credito() {
        val lida = lerNotificacao("Compra aprovada no débito de R$ 20,00 em LOJA. Limite de crédito disponível: R$ 900,00")
        assertEquals(FormaPagamento.DEBITO, lida?.formaPagamento)
    }

    @Test
    fun ganho_nunca_e_credito() {
        val lida = lerNotificacao("Você recebeu um Pix de R$ 50,00 de João. Crédito em conta")
        assertEquals(TipoTransacao.RECEITA, lida?.tipo)
        assertEquals(FormaPagamento.DEBITO, lida?.formaPagamento)
    }
}