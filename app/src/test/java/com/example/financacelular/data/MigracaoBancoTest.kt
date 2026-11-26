package com.example.financacelular.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Migra o schema 8 (o mais antigo suportado) até o atual e confere que os dados antigos sobrevivem. */
@RunWith(AndroidJUnit4::class)
class MigracaoBancoTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val todas = arrayOf(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)

    @Test
    fun migraDoSchema8AoAtualSemPerderDados() {
        helper.createDatabase(NOME, 8).apply {
            execSQL("INSERT INTO categorias (id, nome, tipo) VALUES (1, 'Mercado', 'DESPESA')")
            execSQL(
                "INSERT INTO despesas_recorrentes (id, nome, valor, categoriaId, diaDoMes, tipo, dataCriacao) " +
                    "VALUES (1, 'Netflix', 40.0, 1, 5, 'DESPESA', '2026-01-05')"
            )
            execSQL(
                "INSERT INTO transacoes (valor, data, categoriaId, tipo, descricao, formaPagamento, anoMes, numeroParcela, totalParcelas) " +
                    "VALUES (50.0, '2026-11-01', 1, 'DESPESA', 'Pão', 'DEBITO', '2026-11', 1, 1)"
            )
            execSQL(
                "INSERT INTO transacoes (valor, data, categoriaId, tipo, descricao, formaPagamento, anoMes, numeroParcela, totalParcelas) " +
                    "VALUES (40.0, '2026-11-05', 1, 'DESPESA', 'Netflix (Assinatura)', 'CARTAO_CREDITO', '2026-11', 1, 1)"
            )
            close()
        }

        // runMigrationsAndValidate compara o resultado com o schema exportado da versão atual
        val db = helper.runMigrationsAndValidate(NOME, 16, true, *todas)

        db.query("SELECT COUNT(*), SUM(valorCentavos) FROM transacoes").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
            assertEquals(9000L, it.getLong(1))
        }
        // 8 -> 9 vincula a cobrança da assinatura antiga à recorrência
        db.query("SELECT recorrenteId FROM transacoes WHERE descricao = 'Netflix (Assinatura)'").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        // 11 -> 12: a recorrência antiga continua no crédito
        db.query("SELECT formaPagamento FROM despesas_recorrentes WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("CARTAO_CREDITO", it.getString(0))
        }
        // 10 -> 11: a categoria antiga fica sem ícone escolhido (o app sugere pelo nome)
        db.query("SELECT nome, icone FROM categorias WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Mercado", it.getString(0))
            assertEquals(true, it.isNull(1))
        }
        // 12 -> 13: a tabela nova existe e aceita linhas
        db.execSQL("INSERT INTO estabelecimento_categoria (chave, tipo, categoriaId) VALUES ('padaria', 'DESPESA', 1)")
        db.query("SELECT COUNT(*) FROM estabelecimento_categoria").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migraDoSchema13Para14CriandoOsIndicesSemPerderDados() {
        helper.createDatabase(NOME, 13).apply {
            execSQL("INSERT INTO categorias (id, nome, tipo) VALUES (1, 'Mercado', 'DESPESA')")
            execSQL(
                "INSERT INTO transacoes (valor, data, categoriaId, tipo, descricao, formaPagamento, anoMes, numeroParcela, totalParcelas) " +
                    "VALUES (50.0, '2026-11-01', 1, 'DESPESA', 'Pão', 'DEBITO', '2026-11', 1, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(NOME, 14, true, MIGRATION_13_14)

        db.query("SELECT COUNT(*) FROM transacoes").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%_categoriaId'").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'transacoes' AND name LIKE 'index_transacoes_%'").use {
            it.moveToFirst()
            assertEquals(5, it.getInt(0))
        }
    }

    @Test
    fun migraDoSchema14Para15ComDataDaCompraVazia() {
        helper.createDatabase(NOME, 14).apply {
            execSQL("INSERT INTO categorias (id, nome, tipo) VALUES (1, 'Mercado', 'DESPESA')")
            execSQL(
                "INSERT INTO transacoes (valor, data, categoriaId, tipo, descricao, formaPagamento, anoMes, numeroParcela, totalParcelas) " +
                    "VALUES (33.34, '2026-12-10', 1, 'DESPESA', 'TV (1/3)', 'CARTAO_CREDITO', '2026-12', 1, 3)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(NOME, 15, true, MIGRATION_14_15)

        db.query("SELECT COUNT(*), dataCompra FROM transacoes").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(true, it.isNull(1))
        }
    }

    @Test
    fun migraDoSchema15Para16EmCentavosEComPagamentoDeFaturaPeloCampo() {
        helper.createDatabase(NOME, 15).apply {
            execSQL("INSERT INTO categorias (id, nome, tipo) VALUES (1, 'Mercado', 'DESPESA')")
            fun transacao(id: Int, valor: String, descricao: String, cartaoId: String = "NULL", anoMes: String = "'2026-11'") = execSQL(
                "INSERT INTO transacoes (id, valor, data, categoriaId, tipo, descricao, formaPagamento, cartaoId, anoMes, numeroParcela, totalParcelas) " +
                    "VALUES ($id, $valor, '2026-11-10', 1, 'DESPESA', '$descricao', 'DINHEIRO', $cartaoId, $anoMes, 1, 1)"
            )
            transacao(1, "0.1", "Bala")
            transacao(2, "33.34", "TV (1/3)", cartaoId = "1")
            transacao(3, "1234.56", "Pagamento de Fatura - 2026-10")
            transacao(4, "99.99", "Pagamento de Fatura - 2026-11 - 7")
            transacao(5, "10.0", "Pagamento de Fatura do aluguel")
            transacao(6, "10.0", "Pagamento de Fatura - 2026-11 - x")
            transacao(7, "10.0", "Pagamento de Fatura - 2026-11", cartaoId = "1")
            execSQL(
                "INSERT INTO despesas_recorrentes (id, nome, valor, categoriaId, diaDoMes, tipo, dataCriacao, formaPagamento) " +
                    "VALUES (1, 'Netflix', 39.9, 1, 5, 'DESPESA', '2026-01-05', 'DEBITO')"
            )
            execSQL("INSERT INTO investimentos (id, nome, categoria, valorInvestido, data, anoMes) VALUES (1, 'CDB', 'Renda Fixa', 0.7, '2026-11-01', '2026-11')")
            execSQL("INSERT INTO metas (id, nome, valorAlvo, valorAtual) VALUES (1, 'Viagem', 5000.0, 1250.55)")
            execSQL("INSERT INTO orcamentos (id, categoriaId, anoMes, valorLimite) VALUES (1, 1, '2026-11', 800.0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(NOME, 16, true, MIGRATION_15_16)

        db.query("SELECT id, valorCentavos, pagaFaturaDoCartaoId, anoMes FROM transacoes ORDER BY id").use {
            val esperado = listOf(
                listOf(1L, 10L, null, "2026-11"),
                listOf(2L, 3334L, null, "2026-11"),
                listOf(3L, 123456L, 1L, "2026-10"), // cartão padrão; o mês vem da descrição
                listOf(4L, 9999L, 7L, "2026-11"),
                listOf(5L, 1000L, null, "2026-11"), // só começa com o texto: não é pagamento
                listOf(6L, 1000L, null, "2026-11"),
                listOf(7L, 1000L, null, "2026-11") // compra no cartão nunca é pagamento
            )
            val lido = mutableListOf<List<Any?>>()
            while (it.moveToNext()) {
                lido += listOf(it.getLong(0), it.getLong(1), if (it.isNull(2)) null else it.getLong(2), it.getString(3))
            }
            assertEquals(esperado, lido)
        }
        db.query("SELECT valorCentavos FROM despesas_recorrentes").use { it.moveToFirst(); assertEquals(3990L, it.getLong(0)) }
        db.query("SELECT valorInvestidoCentavos FROM investimentos").use { it.moveToFirst(); assertEquals(70L, it.getLong(0)) }
        db.query("SELECT valorAlvoCentavos, valorAtualCentavos FROM metas").use {
            it.moveToFirst()
            assertEquals(500000L, it.getLong(0))
            assertEquals(125055L, it.getLong(1))
        }
        db.query("SELECT valorLimiteCentavos FROM orcamentos").use { it.moveToFirst(); assertEquals(80000L, it.getLong(0)) }
        // Os ids seguem de onde estavam (AUTOINCREMENT preservado)
        db.execSQL("INSERT INTO transacoes (valorCentavos, data, categoriaId, tipo, formaPagamento, numeroParcela, totalParcelas) VALUES (1, '2026-11-11', 1, 'DESPESA', 'DEBITO', 1, 1)")
        db.query("SELECT MAX(id) FROM transacoes").use { it.moveToFirst(); assertEquals(8L, it.getLong(0)) }
    }

    // Caminho completo: o Room 2.8 no Robolectric compara o nome do banco com o caminho que o helper abre
    private val NOME: String = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migracao-teste").absolutePath
}
