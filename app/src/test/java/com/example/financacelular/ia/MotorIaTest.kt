package com.example.financacelular.ia

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.Categoria
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.TipoTransacao
import com.example.financacelular.data.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class MotorIaTest {
    private lateinit var banco: AppDatabase
    private lateinit var repo: FinancaRepository
    private val cfg = GoldenIa.config()

    /** Cada palavra vira um vetor fixo (semente = hash); o texto é a soma normalizada. */
    private inner class VetorizadorFalso : Vetorizador {
        override val versao = cfg.versaoModelo
        override fun vetorizar(textos: List<String>) = textos.map { texto ->
            val v = FloatArray(cfg.dimensao)
            texto.split(' ').filter { it.isNotEmpty() }.forEach { palavra ->
                val r = java.util.Random(palavra.hashCode().toLong())
                for (d in v.indices) v[d] += r.nextGaussian().toFloat()
            }
            val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-12f)
            FloatArray(v.size) { v[it] / n }
        }
    }

    @Before
    fun abrir() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        banco = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinancaRepository(banco)
    }

    @After
    fun fechar() = banco.close()

    private fun motor(vetorizador: Vetorizador? = VetorizadorFalso()) = MotorIa({ banco }, cfg, vetorizador)

    private suspend fun categoria(nome: String): Categoria {
        val id = banco.categoriaDao().inserir(Categoria(nome = nome, tipo = TipoTransacao.DESPESA))
        return Categoria(id = id, nome = nome, tipo = TipoTransacao.DESPESA)
    }

    private suspend fun gasto(categoria: Categoria, descricao: String) = banco.transacaoDao().inserirTransacao(
        Transacao(
            valorCentavos = 1000, data = LocalDate.of(2026, 11, 1), categoriaId = categoria.id,
            tipo = TipoTransacao.DESPESA, descricao = descricao, anoMes = "2026-11",
        )
    )

    @Test
    fun modeloIndisponivelSoUsaMemoria() = runBlocking {
        val lazer = categoria("Lazer")
        categoria("Mercado")
        gasto(lazer, "Cinema X")
        val m = motor(vetorizador = null)
        assertFalse(m.disponivel)
        assertEquals(Probabilidade(lazer.id, 1f), m.sugerir("CINEMA  x", TipoTransacao.DESPESA))
        assertNull(m.sugerir("Padaria", TipoTransacao.DESPESA))
        assertEquals(lazer.id, m.ranking("cinema x", TipoTransacao.DESPESA).first())
        assertTrue(m.ranking("Padaria", TipoTransacao.DESPESA).isEmpty())
    }

    @Test
    fun descricaoVaziaOuIgualACategoriaNaoSugere() = runBlocking {
        val lazer = categoria("Lazer")
        gasto(lazer, "Cinema X")
        val m = motor()
        assertTrue(m.disponivel)
        assertNull(m.sugerir("", TipoTransacao.DESPESA))
        assertNull(m.sugerir("   ", TipoTransacao.DESPESA))
        assertNull(m.sugerir("lazer", TipoTransacao.DESPESA))
        // descrição só com números/boilerplate: sem texto para o modelo, sem memória → nada
        assertNull(m.sugerir("PIX 123", TipoTransacao.DESPESA))
        assertTrue(m.ranking("", TipoTransacao.DESPESA).isEmpty())
    }

    @Test
    fun naoSugereCategoriaApagadaMesmoSemInvalidar() = runBlocking {
        val mercado = categoria("Mercado")
        val transporte = categoria("Transporte")
        repeat(3) { gasto(transporte, "Loja X") }
        val m = motor()
        assertEquals(transporte.id, m.sugerir("Loja X", TipoTransacao.DESPESA)?.categoriaId)
        // Move os lançamentos para Mercado e apaga Transporte; o motor percebe pela lista de categorias
        repo.moverEExcluirCategoria(transporte, mercado)
        assertEquals(mercado.id, m.sugerir("Loja X", TipoTransacao.DESPESA)?.categoriaId)
        assertFalse(transporte.id in m.ranking("Loja X", TipoTransacao.DESPESA))
        assertFalse(transporte.id in m.ranking("Uber", TipoTransacao.DESPESA))
    }

    @Test
    fun invalidarRetreinaComORotuloMaisRecente() = runBlocking {
        val lazer = categoria("Lazer")
        val trabalho = categoria("Trabalho")
        gasto(lazer, "Uber trip")
        val m = motor()
        assertEquals(lazer.id, m.sugerir("UBER TRIP", TipoTransacao.DESPESA)?.categoriaId)
        gasto(trabalho, "Uber Trip")
        m.invalidar()
        assertEquals(Probabilidade(trabalho.id, 1f), m.sugerir("uber trip", TipoTransacao.DESPESA))
    }

    @Test
    fun pessoaNovaSemLancamentos() = runBlocking {
        val ids = listOf(categoria("Mercado"), categoria("Transporte"), categoria("Lazer")).map { it.id }.toSet()
        categoria("Fatura")
        val m = motor()
        val sugestao = m.sugerir("Padaria do Zé", TipoTransacao.DESPESA)
        assertTrue(sugestao == null || sugestao.categoriaId in ids)
        assertEquals(ids, m.ranking("Padaria do Zé", TipoTransacao.DESPESA).toSet())
        assertTrue(m.ranking("Salário", TipoTransacao.RECEITA).isEmpty())
    }

    @Test
    fun cancelarQuemPediuNaoDescartaOTreino() = runBlocking {
        val lazer = categoria("Lazer")
        categoria("Mercado")
        gasto(lazer, "Cinema X")
        val comecou = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val falso = VetorizadorFalso()
        // O primeiro lote (o do treino) fica preso até o teste cancelar quem pediu
        val m = motor(object : Vetorizador {
            override val versao = falso.versao
            override fun vetorizar(textos: List<String>): List<FloatArray> {
                if (comecou.count > 0) {
                    comecou.countDown()
                    liberar.await(10, TimeUnit.SECONDS)
                }
                return falso.vetorizar(textos)
            }
        })
        val primeiro = launch(Dispatchers.Default) { m.sugerir("Cinema X", TipoTransacao.DESPESA) }
        assertTrue(withContext(Dispatchers.IO) { comecou.await(10, TimeUnit.SECONDS) })
        primeiro.cancelAndJoin()
        liberar.countDown()
        assertEquals(lazer.id, m.sugerir("Cinema X", TipoTransacao.DESPESA)?.categoriaId)
        assertEquals(lazer.id, m.ranking("Cinema X", TipoTransacao.DESPESA).first())
        assertEquals(1, m.treinos.get())
    }

    @Test
    fun erroDoVetorizadorViraSemSugestao() = runBlocking {
        val lazer = categoria("Lazer")
        categoria("Mercado")
        gasto(lazer, "Cinema X")
        val m = motor(object : Vetorizador {
            override val versao = cfg.versaoModelo
            override fun vetorizar(textos: List<String>): List<FloatArray> = throw IllegalStateException("ORT falhou")
        })
        assertNull(m.sugerir("Cinema X", TipoTransacao.DESPESA))
        assertTrue(m.ranking("Cinema X", TipoTransacao.DESPESA).isEmpty())
    }
}
