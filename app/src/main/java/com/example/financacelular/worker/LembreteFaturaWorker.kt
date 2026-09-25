package com.example.financacelular.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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

        if (hoje.dayOfMonth in (diaVencimentoReal - 2)..diaVencimentoReal) {
            val anoMesStr = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val faturaPaga = repository.verificarFaturaPaga(anoMesStr).first()

            if (!faturaPaga) {
                dispararNotificacao()
            }
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun dispararNotificacao() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fatura_lembrete"

        val channel = NotificationChannel(
            channelId,
            "Lembretes de Fatura",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Fatura próxima do vencimento! ⚠️")
            .setContentText("Sua fatura do cartão vence em breve. Pague agora para evitar juros!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(1001, builder.build())
    }
}