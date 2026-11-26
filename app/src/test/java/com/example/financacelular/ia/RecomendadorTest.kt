package com.example.financacelular.ia

import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class RecomendadorTest {
    private val base = YearMonth.of(2026, 4)
    private fun meses(n: Int) = List(n) { base.plusMonths(it.toLong()) }

    private val restaurantes = 1L
    private val mercado = 2L
    private val pet = 3L
    private val nomes = mapOf(restaurantes to "Restaurantes", mercado to "Mercado", pet to "Pet")
    private val genericas = mapOf(restaurantes to "Restaurantes e Delivery", mercado to "Alimentação")

    /** Renda e despesas por categoria iguais em [n] meses; valores em reais para ler melhor. */
    private fun entrada(
        n: Int = 6,
        renda: Long = 4000,
        gastos: Map<Long, Long> = emptyMap(),
        series: Map<Long, List<Long>> = emptyMap(),
        recorrentes: List<DespesaRecorrente> = emptyList(),
        orcamentos: List<Orcamento> = emptyList(),
        atipicos: List<Atipico> = emptyList(),
    ): EntradaRecomendador {
        val ms = meses(n)
        val despesas = ms.mapIndexed { i, m ->
            m to (gastos.mapValues { it.value * 100 } + series.mapValues { it.value[i] * 100 })
        }.toMap()
        return EntradaRecomendador(ms, despesas, ms.associateWith { renda * 100 }, recorrentes, orcamentos, atipicos, genericas, nomes)
    }

    private fun Collection<Recomendacao>.codigos() = map { it.codigo }
    private fun recorrente(i: Int, reais: Long) = DespesaRecorrente(nome = "R$i", valorCentavos = reais * 100, categoriaId = 1, diaDoMes = 5)

    @Test
    fun textoExatoDaPoupanca() {
        val r = Recomendador.recomendar(entrada(renda = 4000, gastos = mapOf(mercado to 3800))).single()
        assertEquals(CodigoRecomendacao.POUPANCA_BAIXA, r.codigo)
        assertEquals(Severidade.ALTA, r.severidade)
        assertEquals("Você está guardando 5% da renda", r.titulo)
        assertEquals("Para chegar a 10%, reduza cerca de R$ 200/mês.", r.detalhe)
        assertEquals(20000L, r.economiaMensalCentavos)
    }

    @Test
    fun poupancaNoLimiteNaoDisparaEAbaixoDispara() {
        assertTrue(Recomendador.recomendar(entrada(gastos = mapOf(mercado to 3600))).isEmpty())
        assertEquals(
            listOf(CodigoRecomendacao.POUPANCA_BAIXA),
            Recomendador.recomendar(entrada(gastos = mapOf(mercado to 3601))).codigos(),
        )
    }

    @Test
    fun poupancaNegativaNaoDiz_guardandoNegativo() {
        val r = Recomendador.recomendar(entrada(renda = 1000, gastos = mapOf(mercado to 1200))).single()
        assertEquals("Você gasta 20% a mais do que ganha", r.titulo)
    }

    @Test
    fun semReceitaNaoGeraPoupanca() {
        // Restaurantes em 100% da "renda" e série subindo: nada depende de renda zero
        val e = entrada(renda = 0, series = mapOf(restaurantes to listOf(100L, 200, 300, 400, 500, 600)))
        assertTrue(Recomendador.recomendar(e).isEmpty())
    }

    @Test
    fun zeroMesesCompletosDevolveVazio() {
        val e = entrada(n = 0, gastos = mapOf(mercado to 3800))
        assertTrue(Recomendador.recomendar(e).isEmpty())
    }

    @Test
    fun excessoDeCategoriaNoLimiteNaoDisparaEAcimaDispara() {
        // Restaurantes: limite de 12% de R$ 4.000 = R$ 480; resto folgado para a poupança não disparar
        assertTrue(Recomendador.recomendar(entrada(gastos = mapOf(restaurantes to 480))).isEmpty())
        val r = Recomendador.recomendar(entrada(gastos = mapOf(restaurantes to 640))).single()
        assertEquals(CodigoRecomendacao.EXCESSO_CATEGORIA, r.codigo)
        assertEquals(Severidade.MEDIA, r.severidade)
        assertEquals("Restaurantes: 16% da renda", r.titulo)
        assertEquals("Média de R$ 640/mês; referência de 12% da renda.", r.detalhe)
        assertEquals("Comparamos sua média de 6 meses (R$ 640) com 12% da sua renda média (R$ 4.000).", r.porQue)
        assertEquals(16000L, r.economiaMensalCentavos)
        assertEquals(restaurantes, r.categoriaId)
    }

    @Test
    fun categoriaNaoDiscricionariaOuSemMapaNaoGeraExcesso() {
        // Mercado mapeia para "Alimentação" (não discricionária); Pet não tem genérica
        assertTrue(Recomendador.recomendar(entrada(gastos = mapOf(mercado to 1500, pet to 1500))).isEmpty())
    }

    @Test
    fun categoriaPersonalizadaNaoMapeadaNaoGeraExcesso() {
        val e = entrada(gastos = mapOf(pet to 1200)).copy(genericaDaCategoria = emptyMap())
        assertTrue(Recomendador.recomendar(e).codigos().none { it == CodigoRecomendacao.EXCESSO_CATEGORIA })
    }

    @Test
    fun tendenciaUsaMinimosQuadrados() {
        assertEquals(25.0, Recomendador.inclinacao(listOf(100.0, 120.0, 150.0, 170.0, 200.0)), 1e-9)
    }

    @Test
    fun tendenciaAltaDisparaComTextoEPoucosMesesNao() {
        val serie = listOf(100L, 100, 100, 200, 200, 200)
        val r = Recomendador.recomendar(entrada(renda = 5000, series = mapOf(restaurantes to serie))).single()
        assertEquals(CodigoRecomendacao.TENDENCIA_ALTA, r.codigo)
        assertEquals("Restaurantes em alta", r.titulo)
        assertEquals("R$ 200 nos últimos meses, contra R$ 100 antes (+100%).", r.detalhe)
        assertEquals(10000L, r.economiaMensalCentavos)
        // Mesma série com 3 meses: abaixo do mínimo de 4
        val curta = entrada(n = 3, renda = 10000, series = mapOf(restaurantes to listOf(100L, 200, 300)))
        assertTrue(Recomendador.recomendar(curta).isEmpty())
    }

    @Test
    fun tendenciaExigeCrescimentoDeMaisDe25PorCento() {
        // 125 / 100 = exatamente 1,25: não dispara; 126 dispara
        assertTrue(Recomendador.recomendar(entrada(n = 4, renda = 1000, series = mapOf(restaurantes to listOf(100L, 100, 125, 125)))).isEmpty())
        val r = Recomendador.recomendar(entrada(n = 4, renda = 1000, series = mapOf(restaurantes to listOf(100L, 100, 126, 126))))
        assertEquals(listOf(CodigoRecomendacao.TENDENCIA_ALTA), r.codigos())
    }

    @Test
    fun tendenciaExigeDiferencaDe15PorMilDaRenda() {
        // Renda 10.000: 1,5% = R$ 150. Subir de 100 para 250 (diferença exata de 150) não dispara; 251 dispara
        fun com(depois: Long) = Recomendador.recomendar(
            entrada(n = 4, renda = 10000, series = mapOf(restaurantes to listOf(100L, 100, depois, depois))),
        ).codigos()
        assertTrue(com(250).isEmpty())
        assertEquals(listOf(CodigoRecomendacao.TENDENCIA_ALTA), com(251))
    }

    @Test
    fun assinaturasCincoNaoSeisSim() {
        val cinco = List(5) { recorrente(it, 30) }
        assertTrue(Recomendador.recomendar(entrada(recorrentes = cinco)).isEmpty())
        val seis = List(6) { recorrente(it, (it + 1) * 10L) } // 10..60, mediana 35
        val r = Recomendador.recomendar(entrada(recorrentes = seis)).single()
        assertEquals(CodigoRecomendacao.ASSINATURAS, r.codigo)
        assertEquals("6 assinaturas (R$ 210/mês)", r.titulo)
        assertEquals("Cancelar 2 delas economiza cerca de R$ 70/mês.", r.detalhe)
        assertEquals(7000L, r.economiaMensalCentavos)
    }

    @Test
    fun assinaturasIgnoramReceitaRecorrente() {
        val receitas = List(6) { recorrente(it, 30).copy(tipo = com.example.financacelular.data.TipoTransacao.RECEITA) }
        assertTrue(Recomendador.recomendar(entrada(recorrentes = receitas)).isEmpty())
    }

    private fun orc(mes: YearMonth, reais: Long, cat: Long = mercado) = Orcamento(categoriaId = cat, anoMes = mes.toString(), valorLimiteCentavos = reais * 100)

    @Test
    fun orcamentoEstouradoEmDoisDeTresDisparaEEmUmNao() {
        val ms = meses(6)
        val serie = listOf(100L, 100, 100, 500, 500, 100) // últimos 3: 500, 500, 100
        val orcamentos = ms.takeLast(3).map { orc(it, 300) }
        val r = Recomendador.recomendar(entrada(series = mapOf(mercado to serie), orcamentos = orcamentos)).single()
        assertEquals(CodigoRecomendacao.ORCAMENTO_ESTOURADO, r.codigo)
        assertEquals("Mercado passou do orçamento", r.titulo)
        assertEquals("Excedeu em 2 dos últimos 3 meses, em média R$ 200.", r.detalhe)
        assertEquals(20000L, r.economiaMensalCentavos)
        val umSo = listOf(100L, 100, 100, 500, 100, 100)
        assertTrue(Recomendador.recomendar(entrada(series = mapOf(mercado to umSo), orcamentos = orcamentos)).isEmpty())
    }

    @Test
    fun orcamentoIgualAoGastoNaoContaComoEstouro() {
        val orcamentos = meses(6).takeLast(3).map { orc(it, 500) }
        val e = entrada(series = mapOf(mercado to listOf(500L, 500, 500, 500, 500, 500)), orcamentos = orcamentos)
        assertTrue(Recomendador.recomendar(e).isEmpty())
    }

    private fun atipico(valor: Long, motivo: MotivoAtipico = MotivoAtipico.VALOR_ALTO_NO_LOCAL, ref: Long? = 0) =
        Atipico(1, 0.7, motivo, valor * 100, ref?.times(100))

    @Test
    fun atipicosNoLimiteDe4PorCentoDisparaEAbaixoNao() {
        // Despesa média R$ 1.000 (Mercado): limite = R$ 40/mês = R$ 240 em 6 meses
        val gastos = mapOf(mercado to 1000L)
        assertEquals(
            listOf(CodigoRecomendacao.GASTOS_ATIPICOS),
            Recomendador.recomendar(entrada(gastos = gastos, atipicos = listOf(atipico(240)))).codigos(),
        )
        assertTrue(Recomendador.recomendar(entrada(gastos = gastos, atipicos = listOf(atipico(239)))).isEmpty())
    }

    @Test
    fun atipicosUsamValorMenosReferenciaEContamDuplicados() {
        val gastos = mapOf(mercado to 1000L)
        val atipicos = listOf(
            atipico(500, ref = 200), // 300
            atipico(300, MotivoAtipico.DUPLICADO, ref = null), // 300
        )
        val r = Recomendador.recomendar(entrada(gastos = gastos, atipicos = atipicos)).single()
        assertEquals(Severidade.ALTA, r.severidade)
        assertEquals("2 gastos fora do seu padrão", r.titulo)
        assertEquals("Impacto de R$ 100/mês; 1 possível cobrança duplicada.", r.detalhe)
        assertEquals(10000L, r.economiaMensalCentavos)
    }

    @Test
    fun atipicoAbaixoDaReferenciaNaoTemImpacto() {
        val e = entrada(gastos = mapOf(mercado to 1000L), atipicos = listOf(atipico(100, ref = 300)))
        assertTrue(Recomendador.recomendar(e).isEmpty())
    }

    @Test
    fun ordenaPorSeveridadeEEconomia() {
        // Poupança (ALTA, R$ 200), atípicos (ALTA, R$ 100), excesso de Restaurantes (MEDIA, R$ 160) e 6 assinaturas (MEDIA, R$ 70)
        val e = entrada(
            renda = 2000,
            gastos = mapOf(restaurantes to 400, mercado to 1600),
            recorrentes = List(6) { recorrente(it, 35) },
            atipicos = listOf(atipico(600)),
        )
        val r = Recomendador.recomendar(e)
        assertEquals(
            listOf(
                CodigoRecomendacao.POUPANCA_BAIXA, CodigoRecomendacao.GASTOS_ATIPICOS,
                CodigoRecomendacao.EXCESSO_CATEGORIA, CodigoRecomendacao.ASSINATURAS,
            ),
            r.codigos(),
        )
        assertEquals(listOf(Severidade.ALTA, Severidade.ALTA, Severidade.MEDIA, Severidade.MEDIA), r.map { it.severidade })
        assertEquals(r.map { it.economiaMensalCentavos }.take(2), r.take(2).map { it.economiaMensalCentavos }.sortedDescending())
    }

    @Test
    fun mesesSemDadoContamComoZero() {
        // Um mês completo, sem despesa nem renda: não quebra nem recomenda
        val e = EntradaRecomendador(meses(1), emptyMap(), emptyMap(), emptyList(), emptyList(), emptyList(), genericas, nomes)
        assertTrue(Recomendador.recomendar(e).isEmpty())
    }

    @Test
    fun formatadorArredondaAgrupaEMarcaNegativo() {
        assertEquals("R$ 0", formatarReaisInteiros(0))
        assertEquals("R$ 1", formatarReaisInteiros(50))
        assertEquals("R$ 0", formatarReaisInteiros(49))
        assertEquals("R$ 1.234", formatarReaisInteiros(123_449))
        assertEquals("R$ 1.235", formatarReaisInteiros(123_450))
        assertEquals("R$ 1.234.567", formatarReaisInteiros(123_456_700))
        assertEquals("-R$ 1.234", formatarReaisInteiros(-123_400))
    }
}
