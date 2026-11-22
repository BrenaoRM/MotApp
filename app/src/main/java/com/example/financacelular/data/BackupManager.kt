package com.example.financacelular.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object BackupManager {

    /**
     * Copia o banco para um arquivo temporário em cacheDir (um por chamada; quem enviar apaga depois).
     * Só devolve o arquivo se o WAL foi todo para o arquivo principal e a cópia passa no quick_check:
     * um export incompleto nunca deve substituir um backup bom no Drive. Null = não foi possível.
     */
    suspend fun exportarBaseDeDadosParaFicheiro(context: Context): File? = withContext(Dispatchers.IO) {
        var copia: File? = null
        try {
            val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)
            val arquivoReal = if (dbFile.exists()) {
                if (!consolidarWal(context)) return@withContext null
                dbFile
            } else {
                // Banco com outro nome (instalações antigas): o checkpoint é por conexão própria
                val dbDir = File(context.applicationInfo.dataDir, "databases")
                dbDir.listFiles()?.find {
                    it.name.contains("financa") &&
                            !it.name.endsWith("-journal") &&
                            !it.name.endsWith("-wal") &&
                            !it.name.endsWith("-shm")
                }?.also { checkpointPorConexaoPropria(it.absolutePath) }
            }
            if (arquivoReal == null || !arquivoReal.exists()) return@withContext null

            copia = File.createTempFile("backup_envio_", ".db", context.cacheDir)
            if (arquivoReal == dbFile) {
                copiarSemEscritas(context, arquivoReal, copia)
            } else {
                arquivoReal.copyTo(copia, overwrite = true)
            }

            if (!RestauroBanco.integro(copia)) {
                copia.delete()
                return@withContext null
            }
            copia
        } catch (e: Exception) {
            Log.e("BackupManager", "Falha ao exportar o banco", e)
            copia?.delete()
            null
        }
    }

    /**
     * Copia com uma transação de escrita aberta na conexão do Room: nenhum commit (nem o checkpoint
     * automático que ele dispara) mexe no arquivo principal no meio da cópia.
     */
    private fun copiarSemEscritas(context: Context, origem: File, destino: File) {
        val db = AppDatabase.getInstance(context).openHelper.writableDatabase
        db.beginTransaction()
        try {
            origem.copyTo(destino, overwrite = true)
        } finally {
            db.endTransaction()
        }
    }

    /** Checkpoint TRUNCATE pela conexão do Room; com leitores ativos pode vir ocupado, então tenta algumas vezes. */
    private suspend fun consolidarWal(context: Context): Boolean {
        val db = AppDatabase.getInstance(context).openHelper.writableDatabase
        repeat(5) {
            val ocupado = db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() && it.getInt(0) != 0 }
            if (!ocupado) return true
            delay(300)
        }
        return false
    }

    private fun checkpointPorConexaoPropria(caminhoDb: String) {
        try {
            val db = SQLiteDatabase.openDatabase(caminhoDb, null, SQLiteDatabase.OPEN_READWRITE)
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }
            db.close()
        } catch (e: Exception) {
            Log.w("BackupManager", "Falha no checkpoint do WAL", e)
        }
    }
}
