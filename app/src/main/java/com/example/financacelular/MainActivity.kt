package com.example.financacelular

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.financacelular.data.TemaPreferencia
import com.example.financacelular.ui.AppNavigation
import com.example.financacelular.ui.ConfiguracoesViewModel
import com.example.financacelular.ui.theme.FinanceAPPTheme
import com.example.financacelular.ui.theme.ProvideResponsiveDensity
import com.example.financacelular.worker.AutoBackupWorker
import com.example.financacelular.worker.LembreteFaturaWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Precisa ser registrado antes de a Activity chegar ao estado STARTED
    private val pedidoPermissaoNotificacao = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Sem ação: se negar, o lembrete simplesmente não é exibido */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        pedirPermissaoDeNotificacaoSeNecessario()

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
        // Garante que as assinaturas tenham cobranças geradas até 12 meses à frente
        lifecycleScope.launch(Dispatchers.IO) {
            FinancaRepository
                .getInstance(AppDatabase.getInstance(applicationContext))
                .gerarRecorrentesPendentes()
        }

        setContent {
            val configuracoesViewModel: ConfiguracoesViewModel = viewModel()
            val temaEscuroSistema = isSystemInDarkTheme()
            val temaEscuro = when (configuracoesViewModel.temaAtual) {
                TemaPreferencia.CLARO -> false
                TemaPreferencia.ESCURO -> true
                TemaPreferencia.SISTEMA -> temaEscuroSistema
            }

            FinanceAPPTheme(darkTheme = temaEscuro) {
                ProvideResponsiveDensity {
                    AppNavigation(configuracoesViewModel = configuracoesViewModel)
                }
            }
        }
    }

    /** No Android 13+ (API 33) a permissão de notificação precisa ser pedida em runtime. */
    private fun pedirPermissaoDeNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val jaConcedida = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!jaConcedida) {
            pedidoPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
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