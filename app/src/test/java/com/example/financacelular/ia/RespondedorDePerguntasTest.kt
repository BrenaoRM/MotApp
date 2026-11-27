package com.example.financacelular.ia

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.FormaPagamento
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RespondedorDePerguntasTest {
    private val hoje = LocalDate.of(2026, 11, 26)
    private val novembro = YearMonth.of(2026, 11)
    private val outubro = YearMonth.of(2026, 10)

    private val mercado = 1L
    private val restaurantes = 2L
    private val transporte = 3L
    private val faturaCat = 4L
    private val salario = 5L
    private val categorias = listOf(
        Categoria(mercado, "Mercado", TipoTransacao.DESPESA),
        Categoria(restaurantes, "Restaurantes", TipoTransacao.DESPESA),
        Categoria(transporte, "Transporte", TipoTransacao.DESPESA),
        Categoria(faturaCat, "Fatura", TipoTransacao.DESPESA),
        Categoria(salario, "Salário", TipoTransacao.RECEITA),
    )

    private var proximoId = 1L
    private fun gasto(reais: Long, cat: Long, mes: YearMonth = novembro, dia: Int = 10, descricao: String? = null) =
        Transacao(
            id = proximoId++, valorCentavos = reais * 100, data = mes.atDay(dia), categoriaId = cat,
            tipo = TipoTransacao.DESPESA, descricao = descricao,
        )

    private fun receita(reais: Long, mes: YearMonth = novembro, descricao: String? = null) = Transacao(
        id = proximoId++, valorCentavos = reais * 100, data = mes.atDay(5), categoriaId = salario,
        tipo = TipoTransacao.RECEITA, descricao = descricao,
    )

    private fun pergunta(i: Intencao, mes: YearMonth = novembro, cat: Long? = null) = Pergunta(i, 0.9f, mes, cat)

    private fun responder(
        p: Pergunta,
        transacoes: List<Transacao> = emptyList(),
        recorrentes: List<DespesaRecorrente> = emptyList(),
        orcamentos: List<Orcamento> = emptyList(),
        recomendacoes: List<Recomendacao> = emptyList(),
        atipicos: List<Atipico> = emptyList(),
    ) = RespondedorDePerguntas.responder(p, transacoes, categorias, recorrentes, orcamentos, recomendacoes, atipicos, hoje)

    private val gastosDeNovembro by lazy {
        listOf(gasto(820, mercado), gasto(540, restaurantes), gasto(310, transporte))
    }

    @Test
    fun totalDoMesIgualAoDaAnalise() {
        val cartao = 9L
        val outroCartao = 8L
        val transacoes = listOf(
            gasto(100, mercado),
            gasto(50, restaurantes),
            // pagamento de fatura: nunca conta (o valor já está nas compras do cartão)
            gasto(900, faturaCat).copy(pagaFaturaDoCartaoId = outroCartao, anoMes = "2026-11"),
            // categoria "Fatura": fora
            gasto(30, faturaCat),
            // compra no cartão com fatura de novembro ainda aberta: ainda não é gasto realizado
            gasto(70, mercado).copy(formaPagamento = FormaPagamento.CARTAO_CREDITO, cartaoId = cartao, anoMes = "2026-11"),
            // compra no cartão com fatura de outubro paga: realizada, no mês de competência da fatura
            gasto(25, mercado, outubro).copy(formaPagamento = FormaPagamento.CARTAO_CREDITO, cartaoId = cartao, anoMes = "2026-10"),
            gasto(5, mercado, outubro).copy(pagaFaturaDoCartaoId = cartao, anoMes = "2026-10"),
            // lançamento futuro: ainda não aconteceu
            gasto(60, mercado, dia = 28),
            // receita não é gasto
            receita(3000),
        )
        assertEquals("Em novembro você gastou R$ 150.", responder(pergunta(Intencao.TOTAL_DO_MES), transacoes))
        assertEquals("Em outubro você gastou R$ 25.", responder(pergunta(Intencao.TOTAL_DO_MES, outubro), transacoes))
    }

    @Test
    fun totalSegueOMesDeCompetenciaDaFatura() {
        // parcela com data em dezembro mas fatura (anoMes) de novembro já paga: conta em novembro, como a Análise
        val cartao = 9L
        val transacoes = listOf(
            gasto(80, mercado, YearMonth.of(2026, 12)).copy(
                formaPagamento = FormaPagamento.CARTAO_CREDITO, cartaoId = cartao, anoMes = "2026-11",
            ),
            gasto(1, faturaCat).copy(pagaFaturaDoCartaoId = cartao, anoMes = "2026-11"),
        )
        assertEquals("Em novembro você gastou R$ 80.", responder(pergunta(Intencao.TOTAL_DO_MES), transacoes))
    }

    @Test
    fun ranking() {
        assertEquals(
            "Em novembro você gastou mais com Mercado: R\$ 820 (49%). Depois vêm Restaurantes (R\$ 540) e Transporte (R\$ 310).",
            responder(pergunta(Intencao.RANKING_CATEGORIAS), gastosDeNovembro),
        )
    }

    @Test
    fun rankingComUmaEDuasCategorias() {
        assertEquals(
            "Em novembro você gastou mais com Mercado: R\$ 820 (100%).",
            responder(pergunta(Intencao.RANKING_CATEGORIAS), listOf(gasto(820, mercado))),
        )
        assertEquals(
            "Em novembro você gastou mais com Mercado: R\$ 820 (60%). Depois vem Restaurantes (R\$ 540).",
            responder(pergunta(Intencao.RANKING_CATEGORIAS), listOf(gasto(820, mercado), gasto(540, restaurantes))),
        )
    }

    @Test
    fun rankingIgnoraFaturaEPagamentoDeFatura() {
        val transacoes = gastosDeNovembro + gasto(5000, faturaCat) +
            gasto(700, mercado).copy(pagaFaturaDoCartaoId = 9L, anoMes = "2026-11")
        assertEquals(
            "Em novembro você gastou mais com Mercado: R\$ 820 (49%). Depois vêm Restaurantes (R\$ 540) e Transporte (R\$ 310).",
            responder(pergunta(Intencao.RANKING_CATEGORIAS), transacoes),
        )
    }

    @Test
    fun rankingComMaisDeTresCategoriasMostraSoTres() {
        val extra = Categoria(8, "Pet", TipoTransacao.DESPESA)
        val texto = RespondedorDePerguntas.responder(
            pergunta(Intencao.RANKING_CATEGORIAS), gastosDeNovembro + gasto(100, 8), categorias + extra,
            emptyList(), emptyList(), emptyList(), emptyList(), hoje,
        )
        assertEquals(
            "Em novembro você gastou mais com Mercado: R\$ 820 (46%). Depois vêm Restaurantes (R\$ 540) e Transporte (R\$ 310).",
            texto,
        )
    }

    @Test
    fun compararMesAnterior() {
        val transacoes = gastosDeNovembro.take(2) + listOf(gasto(600, mercado, outubro), gasto(560, restaurantes, outubro))
        // novembro 1.360, outubro 1.160: +R$ 200; Mercado subiu 220, Restaurantes caiu 20
        assertEquals(
            "Em novembro você gastou R\$ 200 a mais que em outubro. Quem mais subiu foi Mercado (+R\$ 220).",
            responder(pergunta(Intencao.COMPARAR_MES_ANTERIOR), transacoes),
        )
    }

    @Test
    fun compararMesAnteriorQuandoGastouMenos() {
        val transacoes = listOf(gasto(500, mercado), gasto(800, mercado, outubro), gasto(100, restaurantes, outubro))
        assertEquals(
            "Em novembro você gastou R\$ 400 a menos que em outubro.",
            responder(pergunta(Intencao.COMPARAR_MES_ANTERIOR), transacoes),
        )
    }

    @Test
    fun compararMesAnteriorSemGastosNoAnterior() {
        assertEquals(
            "Não encontrei gastos em outubro para comparar.",
            responder(pergunta(Intencao.COMPARAR_MES_ANTERIOR), gastosDeNovembro),
        )
    }

    @Test
    fun gastoEmCategoria() {
        assertEquals(
            "Em novembro você gastou R\$ 820 com Mercado (49% do total).",
            responder(pergunta(Intencao.GASTO_EM_CATEGORIA, cat = mercado), gastosDeNovembro),
        )
        assertEquals(
            "Em novembro você não gastou com Pet.",
            RespondedorDePerguntas.responder(
                pergunta(Intencao.GASTO_EM_CATEGORIA, cat = 8), gastosDeNovembro, categorias + Categoria(8, "Pet", TipoTransacao.DESPESA),
                emptyList(), emptyList(), emptyList(), emptyList(), hoje,
            ),
        )
    }

    @Test
    fun gastoEmCategoriaSemCategoria() {
        assertEquals(
            "De qual categoria? Tente: \"quanto gastei com Mercado\".",
            responder(pergunta(Intencao.GASTO_EM_CATEGORIA, cat = null), gastosDeNovembro),
        )
    }

    @Test
    fun gastoEmCategoriaQueNaoExisteMais() {
        assertEquals(
            "De qual categoria? Tente: \"quanto gastei com Mercado\".",
            responder(pergunta(Intencao.GASTO_EM_CATEGORIA, cat = 999), gastosDeNovembro),
        )
    }

    @Test
    fun mesSemGastos() {
        assertEquals("Não encontrei gastos em outubro.", responder(pergunta(Intencao.TOTAL_DO_MES, outubro), gastosDeNovembro))
        assertEquals("Não encontrei gastos em outubro.", responder(pergunta(Intencao.RANKING_CATEGORIAS, outubro), gastosDeNovembro))
        assertEquals("Não encontrei gastos em outubro.", responder(pergunta(Intencao.MAIOR_GASTO, outubro), gastosDeNovembro))
        assertEquals("Não encontrei gastos em outubro de 2025.", responder(pergunta(Intencao.TOTAL_DO_MES, YearMonth.of(2025, 10)), gastosDeNovembro))
    }

    @Test
    fun desconhecida() {
        assertEquals(
            "Ainda não sei responder isso. Tente: \"Onde estou gastando mais?\"",
            responder(pergunta(Intencao.DESCONHECIDA)),
        )
    }

    @Test
    fun maiorGasto() {
        val transacoes = gastosDeNovembro + gasto(450, transporte, dia = 5, descricao = "Posto Shell")
        assertEquals(
            "Seu maior gasto de novembro foi R\$ 820: Mercado, no dia 10.",
            responder(pergunta(Intencao.MAIOR_GASTO), transacoes),
        )
        assertEquals(
            "Seu maior gasto de novembro em Transporte foi R\$ 450: Posto Shell, no dia 5.",
            responder(pergunta(Intencao.MAIOR_GASTO, cat = transporte), transacoes),
        )
    }

    @Test
    fun orcamentoRestante() {
        val orcamentos = listOf(
            Orcamento(1, mercado, "2026-11", 100000), Orcamento(2, restaurantes, "2026-11", 50000),
            Orcamento(3, mercado, "2026-10", 1),
        )
        // limite 1.500, gasto 820 + 540 = 1.360: restam 140 (91% usado); restaurantes está a 108%
        assertEquals(
            "Do orçamento de novembro ainda restam R\$ 140 de R\$ 1.500 (91% usado). Mais perto do limite: Restaurantes (108%).",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE), gastosDeNovembro, orcamentos = orcamentos),
        )
        assertEquals(
            "Do orçamento de Mercado em novembro ainda restam R\$ 180 de R\$ 1.000 (82% usado).",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE, cat = mercado), gastosDeNovembro, orcamentos = orcamentos),
        )
    }

    @Test
    fun orcamentoEstourado() {
        val orcamentos = listOf(Orcamento(1, restaurantes, "2026-11", 50000))
        assertEquals(
            "Você passou R\$ 40 do orçamento de novembro (R\$ 500).",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE), gastosDeNovembro, orcamentos = orcamentos),
        )
    }

    @Test
    fun semOrcamento() {
        assertEquals(
            "Você não definiu orçamento para novembro.",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE), gastosDeNovembro),
        )
    }

    @Test
    fun assinaturas() {
        fun rec(nome: String, reais: Long, tipo: TipoTransacao = TipoTransacao.DESPESA) =
            DespesaRecorrente(nome = nome, valorCentavos = reais * 100, categoriaId = 1, diaDoMes = 5, tipo = tipo)
        assertEquals(
            "Você tem 2 assinaturas, R\$ 70 por mês: Netflix (R\$ 40) e Spotify (R\$ 30).",
            responder(pergunta(Intencao.ASSINATURAS), recorrentes = listOf(rec("Spotify", 30), rec("Netflix", 40), rec("Salário", 3000, TipoTransacao.RECEITA))),
        )
        assertEquals(
            "Você tem 4 assinaturas, R\$ 100 por mês. As maiores: Netflix (R\$ 40), Spotify (R\$ 30) e Gym (R\$ 20).",
            responder(pergunta(Intencao.ASSINATURAS), recorrentes = listOf(rec("A", 10), rec("Gym", 20), rec("Spotify", 30), rec("Netflix", 40))),
        )
        assertEquals("Não encontrei assinaturas.", responder(pergunta(Intencao.ASSINATURAS)))
    }

    @Test
    fun poupanca() {
        val transacoes = gastosDeNovembro.take(2) + receita(2000)
        assertEquals(
            "Em novembro você recebeu R\$ 2.000 e gastou R\$ 1.360: sobraram R\$ 640 (32% da renda).",
            responder(pergunta(Intencao.POUPANCA), transacoes),
        )
        assertEquals(
            "Em novembro você recebeu R\$ 1.000 e gastou R\$ 1.360: faltaram R\$ 360.",
            responder(pergunta(Intencao.POUPANCA), gastosDeNovembro.take(2) + receita(1000)),
        )
    }

    @Test
    fun semReceitaNaoGeraPoupanca() {
        assertEquals(
            "Não encontrei receitas em novembro, então não dá para calcular quanto sobrou.",
            responder(pergunta(Intencao.POUPANCA), gastosDeNovembro),
        )
    }

    @Test
    fun receitas() {
        val transacoes = listOf(receita(3000, descricao = "Empresa"), receita(500, descricao = "Freela"))
        assertEquals(
            "Em novembro você recebeu R\$ 3.500. A maior entrada foi R\$ 3.000 (Empresa).",
            responder(pergunta(Intencao.RECEITAS), transacoes),
        )
        assertEquals("Não encontrei receitas em novembro.", responder(pergunta(Intencao.RECEITAS), gastosDeNovembro))
    }

    @Test
    fun mediaMensal() {
        // meses fechados: setembro 1.000, outubro 2.000 (agosto sem gasto); novembro (corrente) não entra
        val transacoes = listOf(
            gasto(1000, mercado, YearMonth.of(2026, 9)), gasto(1500, mercado, outubro), gasto(500, restaurantes, outubro),
        ) + gastosDeNovembro
        assertEquals(
            "Em média você gasta R\$ 1.500 por mês (últimos 2 meses).",
            responder(pergunta(Intencao.MEDIA_MENSAL), transacoes),
        )
        assertEquals(
            "Em média você gasta R\$ 1.250 por mês com Mercado (últimos 2 meses).",
            responder(pergunta(Intencao.MEDIA_MENSAL, cat = mercado), transacoes),
        )
        assertEquals(
            "Ainda não há um mês fechado com gastos para calcular a média.",
            responder(pergunta(Intencao.MEDIA_MENSAL), gastosDeNovembro),
        )
    }

    @Test
    fun comoEconomizarUsaRecomendacoes() {
        fun rec(titulo: String) = Recomendacao(CodigoRecomendacao.ASSINATURAS, Severidade.MEDIA, titulo, "", "", 0, null)
        val recs = listOf(rec("Revise assinaturas"), rec("Reduza Restaurantes"), rec("Defina orçamentos"), rec("Quarta"))
        assertEquals(
            "Para economizar: Revise assinaturas; Reduza Restaurantes; Defina orçamentos.",
            responder(pergunta(Intencao.COMO_ECONOMIZAR), recomendacoes = recs),
        )
        assertEquals("Não vi nada para ajustar agora.", responder(pergunta(Intencao.COMO_ECONOMIZAR)))
    }

    @Test
    fun atipicosUsaDetector() {
        val posto = gasto(450, transporte, dia = 5, descricao = "Posto Shell")
        val dupla = gasto(60, restaurantes, dia = 6, descricao = "iFood")
        val deOutubro = gasto(999, mercado, outubro)
        val semDescricao = gasto(300, mercado, dia = 7)
        val transacoes = listOf(posto, dupla, deOutubro, semDescricao)
        val atipicos = listOf(
            Atipico(posto.id, 0.8, MotivoAtipico.VALOR_ALTO_NO_LOCAL, 45000, 15000),
            Atipico(dupla.id, 0.7, MotivoAtipico.DUPLICADO, 6000, null),
            Atipico(deOutubro.id, 0.9, MotivoAtipico.VALOR_ALTO_NA_CATEGORIA, 99900, 20000),
            Atipico(semDescricao.id, 0.6, MotivoAtipico.LOCAL_NOVO_CARO, 30000, null),
            Atipico(-1, 0.5, MotivoAtipico.DUPLICADO, 100, null), // lançamento que não existe mais
        )
        assertEquals(
            "Fora do padrão em novembro: Posto Shell, R\$ 450 (acima do comum no local, típico R\$ 150); " +
                "iFood, R\$ 60 (possível duplicado); Mercado, R\$ 300 (local novo e caro).",
            responder(pergunta(Intencao.ATIPICOS), transacoes, atipicos = atipicos),
        )
        assertEquals("Não vi nada fora do padrão em novembro.", responder(pergunta(Intencao.ATIPICOS), transacoes))
    }

    @Test
    fun atipicosMotivoValorAltoNaCategoria() {
        val t = gasto(800, restaurantes, descricao = "Jantar")
        assertEquals(
            "Fora do padrão em novembro: Jantar, R\$ 800 (acima do comum na categoria, típico R\$ 200).",
            responder(
                pergunta(Intencao.ATIPICOS), listOf(t),
                atipicos = listOf(Atipico(t.id, 0.9, MotivoAtipico.VALOR_ALTO_NA_CATEGORIA, 80000, 20000)),
            ),
        )
    }

    @Test
    fun respostaEmSegundoPlanoTrazTextoESugestoes() = runBlocking {
        val r = RespondedorDePerguntas.responderEmSegundoPlano(
            pergunta(Intencao.TOTAL_DO_MES), gastosDeNovembro, categorias, emptyList(), emptyList(), emptyList(), emptyList(), hoje,
        )
        assertEquals("Em novembro você gastou R\$ 1.670.", r.texto)
        assertTrue(r.sugestoes.isNotEmpty())
        val d = RespondedorDePerguntas.responderEmSegundoPlano(
            pergunta(Intencao.DESCONHECIDA), emptyList(), categorias, emptyList(), emptyList(), emptyList(), emptyList(), hoje,
        )
        assertEquals(Perguntas.SUGESTOES_INICIAIS, d.sugestoes)
    }

    @Test
    fun orcamentoHerdaOLimiteDoMesAnterior() {
        // Definido em setembro, vale em novembro (como a tela de Orçamento): restam 1.000 - 820 = 180
        val orcamentos = listOf(Orcamento(1, mercado, "2026-09", 100000))
        assertEquals(
            "Do orçamento de Mercado em novembro ainda restam R\$ 180 de R\$ 1.000 (82% usado).",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE, cat = mercado), gastosDeNovembro, orcamentos = orcamentos),
        )
    }

    @Test
    fun orcamentoMaisRecenteSubstituiEZeroEhSemOrcamento() {
        val orcamentos = listOf(
            Orcamento(1, mercado, "2026-09", 100000), Orcamento(2, mercado, "2026-10", 0),
            Orcamento(3, restaurantes, "2026-10", 50000),
        )
        // Mercado tem limite 0 (sem orçamento) desde outubro: só Restaurantes conta
        assertEquals(
            "Você passou R\$ 40 do orçamento de novembro (R\$ 500).",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE), gastosDeNovembro, orcamentos = orcamentos),
        )
        assertEquals(
            "Você não definiu orçamento de Mercado para novembro.",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE, cat = mercado), gastosDeNovembro, orcamentos = orcamentos),
        )
    }

    @Test
    fun orcamentoContaOGastoDaFaturaAbertaComoATela() {
        val naFatura = gasto(100, mercado, dia = 28) // data depois de hoje (26): ainda não realizado, mas a tela soma
        val orcamentos = listOf(Orcamento(1, mercado, "2026-11", 100000))
        assertEquals(
            "Do orçamento de Mercado em novembro ainda restam R\$ 80 de R\$ 1.000 (92% usado).",
            responder(pergunta(Intencao.ORCAMENTO_RESTANTE, cat = mercado), gastosDeNovembro + naFatura, orcamentos = orcamentos),
        )
    }
}
