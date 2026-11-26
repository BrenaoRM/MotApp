package com.example.financacelular.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExemplosParaIaTest {
    private lateinit var banco: AppDatabase
    private lateinit var repo: FinancaRepository

    @Before
    fun abrir() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        banco = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FinancaRepository(banco)
    }

    @After
    fun fechar() = banco.close()

    private suspend fun categoria(nome: String, tipo: TipoTransacao = TipoTransacao.DESPESA): Long =
        repo.salvarCategoria(Categoria(nome = nome, tipo = tipo))

    private suspend fun lancar(
        categoriaId: Long, descricao: String?, tipo: TipoTransacao = TipoTransacao.DESPESA,
        pagaFatura: Long? = null, grupo: String? = null,
    ) = repo.salvarTransacao(
        Transacao(
            valorCentavos = 1000, data = LocalDate.of(2026, 11, 1), categoriaId = categoriaId, tipo = tipo,
            descricao = descricao, anoMes = "2026-11", pagaFaturaDoCartaoId = pagaFatura, grupoParcelamentoId = grupo,
        )
    )

    @Test
    fun exemplosIgnoramDescricaoIgualACategoria() = runBlocking {
        val alimentacao = categoria("Alimentação")
        lancar(alimentacao, "Alimentação")
        lancar(alimentacao, "alimentacao ")
        lancar(alimentacao, "Mercado Zé")
        assertEquals(listOf("Mercado Zé" to alimentacao), repo.exemplosParaIa(TipoTransacao.DESPESA))
    }

    @Test
    fun exemplosIgnoramFaturaEReceitaDeOutroTipo() = runBlocking {
        val fatura = categoria("Fatura")
        val lazer = categoria("Lazer")
        val salario = categoria("Salário", TipoTransacao.RECEITA)
        lancar(lazer, "Pagamento fatura Nubank", pagaFatura = 1)
        lancar(fatura, "Fatura antiga")
        lancar(lazer, "Cinema")
        lancar(salario, "Empresa X", TipoTransacao.RECEITA)
        assertEquals(listOf("Cinema" to lazer), repo.exemplosParaIa(TipoTransacao.DESPESA))
        assertEquals(listOf("Empresa X" to salario), repo.exemplosParaIa(TipoTransacao.RECEITA))
    }

    @Test
    fun exemplosIgnoramDescricaoVazia() = runBlocking {
        val lazer = categoria("Lazer")
        lancar(lazer, null)
        lancar(lazer, "")
        lancar(lazer, "   ")
        lancar(lazer, "Show")
        assertEquals(listOf("Show" to lazer), repo.exemplosParaIa(TipoTransacao.DESPESA))
    }

    @Test
    fun parcelamentoContaUmaVezEMantemOrdem() = runBlocking {
        val compras = categoria("Compras")
        lancar(compras, "Loja A")
        repeat(3) { lancar(compras, "TV 10x", grupo = "g1") }
        lancar(compras, "Loja B")
        assertEquals(
            listOf("Loja A" to compras, "TV 10x" to compras, "Loja B" to compras),
            repo.exemplosParaIa(TipoTransacao.DESPESA),
        )
    }
}
