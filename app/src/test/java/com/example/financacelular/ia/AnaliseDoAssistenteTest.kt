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

    private fun montar(t: List<Transacao>, mes: YearMonth = novembro, dispensados: Set<Long> = emptySet(), genericas: Map<Long, String> = emptyMap()) =
        AnaliseDoAssistente.montar(t, categorias, emptyList(), emptyList(), dispensados, mes, cfg, genericas, hoje)

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
