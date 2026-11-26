package com.example.financacelular.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CategoriaRepositoryTest {
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

    private suspend fun categoria(nome: String, tipo: TipoTransacao = TipoTransacao.DESPESA): Categoria {
        val id = repo.salvarCategoria(Categoria(nome = nome, tipo = tipo))
        return Categoria(id = id, nome = nome, tipo = tipo)
    }

    private suspend fun gasto(categoria: Categoria, descricao: String) = repo.salvarTransacao(
        Transacao(
            valorCentavos = reaisEmCentavos(10.0), data = LocalDate.of(2026, 11, 1), categoriaId = categoria.id, tipo = categoria.tipo,
            descricao = descricao, anoMes = "2026-11"
        )
    )

    private fun contar(sql: String): Int =
        banco.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun moverEExcluirPassaTudoParaODestinoEApagaAOrigem() = runBlocking {
        val lazer = categoria("Lazer")
        val comida = categoria("Alimentação")
        gasto(lazer, "Cinema")
        gasto(lazer, "Parque")
        banco.despesaRecorrenteDao().inserir(
            DespesaRecorrente(nome = "Streaming", valorCentavos = reaisEmCentavos(30.0), categoriaId = lazer.id, diaDoMes = 3, formaPagamento = FormaPagamento.DEBITO)
        )
        repo.lembrarCategoria("Cinema Centro", TipoTransacao.DESPESA, lazer.id)

        repo.moverEExcluirCategoria(lazer, comida)

        assertEquals(2, contar("SELECT COUNT(*) FROM transacoes WHERE categoriaId = ${comida.id}"))
        assertEquals(1, contar("SELECT COUNT(*) FROM despesas_recorrentes WHERE categoriaId = ${comida.id}"))
        assertEquals(0, contar("SELECT COUNT(*) FROM categorias WHERE id = ${lazer.id}"))
        // a categoria lembrada para o estabelecimento acompanha o destino
        assertEquals(comida.id, repo.sugerirCategoria("cinema centro", TipoTransacao.DESPESA)?.id)
    }

    @Test
    fun excluirTudoApagaOsDadosEAsugestao() = runBlocking {
        val lazer = categoria("Lazer")
        gasto(lazer, "Cinema")
        repo.lembrarCategoria("Cinema Centro", TipoTransacao.DESPESA, lazer.id)

        repo.excluirCategoriaComDados(lazer)

        assertEquals(0, contar("SELECT COUNT(*) FROM transacoes"))
        assertEquals(0, contar("SELECT COUNT(*) FROM estabelecimento_categoria"))
        assertNull(repo.sugerirCategoria("Cinema Centro", TipoTransacao.DESPESA))
    }

    @Test
    fun sugestaoIgnoraAcentoCaixaETipo() = runBlocking {
        val comida = categoria("Alimentação")
        repo.lembrarCategoria("PADARIA  São José", TipoTransacao.DESPESA, comida.id)

        assertEquals(comida.id, repo.sugerirCategoria("padaria sao jose", TipoTransacao.DESPESA)?.id)
        assertNull(repo.sugerirCategoria("padaria sao jose", TipoTransacao.RECEITA))
        assertNull(repo.sugerirCategoria("outra padaria", TipoTransacao.DESPESA))
        assertNull(repo.sugerirCategoria("   ", TipoTransacao.DESPESA))
    }

    @Test
    fun lembrarDeNovoTrocaACategoria() = runBlocking {
        val comida = categoria("Alimentação")
        val lazer = categoria("Lazer")
        repo.lembrarCategoria("Bar do Zé", TipoTransacao.DESPESA, comida.id)
        repo.lembrarCategoria("bar do ze", TipoTransacao.DESPESA, lazer.id)

        assertEquals(lazer.id, repo.sugerirCategoria("Bar do Zé", TipoTransacao.DESPESA)?.id)
        assertEquals(1, contar("SELECT COUNT(*) FROM estabelecimento_categoria"))
    }
}
