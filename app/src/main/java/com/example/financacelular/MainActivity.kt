package com.example.financacelular

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.ui.AppNavigation
import com.example.financacelular.ui.ConfiguracoesViewModel
import com.example.financacelular.ui.theme.FinanceAPPTheme
import com.example.financacelular.worker.LembreteFaturaWorker
import com.example.financacelular.worker.AutoBackupWorker // <-- Import adicionado
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Agendar verificação diária de fatura
        val workRequest = PeriodicWorkRequestBuilder<LembreteFaturaWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LembreteFatura",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Agendar backup automático diário para o Google Drive, sempre às 2h da manhã
        val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calcularDelayAte2h(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AutoBackupGoogleDrive",
            ExistingPeriodicWorkPolicy.UPDATE,
            backupRequest
        )

        setContent {
            val configuracoesViewModel: ConfiguracoesViewModel = viewModel()
            val temaEscuroSistema = isSystemInDarkTheme()
            val temaEscuro = when (configuracoesViewModel.temaAtual) {
                TemaPreferencia.CLARO -> false
                TemaPreferencia.ESCURO -> true
                TemaPreferencia.SISTEMA -> temaEscuroSistema
            }

            FinanceAPPTheme(darkTheme = temaEscuro) {
                AppNavigation(configuracoesViewModel = configuracoesViewModel)
            }
        }
    }

    private fun calcularDelayAte2h(): Long {
        val agora = Calendar.getInstance()
        val proximaExecucao = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(agora)) {
                add(Calendar.DAY_OF_YEAR, 1) // já passou das 2h hoje, agenda pra amanhã
            }
        }
        return proximaExecucao.timeInMillis - agora.timeInMillis
    }
}