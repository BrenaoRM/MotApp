package com.example.financacelular.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.edit
import java.io.File

/**
 * Troca do banco local por um backup baixado, sem deixar o app sem dados:
 * valida o arquivo antes, troca com a trava do banco (ninguém recria o banco no meio) e guarda o
 * banco anterior em `.bak` até o Room conseguir abrir o restaurado ([concluirPendente]).
 */
object RestauroBanco {
    enum class Resultado { OK, INVALIDO, FALHOU }

    /** A versão 8 é a mais antiga com migração do Room até a atual. */
    private const val TAG = "RestauroBanco"
    private const val VERSAO_MINIMA = 8
    private const val ID_MASTER_ROOM = 42

    /** Trava própria: restaurar e concluirPendente (MainActivity e worker) não rodam ao mesmo tempo. A ordem é sempre esta trava, depois a do banco. */
    @Synchronized
    fun restaurar(context: Context, baixado: File): Resultado {
        try {
            if (!validar(context, baixado)) return Resultado.INVALIDO
            return if (substituir(context, baixado)) Resultado.OK else Resultado.FALHOU
        } finally {
            if (baixado.exists()) baixado.delete()
            apagarAuxiliares(baixado)
        }
    }

    /** O arquivo é um SQLite íntegro, de uma versão que o app consegue migrar e com o mesmo schema. */
    private fun validar(context: Context, arquivo: File): Boolean = try {
        // Banco local corrompido não pode impedir o restauro (é quando ele mais faz falta): sem ele, não
        // compara o schema; se o Room recusar o restaurado, concluirPendente devolve o anterior
        val local = try {
            AppDatabase.getInstance(context).openHelper.readableDatabase
        } catch (e: Exception) {
            Log.w(TAG, "Banco local não abriu; validando o backup só pela versão", e)
            null
        }
        // Cópia temporária nossa: abre em leitura e escrita porque SQLite antigo não abre WAL só para leitura
        SQLiteDatabase.openDatabase(arquivo.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val versao = db.version
            val integro = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            integro && versao in VERSAO_MINIMA..AppDatabase.VERSAO &&
                (versao != AppDatabase.VERSAO || local == null || mesmoSchema(local, db))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Backup baixado não passou na validação", e)
        false
    }

    /** Na mesma versão o Room recusa um banco de schema diferente (por exemplo, de um build de desenvolvimento). */
    private fun mesmoSchema(local: androidx.sqlite.db.SupportSQLiteDatabase, baixado: SQLiteDatabase): Boolean {
        val hashLocal = local.query("SELECT identity_hash FROM room_master_table WHERE id = $ID_MASTER_ROOM")
            .use { if (it.moveToFirst()) it.getString(0) else null }
        val hashBaixado = baixado.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = $ID_MASTER_ROOM", null)
            .use { if (it.moveToFirst()) it.getString(0) else null }
        return hashLocal != null && hashLocal == hashBaixado
    }

    /** Um quick_check do arquivo que vai para o Drive: um export ruim nunca deve substituir um backup bom. */
    fun integro(arquivo: File): Boolean = try {
        SQLiteDatabase.openDatabase(arquivo.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao verificar a integridade do backup", e)
        false
    } finally {
        apagarAuxiliares(arquivo)
    }

    private fun substituir(context: Context, baixado: File): Boolean {
        val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)
        val anterior = File(dbFile.absolutePath + ".bak")
        return AppDatabase.comBancoFechado {
            try {
                dbFile.parentFile?.mkdirs()
                anterior.delete()
                if (dbFile.exists() && !dbFile.renameTo(anterior)) {
                    dbFile.copyTo(anterior, overwrite = true)
                }
                // O WAL/SHM eram do banco antigo (consolidados no fechamento); não podem ficar ao lado do novo
                apagarAuxiliares(dbFile)
                if (!baixado.renameTo(dbFile)) {
                    dbFile.delete()
                    baixado.copyTo(dbFile, overwrite = true)
                }
                apagarAuxiliares(dbFile)
                ContaDrive.prefs(context).edit(commit = true) { putBoolean(ContaDrive.RESTAURO_PENDENTE, true) }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao trocar o banco pelo restaurado", e)
                if (anterior.exists()) {
                    dbFile.delete()
                    anterior.renameTo(dbFile)
                }
                false
            }
        }
    }

    /** Leitura barata (só as preferências): diz se [concluirPendente] tem trabalho a fazer. */
    fun temPendente(context: Context): Boolean =
        ContaDrive.prefs(context).getBoolean(ContaDrive.RESTAURO_PENDENTE, false)

    /**
     * Chamado ao abrir o app: se houve restauro, abre o Room agora. Deu certo, o `.bak` é apagado;
     * deu errado (schema que o Room recusa), o banco anterior volta e o app não trava em loop.
     */
    @Synchronized
    fun concluirPendente(context: Context) {
        val prefs = ContaDrive.prefs(context)
        // Lido dentro da trava: quem chegar depois (MainActivity e worker) já encontra a marca limpa
        if (!prefs.getBoolean(ContaDrive.RESTAURO_PENDENTE, false)) return
        val dbFile = context.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO)
        val anterior = File(dbFile.absolutePath + ".bak")
        try {
            AppDatabase.getInstance(context).openHelper.writableDatabase
            anterior.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Room não abriu o banco restaurado; voltando ao anterior", e)
            AppDatabase.comBancoFechado {
                if (anterior.exists()) {
                    dbFile.delete()
                    apagarAuxiliares(dbFile)
                    if (!anterior.renameTo(dbFile)) {
                        anterior.copyTo(dbFile, overwrite = true)
                        anterior.delete()
                    }
                    desconectarContaDoRestauro(prefs)
                }
            }
        } finally {
            prefs.edit(commit = true) { putBoolean(ContaDrive.RESTAURO_PENDENTE, false) }
        }
    }

    /**
     * Com o banco anterior de volta, a conta que o restauro tinha adotado não combina mais com ele:
     * desconecta (e deixa a permissão dela para ser revogada) em vez de o worker das 2h gravar os
     * dados antigos no Drive dessa conta.
     */
    private fun desconectarContaDoRestauro(prefs: android.content.SharedPreferences) {
        val adotada = prefs.getString(ContaDrive.EMAIL, "").orEmpty()
        val pendentes = prefs.getStringSet(ContaDrive.CONTAS_A_REVOGAR, emptySet()).orEmpty().toMutableSet()
        if (adotada.isNotBlank()) pendentes.add(adotada)
        prefs.edit(commit = true) {
            remove(ContaDrive.EMAIL)
            remove(ContaDrive.ULTIMA_DATA_BACKUP)
            putStringSet(ContaDrive.CONTAS_A_REVOGAR, pendentes)
            putBoolean(ContaDrive.RESTAURO_REVERTIDO, true)
        }
    }

    private fun apagarAuxiliares(db: File) {
        File(db.absolutePath + "-wal").delete()
        File(db.absolutePath + "-shm").delete()
        File(db.absolutePath + "-journal").delete()
    }
}
