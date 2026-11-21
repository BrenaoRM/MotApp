package com.example.financacelular.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preferências da conta do Google Drive, num arquivo à parte (`financacelular_conta.xml`) que fica
 * FORA do backup do Android: se voltassem para um aparelho novo, o app se acharia conectado e o
 * backup das 2h poderia gravar um banco antigo por cima do backup mais novo da nuvem.
 * Ver `res/xml/data_extraction_rules.xml` e `backup_rules.xml`.
 */
object ContaDrive {
    private const val ARQUIVO = "financacelular_conta"
    private const val ARQUIVO_ANTIGO = "financacelular_prefs"

    const val EMAIL = "email_utilizador"
    const val ULTIMA_DATA_BACKUP = "ultima_data_backup"
    /** Contas que perderam a vez mas ainda precisam ter a permissão revogada no Google. */
    const val CONTAS_A_REVOGAR = "contas_a_revogar"
    const val RESTAURO_PENDENTE = "restauro_pendente"
    const val RESTAURO_REVERTIDO = "restauro_revertido"
    private const val MIGRADO = "migrado"

    fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        val conta = app.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        if (!conta.getBoolean(MIGRADO, false)) {
            // Versões anteriores guardavam e-mail e data no arquivo geral. Só migra se for uma atualização
            // neste aparelho: numa instalação nova (aparelho novo restaurado pelo backup do Android) esses
            // valores vieram de outro aparelho e não podem reconectar a conta sozinhos.
            val antigo = app.getSharedPreferences(ARQUIVO_ANTIGO, Context.MODE_PRIVATE)
            val atualizacao = try {
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                info.firstInstallTime != info.lastUpdateTime
            } catch (_: Exception) {
                false
            }
            conta.edit(commit = true) {
                if (atualizacao) {
                    antigo.getString(EMAIL, null)?.let { putString(EMAIL, it) }
                    antigo.getString(ULTIMA_DATA_BACKUP, null)?.let { putString(ULTIMA_DATA_BACKUP, it) }
                }
                putBoolean(MIGRADO, true)
            }
            antigo.edit(commit = true) {
                remove(EMAIL)
                remove(ULTIMA_DATA_BACKUP)
            }
        }
        return conta
    }

    fun email(context: Context): String = prefs(context).getString(EMAIL, "") ?: ""
}
