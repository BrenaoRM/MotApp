package com.example.financacelular.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FinancaRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class LembreteFaturaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FinancaRepository.getInstance(AppDatabase.getInstance(applicationContext))
        val cartao = repository.obterCartaoSync(1L) ?: return Result.success()

        val hoje = LocalDate.now()
        val diaVencimentoReal = minOf(cartao.diaVencimento, YearMonth.now().lengthOfMonth())

        // Dispara se faltam 2 dias para vencer, 1 dia, ou se é o próprio dia de vencimento
        if (hoje.dayOfMonth in (diaVencimentoReal - 2)..diaVencimentoReal) {
            val anoMesStr = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val faturaPaga = repository.verificarFaturaPaga(1L, anoMesStr).first()

            if (!faturaPaga) {
                dispararNotificacao()
            }
        }
        return Result.success()
    }

    private fun dispararNotificacao() {
        // Verifica se a permissão de notificações foi concedida (necessário para Android 13+)[cite: 10]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return // Se não tiver permissão, aborta o disparo da notificação para evitar crash
            }
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fatura_lembrete"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Lembretes de Fatura",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Fatura próxima do vencimento! ⚠️")
            .setContentText("Sua fatura do cartão vence em breve. Pague agora para evitar juros!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(1001, builder.build())
    }
}