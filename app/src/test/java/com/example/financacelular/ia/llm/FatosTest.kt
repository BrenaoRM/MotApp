package com.example.financacelular.ia.llm

import com.example.financacelular.data.Categoria
import com.example.financacelular.data.DespesaRecorrente
import com.example.financacelular.data.Orcamento
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import com.example.financacelular.ia.Atipico
import com.example.financacelular.ia.CodigoRecomendacao
import com.example.financacelular.ia.Intencao
import com.example.financacelular.ia.MotivoAtipico
import com.example.financacelular.ia.Pergunta
import com.example.financacelular.ia.Recomendacao
import com.example.financacelular.ia.RespondedorDePerguntas
import com.example.financacelular.ia.Severidade
import com.example.financacelular.ia.llm.TipoValor.NUMERO
import com.example.financacelular.ia.llm.TipoValor.PORCENTO
import com.example.financacelular.ia.llm.TipoValor.REAIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FatosTest {
    private val hoje = LocalDate.of(2026, 11, 26)
    private val novembro = YearMonth.of(2026, 11)
    private val outubro = YearMonth.of(2026, 10)

    private val alimentacao = 1L
    private val lazer = 2L
    private val mercado = 3L
    private val pet = 4L
    private val lanche = 5L
    private val salario = 6L
    private val categorias = listOf(
        Categoria(alimentacao, "Alimentação", TipoTransacao.DESPESA),
        Categoria(lazer, "Lazer", TipoTransacao.DESPESA),
        Categoria(mercado, "Mercado", TipoTransacao.DESPESA),
        Categoria(pet, "Pet \"Rex\"", TipoTransacao.DESPESA),
        Categoria(lanche, "🍔 Lanche", TipoTransacao.DESPESA),
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

    private fun fato(
        i: Intencao,
        transacoes: List<Transacao> = emptyList(),
        mes: YearMonth = novembro,
        cat: Long? = null,
        recorrentes: List<DespesaRecorrente> = emptyList(),
        orcamentos: List<Orcamento> = emptyList(),
        recomendacoes: List<Recomendacao> = emptyList(),
        atipicos: List<Atipico> = emptyList(),
    ): Fato {
        val f = RespondedorDePerguntas.fato(
            Pergunta(i, 0.9f, mes, cat), transacoes, categorias, recorrentes, orcamentos, recomendacoes, atipicos, hoje,
        )
        verificarInvariante(f)
        return f
    }

    private val numero = Regex("""\d+(?:[.,]\d+)*""")

    /** Todo número da frase (fora rótulos e período, que o LLM copia por inteiro) tem um item com o mesmo valor. */
    private fun verificarInvariante(f: Fato) {
        var texto = f.frase
        (f.itens.mapNotNull { it.rotulo } + listOfNotNull(f.periodo)).sortedByDescending { it.length }
            .forEach { texto = texto.replace(it, " ") }
        val valores = f.itens.map { it.valor }.toSet()
        numero.findAll(texto).forEach {
            assertTrue("\"${it.value}\" sem item em: ${f.frase}", it.value in valores)
        }
        f.itens.forEach { assertTrue("item \"${it.valor}\" não está na frase: ${f.frase}", f.frase.contains(it.valor)) }
    }

    @Test
    fun totalDoMes() {
        val f = fato(Intencao.TOTAL_DO_MES, listOf(gasto(1000, alimentacao), gasto(234, lazer)))
        assertEquals("Em novembro você gastou R$ 1.234.", f.frase)
        assertEquals("novembro", f.periodo)
        assertEquals(listOf(ItemDoFato(null, REAIS, "1.234")), f.itens)
    }

    @Test
    fun ranking() {
        val extras = listOf(gasto(285, pet), gasto(285, lanche), gasto(285, 7), gasto(285, 8), gasto(285, 9))
        val f = fato(
            Intencao.RANKING_CATEGORIAS,
            listOf(gasto(1200, alimentacao), gasto(500, lazer), gasto(300, mercado)) + extras,
        )
        assertEquals(
            listOf(
                ItemDoFato("Alimentação", REAIS, "1.200"), ItemDoFato("Alimentação", PORCENTO, "35"),
                ItemDoFato("Lazer", REAIS, "500"), ItemDoFato("Mercado", REAIS, "300"),
            ),
            f.itens,
        )
        assertEquals("novembro", f.periodo)
    }

    @Test
    fun gastoEmCategoriaComNomesEspeciais() {
        val transacoes = listOf(gasto(300, pet), gasto(100, lanche))
        val a = fato(Intencao.GASTO_EM_CATEGORIA, transacoes, cat = pet)
        assertEquals(listOf(ItemDoFato("Pet \"Rex\"", REAIS, "300"), ItemDoFato("Pet \"Rex\"", PORCENTO, "75")), a.itens)
        val b = fato(Intencao.GASTO_EM_CATEGORIA, transacoes, cat = lanche)
        assertEquals(listOf(ItemDoFato("🍔 Lanche", REAIS, "100"), ItemDoFato("🍔 Lanche", PORCENTO, "25")), b.itens)
        assertTrue(b.frase.contains("🍔 Lanche"))
    }

    @Test
    fun gastoEmCategoriaSemCategoriaNaoTemItens() {
        val f = fato(Intencao.GASTO_EM_CATEGORIA, listOf(gasto(10, lazer)))
        assertNull(f.periodo)
        assertTrue(f.itens.isEmpty())
    }

    @Test
    fun compararMesAnterior() {
        val f = fato(
            Intencao.COMPARAR_MES_ANTERIOR,
            listOf(gasto(800, alimentacao), gasto(100, lazer), gasto(500, alimentacao, outubro), gasto(100, lazer, outubro)),
        )
        assertEquals(listOf(ItemDoFato(null, REAIS, "300"), ItemDoFato("Alimentação", REAIS, "300")), f.itens)
    }

    @Test
    fun compararComMesAnteriorDeOutroAnoTrazOAnoComoItem() {
        val janeiro = YearMonth.of(2026, 1)
        val dezembro = YearMonth.of(2025, 12)
        val f = fato(
            Intencao.COMPARAR_MES_ANTERIOR, listOf(gasto(500, lazer, janeiro), gasto(200, lazer, dezembro)), mes = janeiro,
        )
        assertEquals("janeiro", f.periodo)
        assertTrue(f.itens.contains(ItemDoFato("dezembro de 2025", NUMERO, "2025")))
    }

    @Test
    fun maiorGasto() {
        val f = fato(Intencao.MAIOR_GASTO, listOf(gasto(90, lazer), gasto(450, pet, dia = 12, descricao = "Ração")))
        assertEquals(listOf(ItemDoFato("Ração", REAIS, "450"), ItemDoFato("Ração", NUMERO, "12")), f.itens)
    }

    @Test
    fun orcamentoRestanteEPassouDoLimite() {
        val orcamentos = listOf(
            Orcamento(1, lazer, "2026-11", 100_000), Orcamento(2, mercado, "2026-11", 50_000),
        )
        val dentro = fato(Intencao.ORCAMENTO_RESTANTE, listOf(gasto(300, lazer), gasto(450, mercado)), orcamentos = orcamentos)
        assertEquals(
            listOf(
                ItemDoFato(null, REAIS, "750"), ItemDoFato(null, REAIS, "1.500"), ItemDoFato(null, PORCENTO, "50"),
                ItemDoFato("Mercado", PORCENTO, "90"),
            ),
            dentro.itens,
        )
        val passou = fato(Intencao.ORCAMENTO_RESTANTE, listOf(gasto(600, mercado)), cat = mercado, orcamentos = orcamentos)
        assertEquals(listOf(ItemDoFato("Mercado", REAIS, "100"), ItemDoFato("Mercado", REAIS, "500")), passou.itens)
    }

    @Test
    fun assinaturas() {
        val lista = listOf(
            DespesaRecorrente(nome = "Netflix", valorCentavos = 5590, categoriaId = lazer, diaDoMes = 5),
            DespesaRecorrente(nome = "Spotify", valorCentavos = 2190, categoriaId = lazer, diaDoMes = 6),
        )
        val f = fato(Intencao.ASSINATURAS, recorrentes = lista)
        assertNull(f.periodo)
        assertEquals(
            listOf(
                ItemDoFato(null, NUMERO, "2"), ItemDoFato(null, REAIS, "78"),
                ItemDoFato("Netflix", REAIS, "56"), ItemDoFato("Spotify", REAIS, "22"),
            ),
            f.itens,
        )
    }

    @Test
    fun poupanca() {
        val f = fato(Intencao.POUPANCA, listOf(receita(4000), gasto(3000, lazer)))
        assertEquals(
            listOf(
                ItemDoFato(null, REAIS, "4.000"), ItemDoFato(null, REAIS, "3.000"),
                ItemDoFato(null, REAIS, "1.000"), ItemDoFato(null, PORCENTO, "25"),
            ),
            f.itens,
        )
        val falta = fato(Intencao.POUPANCA, listOf(receita(1000), gasto(1500, lazer)))
        assertEquals(ItemDoFato(null, REAIS, "500"), falta.itens.last())
    }

    @Test
    fun receitas() {
        val f = fato(Intencao.RECEITAS, listOf(receita(3000, descricao = "Salário"), receita(500, descricao = "Freela")))
        assertEquals(listOf(ItemDoFato(null, REAIS, "3.500"), ItemDoFato("Salário", REAIS, "3.000")), f.itens)
    }

    @Test
    fun mediaMensal() {
        val transacoes = listOf(gasto(900, lazer, outubro), gasto(300, lazer, YearMonth.of(2026, 9)))
        val f = fato(Intencao.MEDIA_MENSAL, transacoes)
        assertNull(f.periodo)
        assertEquals(listOf(ItemDoFato(null, REAIS, "600"), ItemDoFato(null, NUMERO, "2")), f.itens)
        val um = fato(Intencao.MEDIA_MENSAL, listOf(gasto(900, pet, outubro)), cat = pet)
        assertEquals(listOf(ItemDoFato("Pet \"Rex\"", REAIS, "900")), um.itens)
    }

    @Test
    fun atipicos() {
        val t = gasto(800, lazer, descricao = "Show")
        val a = Atipico(t.id, 3.0, MotivoAtipico.VALOR_ALTO_NA_CATEGORIA, 80_000, 20_000)
        val f = fato(Intencao.ATIPICOS, listOf(t), atipicos = listOf(a))
        assertEquals(listOf(ItemDoFato("Show", REAIS, "800"), ItemDoFato("Show", REAIS, "200")), f.itens)
    }

    @Test
    fun comoEconomizar() {
        val r = Recomendacao(
            CodigoRecomendacao.POUPANCA_BAIXA, Severidade.ALTA, "Você está guardando 12% da renda", "", "", 0, null,
        )
        val f = fato(Intencao.COMO_ECONOMIZAR, recomendacoes = listOf(r))
        assertNull(f.periodo)
        assertEquals(listOf(ItemDoFato(null, PORCENTO, "12")), f.itens)
    }

    @Test
    fun desconhecidaTemFraseSemItens() {
        val f = fato(Intencao.DESCONHECIDA)
        assertTrue(f.itens.isEmpty())
        assertEquals(Intencao.DESCONHECIDA, f.ferramenta)
    }

    @Test
    fun mesFuturoOuVazioDaFatoValidoEmTodasAsIntencoes() {
        val dezembro = YearMonth.of(2026, 12) // depois de "hoje"
        val dadosDeNovembro = listOf(gasto(100, pet), gasto(50, lanche), receita(1000))
        Intencao.entries.forEach { i ->
            listOf(emptyList(), dadosDeNovembro).forEach { dados ->
                listOf(dezembro, YearMonth.of(2024, 3)).forEach { mes ->
                    val f = fato(i, dados, mes = mes, cat = pet)
                    assertEquals(i, f.ferramenta)
                    assertTrue("frase vazia em $i", f.frase.isNotBlank())
                }
            }
        }
        val f = fato(Intencao.TOTAL_DO_MES, emptyList(), mes = dezembro)
        assertEquals("Não encontrei gastos em dezembro.", f.frase)
        assertEquals("dezembro", f.periodo)
        assertTrue(f.itens.isEmpty())
    }

    @Test
    fun jsonUsaOTipoEmMinusculas() {
        val f = fato(Intencao.TOTAL_DO_MES, listOf(gasto(10, lazer)))
        val item = f.paraJson().getJSONArray("itens").getJSONObject(0)
        assertEquals("reais", item.getString("tipo"))
        assertEquals("10", item.getString("valor"))
    }
}
