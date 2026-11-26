package com.example.financacelular.ia

import com.example.financacelular.data.chaveDoEstabelecimento
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ClassificadorDeCategoriaTest {

    private val atipicos = ConfigAtipicos(0.04f, 200, 256, 42, 20, 3000, 3f, 3, 0.2f, 6)

    /** 4-d, genérico neutro (pesos zero, nomes genéricos longe de tudo → nenhuma categoria mapeada), sem léxico. */
    private val cfg = ConfigIa(
        versaoModelo = 1, dimensao = 4, maxTokens = 64,
        classificador = ConfigClassificador(
            epocas = 200, taxa = 0.5f, lambdaL2 = 1e-4f, pesoPrototipo = 1f, alfaK = 10f,
            temperaturaPrior = 0.05f, limiarSugestao = 0.42f, minimoExemplos = 3,
        ),
        generico = ConfigGenerico(
            nomes = List(9) { "G$it" }, discricionarias = emptyMap(), pesos = List(9) { FloatArray(4) },
            vieses = FloatArray(9), vetoresNomes = List(9) { FloatArray(4) },
            similaridadeMinimaMapeamento = 0.5f, confiancaLexico = 0.9f,
        ),
        atipicos = atipicos, lexico = emptyMap(),
    )

    private fun unit(vararg v: Float): FloatArray {
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    private fun eixo(i: Int, ruido: Float = 0f, semente: Int = 0): FloatArray {
        val r = java.util.Random(semente.toLong())
        return unit(*FloatArray(4) { (if (it == i) 1f else 0f) + ruido * r.nextGaussian().toFloat() })
    }

    private val categorias = listOf(
        CategoriaParaIa(1, "A", eixo(0)), CategoriaParaIa(2, "B", eixo(1)), CategoriaParaIa(3, "C", eixo(2)),
    )

    private fun exemplosEmTornoDosEixos(semente: Int) = (0 until 3).flatMap { c ->
        (0 until 10).map { ExemploRotulado(eixo(c, 0.3f, semente + c * 100 + it), (c + 1).toLong()) }
    }

    @Test
    fun separaClassesSeparaveis() {
        val modelo = ClassificadorDeCategoria(cfg).treinar(exemplosEmTornoDosEixos(1), categorias)
        val novos = exemplosEmTornoDosEixos(5000)
        val acertos = novos.count { modelo.prever(it.vetor).first().categoriaId == it.categoriaId }
        assertEquals(novos.size, acertos)
    }

    @Test
    fun semExemplosUsaPrototipo() {
        val modelo = ClassificadorDeCategoria(cfg).treinar(emptyList(), categorias)
        assertEquals(2L, modelo.prever(unit(0.1f, 1f, 0.2f, 0.1f)).first().categoriaId)
    }

    @Test
    fun probabilidadesSomamUm() {
        val modelo = ClassificadorDeCategoria(cfg).treinar(exemplosEmTornoDosEixos(1), categorias)
        val p = modelo.prever(unit(0.3f, 0.2f, 0.5f, 0.7f))
        assertTrue(abs(p.sumOf { it.p.toDouble() } - 1) < 1e-4)
        assertEquals(p.sortedByDescending { it.p }, p)
    }

    @Test
    fun naoSugereCategoriaQueSumiu() {
        val modelo = ClassificadorDeCategoria(cfg).treinar(
            exemplosEmTornoDosEixos(1), categorias.take(2), memoria = mapOf("loja c" to 3L),
        )
        assertEquals(setOf(1L, 2L), modelo.categorias)
        for (semente in 0 until 20) {
            val p = modelo.prever(eixo(2, 0.2f, semente), "LOJA C")
            assertFalse(p.any { it.categoriaId == 3L })
            assertTrue(modelo.sugerir(eixo(2, 0.2f, semente), "LOJA C")?.categoriaId != 3L)
        }
    }

    @Test
    fun aprendeCorrecao() {
        val petz = unit(0.05f, 0.05f, 0.05f, 1f)
        val emA = List(5) { ExemploRotulado(petz, 1) }
        val classificador = ClassificadorDeCategoria(cfg)
        val antes = classificador.treinar(emA, categorias, mapOf(chaveDoEstabelecimento("PETZ") to 1L))
        assertEquals(1L, antes.prever(petz).first().categoriaId)
        assertEquals(1L, antes.prever(petz, "PETZ").first().categoriaId)
        // A memória guarda o rótulo mais recente: a correção para B vence o que o vetor ainda diz
        val depois = classificador.treinar(emA + List(5) { ExemploRotulado(petz, 2) }, categorias,
            mapOf(chaveDoEstabelecimento("PETZ") to 2L))
        assertEquals(2L, depois.prever(petz, "petz ").first().categoriaId)
        assertEquals(Probabilidade(2L, 1f), depois.sugerir(petz, "Petz"))
    }

    @Test
    fun deterministico() {
        val a = ClassificadorDeCategoria(cfg).treinar(exemplosEmTornoDosEixos(1), categorias)
        val b = ClassificadorDeCategoria(cfg).treinar(exemplosEmTornoDosEixos(1), categorias)
        val x = unit(0.3f, 0.2f, 0.5f, 0.7f)
        assertEquals(a.prever(x), b.prever(x))
    }

    @Test
    fun pessoaNovaSemExemplosSoSugereCategoriaMapeadaOuComExemplos() {
        // Ninguém mapeado e 0 exemplos: o prior pode apontar B, mas não há base para sugerir
        val modelo = ClassificadorDeCategoria(cfg).treinar(emptyList(), categorias)
        assertNull(modelo.sugerir(eixo(1)))
        // Com 3 exemplos de B (minimoExemplos), a sugestão aparece
        val comExemplos = ClassificadorDeCategoria(cfg).treinar(List(3) { ExemploRotulado(eixo(1, 0.1f, it), 2) }, categorias)
        assertEquals(2L, comExemplos.sugerir(eixo(1))?.categoriaId)
    }

    @Test
    fun semCategoriasNaoQuebra() {
        val modelo = ClassificadorDeCategoria(cfg).treinar(exemplosEmTornoDosEixos(1), emptyList())
        assertTrue(modelo.prever(eixo(0)).isEmpty())
        assertNull(modelo.sugerir(eixo(0), "X"))
    }

    @Test
    fun lexicoPorPalavraInteira() {
        val nomes = listOf("Transporte", "Lazer e Assinaturas", "Saúde")
        val lex = mapOf("uber" to "Transporte", "prime" to "Lazer e Assinaturas", "smart fit" to "Saúde")
        assertEquals(1, genericaDoLexico("AMAZON PRIME VIDEO", lex, nomes))
        assertEquals(-1, genericaDoLexico("PRIMEIRO MERCADO", lex, nomes))
        assertEquals(2, genericaDoLexico("SMART FIT ACADEMIA", lex, nomes))
        assertEquals(-1, genericaDoLexico("SMARTFIT", lex, nomes))
        assertEquals(0, genericaDoLexico("Compra cartão UBER* trip São Paulo", lex, nomes))
        assertEquals(-1, genericaDoLexico("", lex, nomes))
    }

    // ------------------------------------------------------------ golden do Python (modelo_app)

    @Test
    fun goldenClassificadorIgualAoPython() {
        val g = JSONObject(GoldenIa.recurso("golden_classificador.json"))
        val config = ConfigIa(
            versaoModelo = 1, dimensao = g.getInt("dimensao"), maxTokens = 64,
            classificador = ConfigClassificador.deJson(g.getJSONObject("classificador")),
            generico = ConfigGenerico.deJson(g.getJSONObject("generico")),
            atipicos = atipicos, lexico = ConfigIa.lexicoDeJson(g.getJSONObject("lexico")),
        )
        val cats = g.getJSONArray("categorias")
        // ids diferentes dos índices, para pegar troca de id por posição
        val categorias = List(cats.length()) {
            val o = cats.getJSONObject(it)
            CategoriaParaIa(100L + it, o.getString("nome"), floats(o.getJSONArray("vetorNome")))
        }
        val diferencas = sortedMapOf<String, Double>()
        fun comparar(nome: String, esperado: JSONArray, obtido: DoubleArray) {
            assertEquals(nome, esperado.length(), obtido.size)
            for (k in obtido.indices) {
                val d = abs(esperado.getDouble(k) - obtido[k])
                diferencas[nome] = maxOf(diferencas[nome] ?: 0.0, d)
                assertTrue("$nome[$k]: esperado ${esperado.getDouble(k)}, obtido ${obtido[k]}", d < 1e-4)
            }
        }

        val casos = g.getJSONArray("casos")
        for (ic in 0 until casos.length()) {
            val caso = casos.getJSONObject(ic)
            val ex = caso.getJSONArray("exemplos")
            val exemplos = List(ex.length()) {
                val o = ex.getJSONObject(it)
                o.getString("descricao") to ExemploRotulado(floats(o.getJSONArray("x")), 100L + o.getInt("categoria"))
            }
            val memoria = LinkedHashMap<String, Long>()
            exemplos.forEach { (descricao, e) -> memoria[chaveDoEstabelecimento(descricao)] = e.categoriaId }
            val modelo = ClassificadorDeCategoria(config).treinar(exemplos.map { it.second }, categorias, memoria)
            val esperado = caso.getJSONObject("esperado")

            val mapa = esperado.getJSONArray("mapa")
            assertEquals((0 until mapa.length()).map { mapa.getInt(it) }, modelo.mapa.toList())

            val consultas = caso.getJSONArray("consultas")
            for (iq in 0 until consultas.length()) {
                val q = consultas.getJSONObject(iq)
                val det = modelo.detalhar(floats(q.getJSONArray("x")), q.getString("descricao"))
                val rotulo = "n=${caso.getInt("n")} '${q.getString("descricao")}'"
                assertEquals("$rotulo genericaLexico", esperado.getJSONArray("genericaLexico").getInt(iq), det.genericaLexico)
                assertEquals("$rotulo rotuloMemoria", esperado.getJSONArray("rotuloMemoria").getInt(iq), det.rotuloMemoria)
                assertEquals("$rotulo sugestao", esperado.getJSONArray("sugestao").getInt(iq), det.sugestao)
                comparar("s", esperado.getJSONArray("s").getJSONArray(iq), det.s)
                comparar("prior", esperado.getJSONArray("prior").getJSONArray(iq), det.prior)
                comparar("pPessoal", esperado.getJSONArray("pPessoal").getJSONArray(iq), det.pPessoal)
                comparar("pFinal", esperado.getJSONArray("pFinal").getJSONArray(iq), det.pFinal)
                val dAlfa = abs(esperado.getDouble("alfa") - det.alfa)
                diferencas["alfa"] = maxOf(diferencas["alfa"] ?: 0.0, dAlfa)
                assertTrue(dAlfa < 1e-4)
                // a API pública devolve o mesmo que o detalhe
                val sugestao = modelo.sugerir(floats(q.getJSONArray("x")), q.getString("descricao"))
                if (det.sugestao < 0) assertNull(sugestao) else assertNotNull(sugestao)
            }
        }
        println("DIFERENCA_MAXIMA_GOLDEN=$diferencas")
    }

    private fun floats(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
}
