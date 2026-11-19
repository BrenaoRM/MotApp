package com.example.financacelular.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Erro da última instalação para a AtualizacaoSheet. Vazio = usuário cancelou. */
object StatusInstalacao {
    val erro = MutableStateFlow<String?>(null)
}

/** Recebe o resultado da sessão do PackageInstaller aberta pelo ApkDownloader. */
class InstalacaoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmar) }
                    .onFailure { StatusInstalacao.erro.value = "Não foi possível abrir o instalador" }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> StatusInstalacao.erro.value = ""
            else -> StatusInstalacao.erro.value =
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Falha na instalação"
        }
    }
}
