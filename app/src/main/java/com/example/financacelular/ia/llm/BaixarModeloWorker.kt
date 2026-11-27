package com.example.financacelular.ia.llm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** Baixa os arquivos do modo pedido em primeiro plano, com notificação de progresso. */
class BaixarModeloWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
    private val modoPedido = inputData.getString(GerenciadorDeModelo.CHAVE_MODO)?.let { n -> ModoIa.entries.firstOrNull { it.name == n } }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modo = modoPedido ?: return@withContext Result.failure()
        val modelo = ModelosIa.disponiveis.firstOrNull { it.modo == modo }
        if (modelo == null) {
            GerenciadorDeModelo.definirEstado(EstadoModelo.Falhou(modo))
            return@withContext Result.failure()
        }
        // Sem POST_NOTIFICATIONS (Android 13+) o serviço roda igual, só a notificação fica oculta
        try { setForeground(info(0)) } catch (_: Exception) { }

        val total = modelo.arquivos.sumOf { it.bytes }.coerceAtLeast(1)
        val pasta = GerenciadorDeModelo.pasta(applicationContext)
        var concluidos = 0L
        var ultimo = -1
        try {
            for (arquivo in modelo.arquivos) {
                val ok = GerenciadorDeModelo.baixar(arquivo, pasta) { bytes ->
                    val pct = ((concluidos + bytes) * 100 / total).toInt()
                    if (pct != ultimo) {
                        ultimo = pct
                        GerenciadorDeModelo.definirEstado(EstadoModelo.Baixando(modo, pct / 100f))
                        atualizarNotificacao(pct)
                    }
                }
                ensureActive()
                if (!ok) {
                    GerenciadorDeModelo.definirEstado(EstadoModelo.Falhou(modo))
                    return@withContext Result.failure()
                }
                concluidos += arquivo.bytes
            }
        } catch (e: IOException) {
            if (runAttemptCount >= 4) {
                GerenciadorDeModelo.definirEstado(EstadoModelo.Falhou(modo))
                return@withContext Result.failure()
            }
            return@withContext Result.retry()
        }
        GerenciadorDeModelo.concluir(applicationContext, modelo)
        Result.success()
    }

    private fun atualizarNotificacao(pct: Int) {
        if (!podeNotificar()) return
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notificacao(pct))
    }

    private fun podeNotificar() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificacao(pct: Int): Notification {
        val gerenciador = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        gerenciador.createNotificationChannel(NotificationChannel(CANAL, "Download da IA", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(applicationContext, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Baixando a IA")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun info(pct: Int): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ID, notificacao(pct), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(ID, notificacao(pct))

    private companion object {
        const val CANAL = "ia_download"
        const val ID = 4201
    }
}
