package com.example.financacelular.worker

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financacelular.data.BackupManager
import com.example.financacelular.data.GoogleDriveService
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("email_utilizador", "") ?: ""

            if (email.isBlank() || email == "Conta Conectada") {
                return@withContext Result.success() // Ignora se não houver conta Google associada
            }

            val account = Account(email, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/drive.file"

            // Pede o token sem mostrar nenhum pop-up
            val token = GoogleAuthUtil.getToken(applicationContext, account, scope)

            val ficheiroBackup = BackupManager.exportarBaseDeDadosParaFicheiro(applicationContext)
            if (ficheiroBackup != null && ficheiroBackup.exists()) {
                GoogleDriveService.uploadBackup(token, ficheiroBackup)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Se falhar (ex: sem internet), tenta novamente mais tarde
            Result.retry()
        }
    }
}