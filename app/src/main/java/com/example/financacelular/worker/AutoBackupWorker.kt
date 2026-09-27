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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Mesmo escopo pedido em ConfiguracoesViewModel.kt no login/backup manual.
private const val ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive.file"

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("financacelular_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("email_utilizador", "") ?: ""

            if (email.isBlank() || email == "Conta Conectada") {
                return@withContext Result.success() // Ignora se não houver conta Google associada
            }

            // Antes usava GoogleAuthUtil.getToken() (API legada, ligada à AccountManager
            // clássica) — incompatível com o login feito via Credential Manager, por isso
            // falhava na maior parte das vezes. Agora usa o MESMO AuthorizationClient do
            // botão de backup manual. Segundo a doc oficial, depois da primeira autorização
            // interativa (já feita quando a conta foi conectada), chamadas seguintes a
            // authorize() devolvem o token direto, sem UI — funciona normalmente aqui,
            // mesmo sem Activity, porque a chamada aceita Context puro.
            val resultado = autorizarDrive(applicationContext)

            if (resultado.hasResolution()) {
                // Precisaria mostrar tela de consentimento (nunca concedido ou foi
                // revogado) — não dá pra resolver em segundo plano. Tenta de novo mais
                // tarde; se continuar falhando, o utilizador precisa abrir o app e
                // reconectar a conta em Configurações.
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
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Se falhar (ex: sem internet), tenta novamente mais tarde
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