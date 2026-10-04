package com.example.financacelular.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.financacelular.MainActivity
import com.example.financacelular.data.AppDatabase
import com.example.financacelular.data.FaturaAVencer
import com.example.financacelular.data.FinancaRepository
import com.example.financacelular.data.faturasProximasDoVencimento
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

class LembreteFaturaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FinancaRepository.getInstance(AppDatabase.getInstance(applicationContext))

        val cartoes = repository.listarTodosCartoesSync()
        if (cartoes.isEmpty()) return Result.success()

        // Avalia cada cartão separadamente: fatura em aberto, com valor, vencendo em até 2 dias
        val transacoes = repository.listarTransacoes().first()
        faturasProximasDoVencimento(cartoes, transacoes).forEach { dispararNotificacao(it) }

        return Result.success()
    }

    private fun podeNotificar(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val concedida = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!concedida) return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    // A permissão é verificada em podeNotificar(); o lint não consegue enxergar isso.
    @SuppressLint("MissingPermission")
    private fun dispararNotificacao(fatura: FaturaAVencer) {
        if (!podeNotificar()) return

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fatura_lembrete"

        val channel = NotificationChannel(
            channelId,
            "Lembretes de Fatura",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        // Ao tocar na notificação, abre o app
        val intentAbrirApp = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intentAbrirApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Fatura do ${fatura.cartao.nome} próxima do vencimento! ⚠️")
            .setContentText("Vence em ${fatura.vencimento.format(DateTimeFormatter.ofPattern("dd/MM"))}. Pague para evitar juros!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Um id por cartão, para que as notificações de cartões diferentes não se sobrescrevam
        notificationManager.notify(1001 + fatura.cartao.id.toInt(), builder.build())
    }
}