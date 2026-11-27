package com.example.financacelular.ia

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AnaliseDoAssistenteTest {
    private val cfg = GoldenIa.config()
    private val hoje = LocalDate.of(2026, 11, 30)
    private val novembro = YearMonth.of(2026, 11)
    private val outubro = YearMonth.of(2026, 10)
    private val categorias = listOf(
        Categoria(1, "Mercado", TipoTransacao.DESPESA),
        Categoria(2, "Moradia", TipoTransacao.DESPESA),
        Categoria(3, "Salário", TipoTransacao.RECEITA),
    )
    private val lugares = listOf("Padaria Pão", "Mercado Bom", "Farmácia Vida", "Açaí Top", "Lanche Rei")

    private fun despesa(id: Long, data: LocalDate, centavos: Long, descricao: String, categoria: Long = 1, recorrente: Long? = null) =
        Transacao(id = id, valorCentavos = centavos, data = data, categoriaId = categoria, tipo = TipoTransacao.DESPESA, descricao = descricao, recorrenteId = recorrente)

    /** 5 meses (jul a nov), cada um com salário de 4000, aluguel de 3000 (recorrente) e 6 gastos de ~R$ 100 a R$ 140. */
    private fun historico(): List<Transacao> {
        var id = 1L
        val saida = mutableListOf<Transacao>()
        for (m in 7..11) {
            val mes = YearMonth.of(2026, m)
            saida += Transacao(id = id++, valorCentavos = 400000, data = mes.atDay(5), categoriaId = 3, tipo = TipoTransacao.RECEITA, descricao = "Salário")
            saida += despesa(id++, mes.atDay(6), 300000, "Aluguel", 2, recorrente = 1)
            repeat(6) { i -> saida += despesa(id++, mes.atDay(8 + i * 3), 10000L + (i * 7 % 5) * 1000, lugares[i % 5]) }
        }
        return saida
    }

    private val joalheria = despesa(900, LocalDate.of(2026, 10, 20), 500000, "Joalheria Ouro")
    private val posto = despesa(901, LocalDate.of(2026, 11, 12), 180000, "Posto Shell")
    private val tudo = historico() + joalheria + posto

    private fun montar(
        t: List<Transacao>, mes: YearMonth = novembro, dispensados: Set<Long> = emptySet(),
        genericas: Map<Long, String> = emptyMap(), cats: List<Categoria> = categorias,
    ) = AnaliseDoAssistente.montar(t, cats, emptyList(), emptyList(), dispensados, mes, cfg, genericas, hoje)

    private fun avulsas(n: Int) = historico().filter { it.tipo == TipoTransacao.DESPESA && it.recorrenteId == null }.take(n)

    @Test
    fun poucosDadosAbaixoDe20() {
        val poucos = historico().filter { it.tipo == TipoTransacao.DESPESA && it.recorrenteId == null }.take(19)
        val estado = montar(poucos)
        assertEquals(EstadoAssistente.PoucosDados(19, 20), estado)
    }

    @Test
    fun economiaTotalEhSomaDasRecomendacoes() {
        val pronto = montar(tudo) as EstadoAssistente.Pronto
        assertTrue(pronto.recomendacoes.isNotEmpty())
        assertEquals(pronto.recomendacoes.sumOf { it.economiaMensalCentavos }, pronto.economiaTotalCentavos)
    }

    @Test
    fun economiaTotalTemValorConhecido() {
        // Jul a out: renda 4000, despesa 3000 + 700 = 3700 por mês. Guarda 7,5%; para chegar a 10% faltam R$ 100.
        val pronto = montar(historico()) as EstadoAssistente.Pronto
        assertEquals(listOf(CodigoRecomendacao.POUPANCA_BAIXA), pronto.recomendacoes.map { it.codigo })
        assertEquals(10000L, pronto.economiaTotalCentavos)
    }

    @Test
    fun pagamentoDeFaturaECategoriaFaturaNaoContam() {
        val cats = categorias + Categoria(9, "Fatura", TipoTransacao.DESPESA)
        val pagamento = despesa(800, LocalDate.of(2026, 11, 10), 300000, "Pagamento fatura").copy(pagaFaturaDoCartaoId = 1)
        val naFatura = despesa(801, LocalDate.of(2026, 11, 11), 120000, "Ajuste", categoria = 9)
        assertEquals(EstadoAssistente.PoucosDados(19, 20), montar(avulsas(19) + pagamento + naFatura, cats = cats))
    }

    @Test
    fun parcelasDeUmaCompraContamComoUmGasto() {
        val parcelas = (1..3).map { n ->
            despesa(950L + n, LocalDate.of(2026, 10, 25).plusMonths(n - 1L), 100000, "Loja X").copy(
                numeroParcela = n, totalParcelas = 3, grupoParcelamentoId = "g1", dataCompra = LocalDate.of(2026, 10, 20),
            )
        }
        // 18 avulsas + 1 compra = 19 (se as 3 parcelas contassem separadas seriam 21)
        assertEquals(EstadoAssistente.PoucosDados(19, 20), montar(avulsas(18) + parcelas))
    }

    @Test
    fun mesDaCompraParceladaEhODaDataDeCompra() {
        val parcelas = (1..2).map { n ->
            despesa(950L + n, LocalDate.of(2026, 10, 25).plusMonths(n - 1L), 250000, "Loja X").copy(
                numeroParcela = n, totalParcelas = 2, grupoParcelamentoId = "g1", dataCompra = LocalDate.of(2026, 10, 20),
            )
        }
        val base = historico() + parcelas
        val out = montar(base, outubro) as EstadoAssistente.Pronto
        val nov = montar(base, novembro) as EstadoAssistente.Pronto
        val ids = out.atipicosDoMes.map { it.atipico.transacaoId }
        assertTrue(951L in ids)
        assertEquals(LocalDate.of(2026, 10, 20), out.atipicosDoMes.first { it.atipico.transacaoId == 951L }.data)
        assertTrue(nov.atipicosDoMes.none { it.atipico.transacaoId == 951L })
    }

    @Test
    fun atipicosDoMesSoDoMesGlobal() {
        val nov = montar(tudo, novembro) as EstadoAssistente.Pronto
        assertEquals(listOf(901L), nov.atipicosDoMes.map { it.atipico.transacaoId })
        assertEquals("Posto Shell", nov.atipicosDoMes.single().descricao)
        assertEquals("Mercado", nov.atipicosDoMes.single().nomeCategoria)
        val out = montar(tudo, outubro) as EstadoAssistente.Pronto
        assertEquals(listOf(900L), out.atipicosDoMes.map { it.atipico.transacaoId })
    }

    @Test
    fun dispensadoSomeDaLista() {
        val pronto = montar(tudo, novembro, dispensados = setOf(901L)) as EstadoAssistente.Pronto
        assertTrue(pronto.atipicosDoMes.isEmpty())
    }

    @Test
    fun funcionaSemIa() {
        val pronto = montar(tudo, novembro, genericas = emptyMap()) as EstadoAssistente.Pronto
        assertEquals(1, pronto.atipicosDoMes.size)
        assertTrue(pronto.recomendacoes.any { it.codigo == CodigoRecomendacao.POUPANCA_BAIXA })
    }
}
