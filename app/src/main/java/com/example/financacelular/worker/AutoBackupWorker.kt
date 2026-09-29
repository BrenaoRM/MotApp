package com.example.financacelular.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financacelular.data.BackupManager
import com.example.financacelular.data.GoogleDriveService
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
            val prefs = applicationContext.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("email_utilizador", "") ?: ""
            if (email.isBlank()) {
                return@withContext Result.success()
            }
            val resultado = autorizarDrive(applicationContext)
            if (resultado.hasResolution()) {
                return@withContext Result.retry()
            }
            val token = resultado.accessToken
            if (token.isNullOrBlank()) {
                return@withContext Result.retry()
            }
            val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(applicationContext)
            if (ficheiroBackup != null && ficheiroBackup.exists()) {
                val sucesso = GoogleDriveService.uploadBackup(token, ficheiroBackup)
                if (!sucesso) return@withContext Result.retry()

                // Atualiza a data e hora do último backup no SharedPreferences
                val formato = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                val dataFormatada = formato.format(Date())
                prefs.edit().putString("ultima_data_backup", dataFormatada).apply()
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun autorizarDrive(context: Context): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(ESCOPO_DRIVE)))
            .build()
        return suspendCancellableCoroutine { continuacao ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { continuacao.resume(it) }
                .addOnFailureListener { continuacao.resumeWithException(it) }
        }
    }
}