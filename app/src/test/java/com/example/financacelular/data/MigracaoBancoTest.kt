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

    private val todas = arrayOf(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)

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
        val db = helper.runMigrationsAndValidate(NOME, 13, true, *todas)

        db.query("SELECT COUNT(*), SUM(valor) FROM transacoes").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
            assertEquals(90.0, it.getDouble(1), 0.001)
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

    // Caminho completo: o Room 2.8 no Robolectric compara o nome do banco com o caminho que o helper abre
    private val NOME: String = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migracao-teste").absolutePath
}
