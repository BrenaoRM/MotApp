package com.example.financacelular

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.example.financacelular.deteccao.NotificacaoListener
import android.content.pm.PackageManager
import com.example.financacelular.deteccao.PreenchimentoPendente
import com.example.financacelular.deteccao.paraDetectada
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
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
import com.example.financacelular.data.RestauroBanco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Precisa ser registrado antes de a Activity chegar ao estado STARTED
    private val pedidoPermissaoNotificacao = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Sem ação: se negar, o lembrete simplesmente não é exibido */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restauro de backup pendente: o Room abre (ou o banco anterior volta) antes de qualquer tela usar o banco
        RestauroBanco.concluirPendente(applicationContext)

        enableEdgeToEdge()

        com.example.financacelular.ui.ValoresVisiveis.iniciar(this)

        // Aberto pelo aviso de transação detectada (só na criação, para não repetir ao girar a tela)
        if (savedInstanceState == null) intent.paraDetectada()?.let(PreenchimentoPendente::definir)

        pedirPermissaoDeNotificacaoSeNecessario()

        // Se o sistema deixou o leitor de notificações desligado, religa ao abrir o app
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            NotificationListenerService.requestRebind(ComponentName(this, NotificacaoListener::class.java))
        }

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

            // Barras do sistema acompanham o tema escolhido no app (não só o do sistema)
            DisposableEffect(temaEscuro) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        detectDarkMode = { temaEscuro }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                        detectDarkMode = { temaEscuro }
                    )
                )
                onDispose { }
            }

            FinanceAPPTheme(darkTheme = temaEscuro) {
                ProvideResponsiveDensity {
                    AppNavigation(configuracoesViewModel = configuracoesViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.paraDetectada()?.let(PreenchimentoPendente::definir)
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