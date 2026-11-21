package com.example.financacelular.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Restauro de backup: o arquivo ruim nunca troca o banco do aparelho, o bom troca e o Room abre. */
@RunWith(AndroidJUnit4::class)
class RestauroBancoTest {
    private lateinit var contexto: Context

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        AppDatabase.destruirInstancia()
        contexto.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO).parentFile?.listFiles()?.forEach { it.delete() }
    }

    @After
    fun limpar() {
        AppDatabase.destruirInstancia()
    }

    private fun nomesLocais(): List<String> = runBlocking {
        AppDatabase.getInstance(contexto).categoriaDao().listarTodasSync().map { it.nome }
    }

    private fun criarBackupValido(nomeCategoria: String): File {
        val origem = Room.databaseBuilder(contexto, AppDatabase::class.java, "origem-backup.db").build()
        runBlocking { origem.categoriaDao().inserir(Categoria(nome = nomeCategoria, tipo = TipoTransacao.DESPESA)) }
        origem.close() // consolida o WAL no arquivo
        val baixado = File(contexto.cacheDir, "baixado.db")
        contexto.getDatabasePath("origem-backup.db").copyTo(baixado, overwrite = true)
        return baixado
    }

    @Test
    fun backupValidoSubstituiOBancoEOcRoomAbre() {
        runBlocking { AppDatabase.getInstance(contexto).categoriaDao().inserir(Categoria(nome = "SoNoAparelho", tipo = TipoTransacao.DESPESA)) }
        val baixado = criarBackupValido("SoNoBackup")

        assertEquals(RestauroBanco.Resultado.OK, RestauroBanco.restaurar(contexto, baixado))
        RestauroBanco.concluirPendente(contexto)

        val nomes = nomesLocais()
        assertTrue(nomes.contains("SoNoBackup"))
        assertFalse(nomes.contains("SoNoAparelho"))
        // o .bak só some depois de o Room abrir o banco restaurado
        assertFalse(File(contexto.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO).absolutePath + ".bak").exists())
    }

    @Test
    fun arquivoQueNaoESqliteEhRecusadoEOBancoFicaIntacto() {
        runBlocking { AppDatabase.getInstance(contexto).categoriaDao().inserir(Categoria(nome = "Meu dado", tipo = TipoTransacao.DESPESA)) }
        val lixo = File(contexto.cacheDir, "lixo.db").apply { writeText("isto nao e um banco de dados") }

        assertEquals(RestauroBanco.Resultado.INVALIDO, RestauroBanco.restaurar(contexto, lixo))

        assertTrue(nomesLocais().contains("Meu dado"))
    }

    @Test
    fun backupDeVersaoMaisNovaQueOAppEhRecusado() {
        runBlocking { AppDatabase.getInstance(contexto).categoriaDao().inserir(Categoria(nome = "Meu dado", tipo = TipoTransacao.DESPESA)) }
        val futuro = File(contexto.cacheDir, "futuro.db")
        SQLiteDatabase.openOrCreateDatabase(futuro, null).use { it.version = 99 }

        assertEquals(RestauroBanco.Resultado.INVALIDO, RestauroBanco.restaurar(contexto, futuro))

        assertTrue(nomesLocais().contains("Meu dado"))
    }

    @Test
    fun backupDaVersaoAtualComOutroSchemaEhRecusado() {
        runBlocking { AppDatabase.getInstance(contexto).categoriaDao().inserir(Categoria(nome = "Meu dado", tipo = TipoTransacao.DESPESA)) }
        val outro = File(contexto.cacheDir, "outro-schema.db")
        val versaoAtual = AppDatabase.getInstance(contexto).openHelper.readableDatabase.version
        SQLiteDatabase.openOrCreateDatabase(outro, null).use {
            it.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            it.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'hash-de-um-build-de-desenvolvimento')")
            it.version = versaoAtual
        }

        assertEquals(RestauroBanco.Resultado.INVALIDO, RestauroBanco.restaurar(contexto, outro))

        assertTrue(nomesLocais().contains("Meu dado"))
    }

    @Test
    fun integroAceitaUmBancoBom() {
        val bom = criarBackupValido("Qualquer")
        assertTrue(RestauroBanco.integro(bom))
    }
}
