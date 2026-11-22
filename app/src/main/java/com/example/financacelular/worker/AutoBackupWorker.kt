package com.example.financacelular.worker

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.BackupManager
import com.example.financacelular.data.ContaDrive
import com.example.financacelular.data.GoogleDriveService
import com.example.financacelular.data.RestauroBanco
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive.file"

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Se um restauro de backup ficou pendente, o Room precisa abrir (ou reverter) antes de qualquer cópia
            RestauroBanco.concluirPendente(applicationContext)
            val prefs = ContaDrive.prefs(applicationContext)
            val email = prefs.getString(ContaDrive.EMAIL, "") ?: ""
            if (email.isBlank()) {
                return@withContext Result.success()
            }
            val resultado = autorizarDrive(applicationContext, email)
            // Consentimento revogado ou token vazio: retry não resolve, tenta de novo só no próximo período
            if (resultado.hasResolution()) {
                return@withContext Result.failure()
            }
            val token = resultado.accessToken
            if (token.isNullOrBlank()) {
                return@withContext Result.failure()
            }
            if (!applicationContext.getDatabasePath(AppDatabase.NOME_ARQUIVO_BANCO).exists()) {
                return@withContext Result.success() // nada para guardar ainda
            }
            // Null = não deu para gerar uma cópia íntegra agora (WAL ocupado, cópia inválida): tenta mais tarde
            val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(applicationContext)
                ?: return@withContext tentarDeNovo()
            // Conta trocada/desconectada (ou banco restaurado) desde que o token foi pedido: não envia
            // o banco de agora para o Drive da conta de antes
            if (prefs.getString(ContaDrive.EMAIL, "") != email) {
                ficheiroBackup.delete()
                return@withContext Result.success()
            }
            val sucesso = try {
                GoogleDriveService.uploadBackup(token, ficheiroBackup)
            } finally {
                ficheiroBackup.delete()
            }
            if (!sucesso) return@withContext tentarDeNovo()

            // Só atualiza a data se a conta continua a mesma (pode ter sido desconectada ou trocada no meio)
            if (prefs.getString(ContaDrive.EMAIL, "") == email) {
                val formato = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                val dataFormatada = formato.format(Date())
                prefs.edit().putString(ContaDrive.ULTIMA_DATA_BACKUP, dataFormatada).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AutoBackupWorker", "Falha no backup automático", e)
            tentarDeNovo()
        }
    }

    // Falha transitória: no máximo 3 tentativas por execução; depois espera o próximo período
    private fun tentarDeNovo(): Result = if (runAttemptCount < 3) Result.retry() else Result.failure()

    private suspend fun autorizarDrive(context: Context, email: String): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(ESCOPO_DRIVE)))
            .setAccount(Account(email, "com.google"))
            .build()
        return suspendCancellableCoroutine { continuacao ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { continuacao.resume(it) }
                .addOnFailureListener { continuacao.resumeWithException(it) }
        }
    }
}