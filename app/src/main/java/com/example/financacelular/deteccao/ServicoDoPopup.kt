package com.example.financacelular.deteccao

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Mantém o app em primeiro plano enquanto o popup de transação está aberto. Com o app fechado, alguns Androids
 * (a Samsung, por exemplo) congelam o processo logo depois de entregar o aviso do banco: a janela do popup era
 * criada mas não terminava de desenhar, e só aparecia quando outra notificação acordava o app, minutos depois.
 * Em primeiro plano o processo não é congelado. Tipo `shortService`: o Android dá no máximo 3 minutos.
 */
class ServicoDoPopup : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gerenciador = getSystemService(NotificationManager::class.java)
        gerenciador.createNotificationChannel(
            NotificationChannel(CANAL, "Popup de transação aberto", NotificationManager.IMPORTANCE_MIN)
        )
        val notificacao = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Transação detectada")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(ID, notificacao)
            }
        } catch (e: RuntimeException) {
            Log.w("ServicoDoPopup", "Primeiro plano recusado", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // Passados os 3 minutos o popup continua aberto, só sem a proteção
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    companion object {
        private const val CANAL = "popup_aberto"
        private const val ID = 7301

        fun iniciar(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ServicoDoPopup::class.java))
            } catch (e: RuntimeException) { // ForegroundServiceStartNotAllowedException: o popup abre do mesmo jeito
                Log.w("ServicoDoPopup", "Primeiro plano recusado", e)
            }
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, ServicoDoPopup::class.java))
        }
    }
}
